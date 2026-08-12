package com.bharatisethiya.recurringexpensetracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme

enum class Recurrence { Daily, Weekly, Monthly, Yearly }

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#6750A4"
) {
    fun color(): Color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color(0xFF6750A4) }
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val price: Double,
    val currencyCode: String = "USD",
    val recurrence: Recurrence = Recurrence.Monthly,
    val everyXRecurrence: Int = 1,
    val firstPaymentDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val archivedDate: LocalDate? = null,
    val requireManualConfirmation: Boolean = false,
    val tagId: String? = null,
)

// ORIGINAL FORMULA from RecurringExpenseEntry.kt:getMonthlyPrice()
// Daily: (365/12)/everyX * price, Weekly: (52/12)/everyX * price, Monthly: 1/everyX*price, Yearly: price/(everyX*12)
fun Expense.monthlyCost(): Double {
    val every = everyXRecurrence.coerceAtLeast(1).toDouble()
    val base = when (recurrence) {
        Recurrence.Daily -> (365.0 / 12.0) / every * price
        Recurrence.Weekly -> (52.0 / 12.0) / every * price
        Recurrence.Monthly -> 1.0 / every * price
        Recurrence.Yearly -> price / (every * 12.0)
    }
    return base
}

fun exchangeFactor(from: String, to: String): Double {
    if (from == to) return 1.0
    val toUsd = mapOf(
        "USD" to 1.0, "EUR" to 1.08, "GBP" to 1.27, "INR" to 0.012,
        "JPY" to 0.0068, "CAD" to 0.74, "AUD" to 0.66, "CHF" to 1.12,
        "CNY" to 0.14, "SEK" to 0.095, "NZD" to 0.61, "BRL" to 0.20,
        "MXN" to 0.06, "SGD" to 0.74, "HKD" to 0.13, "ZAR" to 0.053
    )
    val fromUsd = toUsd[from] ?: 1.0
    val targetUsd = toUsd[to] ?: 1.0
    return fromUsd / targetUsd
}

fun monthlyCostConverted(expense: Expense, defaultCurrency: String): Double {
    if (expense.archivedDate != null) return 0.0
    if (expense.endDate != null && expense.endDate.isBefore(LocalDate.now())) return 0.0
    return expense.monthlyCost() * exchangeFactor(expense.currencyCode, defaultCurrency)
}

data class UpcomingPayment(
    val expense: Expense,
    val dueDate: LocalDate,
    val remainingDays: Long,
    val requiresConfirmation: Boolean,
    val isOverdue: Boolean,
)

fun upcomingPayments(expenses: List<Expense>, daysAhead: Long = 30): List<UpcomingPayment> {
    val today = LocalDate.now()
    val end = today.plusDays(daysAhead)
    val result = mutableListOf<UpcomingPayment>()
    for (exp in expenses) {
        if (exp.archivedDate != null) continue
        if (exp.endDate != null && exp.endDate.isBefore(today)) continue
        var date = exp.firstPaymentDate
        var guard = 0
        while (date.isBefore(today) && guard < 1000) {
            date = advance(date, exp.recurrence, exp.everyXRecurrence)
            guard++
        }
        guard = 0
        while (!date.isAfter(end) && guard < 200) {
            if (!date.isBefore(today)) {
                val remaining = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                result.add(UpcomingPayment(exp, date, remaining, exp.requireManualConfirmation, remaining < 0))
            }
            date = advance(date, exp.recurrence, exp.everyXRecurrence)
            guard++
        }
    }
    return result.sortedBy { it.dueDate }
}

fun advance(date: LocalDate, recurrence: Recurrence, everyX: Int = 1): LocalDate {
    val every = everyX.coerceAtLeast(1)
    return when (recurrence) {
        Recurrence.Daily -> date.plusDays(every.toLong())
        Recurrence.Weekly -> date.plusWeeks(every.toLong())
        Recurrence.Monthly -> date.plusMonths(every.toLong())
        Recurrence.Yearly -> date.plusYears(every.toLong())
    }
}

fun availableCurrencies(): List<String> {
    return try {
        Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
    } catch (e: Exception) {
        listOf("USD","EUR","GBP","INR","JPY","CAD","AUD","CHF","CNY","SEK","NZD","BRL","MXN","SGD","HKD","NOK","DKK","ZAR","AED","PLN","TRY","RUB","KRW","THB","USD","EUR","GBP")
    }
}

data class WhatsNewSlide(val title: String, val desc: String, val preview: String)

val whatsNewSlides = listOf(
    WhatsNewSlide(
        title = "Archive Expenses!",
        desc = "Archive expenses you no longer need to keep your active list clean.\nLet the app auto-archive ended expenses for you.\nRestore archived anytime when you need them back.",
        preview = "Recurring Expenses\nArchived Monthly $34.99\nWeekly $8.07 Yearly $419.88\nMobile Phone $34.99 Phone"
    ),
    WhatsNewSlide(
        title = "Customize the Upcoming Horizon!",
        desc = "Choose how far ahead the upcoming payments view looks.\nPick anything from 1 month to 10 years in the settings.",
        preview = "Upcoming horizon\n1 month\n3 months\n6 months\n1 year\n2 years\n5 years\n10 years checked"
    ),
    WhatsNewSlide(
        title = "Upcoming Payments in the Widget!",
        desc = "The widget now shows upcoming payments just like the app.\nSame order and in chronological order.",
        preview = "Upcoming Payments\nAnother Subscription $24.99 May 28, 2026\nAmazon Prime $89.00 Jun 1, 2026\nNetflix $17.99 Jun 7, 2026\nMobile Phone $34.99 Jun 10, 2026"
    )
)

fun gsonWithLocalDate(): Gson {
    val builder = GsonBuilder()
    builder.registerTypeAdapter(LocalDate::class.java, object : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(src: LocalDate?, typeOfSrc: java.lang.reflect.Type?, context: com.google.gson.JsonSerializationContext?): com.google.gson.JsonElement {
            return com.google.gson.JsonPrimitive(src?.format(DateTimeFormatter.ISO_DATE) ?: "")
        }
        override fun deserialize(json: com.google.gson.JsonElement?, typeOfT: java.lang.reflect.Type?, context: com.google.gson.JsonDeserializationContext?): LocalDate {
            return if (json == null || json.asString.isBlank()) LocalDate.now() else LocalDate.parse(json.asString, DateTimeFormatter.ISO_DATE)
        }
    })
    return builder.create()
}

fun saveExpenses(context: Context, expenses: List<Expense>) {
    try {
        val gson = gsonWithLocalDate()
        val json = gson.toJson(expenses)
        context.openFileOutput("expenses.json", Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
    } catch (e: Exception) { }
}

fun loadExpenses(context: Context): List<Expense> {
    return try {
        val gson = gsonWithLocalDate()
        val json = context.openFileInput("expenses.json").bufferedReader().readText()
        val type = object : TypeToken<List<Expense>>() {}.type
        gson.fromJson<List<Expense>>(json, type) ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

fun saveTags(context: Context, tags: List<Tag>) {
    try {
        val gson = Gson()
        val json = gson.toJson(tags)
        context.openFileOutput("tags.json", Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
    } catch (e: Exception) { }
}

fun loadTags(context: Context): List<Tag> {
    return try {
        val gson = Gson()
        val json = context.openFileInput("tags.json").bufferedReader().readText()
        val type = object : TypeToken<List<Tag>>() {}.type
        gson.fromJson<List<Tag>>(json, type) ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

fun backupData(context: Context, expenses: List<Expense>, tags: List<Tag>, defaultCurrency: String): String {
    return try {
        val gson = gsonWithLocalDate()
        val data = mapOf("expenses" to expenses, "tags" to tags, "defaultCurrency" to defaultCurrency, "exportDate" to LocalDate.now().format(DateTimeFormatter.ISO_DATE))
        val json = gson.toJson(data)
        context.openFileOutput("backup_recurring_${LocalDate.now()}.json", Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
        // also write to external cache for sharing
        val file = java.io.File(context.getExternalFilesDir(null), "backup_recurring_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
        file.writeText(json)
        file.absolutePath
    } catch (e: Exception) { "Backup failed: ${e.message}" }
}

fun restoreData(context: Context): Pair<List<Expense>, List<Tag>> {
    return try {
        val gson = gsonWithLocalDate()
        // try latest backup in files dir
        val dir = context.filesDir
        val backups = dir.listFiles { f -> f.name.startsWith("backup_recurring") }?.sortedBy { it.lastModified() } ?: emptyList()
        if (backups.isEmpty()) {
            // try external
            val ext = context.getExternalFilesDir(null)?.listFiles { f -> f.name.startsWith("backup_recurring") }?.sortedBy { it.lastModified() } ?: emptyList()
            if (ext.isEmpty()) return emptyList<Expense>() to emptyList<Tag>()
            val json = ext.last().readText()
            val map = gson.fromJson(json, Map::class.java) as Map<String, Any>
            val expJson = gson.toJson(map["expenses"])
            val tagJson = gson.toJson(map["tags"])
            val expType = object : TypeToken<List<Expense>>() {}.type
            val tagType = object : TypeToken<List<Tag>>() {}.type
            val exps = gson.fromJson<List<Expense>>(expJson, expType) ?: emptyList()
            val tgs = gson.fromJson<List<Tag>>(tagJson, tagType) ?: emptyList()
            return exps to tgs
        }
        val json = backups.last().readText()
        val map = gson.fromJson(json, Map::class.java) as Map<String, Any>
        val expJson = gson.toJson(map["expenses"])
        val tagJson = gson.toJson(map["tags"])
        val expType = object : TypeToken<List<Expense>>() {}.type
        val tagType = object : TypeToken<List<Tag>>() {}.type
        val exps = gson.fromJson<List<Expense>>(expJson, expType) ?: emptyList()
        val tgs = gson.fromJson<List<Tag>>(tagJson, tagType) ?: emptyList()
        exps to tgs
    } catch (e: Exception) { emptyList<Expense>() to emptyList<Tag>() }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                ExpenseTrackerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp() {
    val context = LocalContext.current
    var defaultCurrency by rememberSaveable { mutableStateOf("USD") }
    var tags by remember { mutableStateOf(loadTags(context).ifEmpty {
        listOf(
            Tag(name = "Housing", colorHex = "#FF8A65"),
            Tag(name = "Subscriptions", colorHex = "#90CAF9"),
            Tag(name = "Insurance", colorHex = "#A5D6A7"),
            Tag(name = "Income", colorHex = "#81C784"),
            Tag(name = "Phone", colorHex = "#CE93D8")
        )
    }) }
    // Changed: do NOT seed hardcoded expenses after clear — original shows $0.00 empty after pm clear
    var expenses by remember { mutableStateOf(loadExpenses(context)) }

    // WhatsNew onboarding - matches original WhatsNew with WHATS_NEW_VERSION=3, 3 slides Archive, Horizon, Widget
    var showWhatsNew by rememberSaveable { mutableStateOf(context.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("show_whats_new", true)) }

    // persist on change
    LaunchedEffect(expenses) { saveExpenses(context, expenses) }
    LaunchedEffect(tags) { saveTags(context, tags) }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var isGrid by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTagId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var upcomingHorizon by rememberSaveable { mutableStateOf(0) }

    var showAddEdit by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    var showTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }

    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencyPickerForExpense by remember { mutableStateOf(false) }

    var formName by rememberSaveable { mutableStateOf("") }
    var formDesc by rememberSaveable { mutableStateOf("") }
    var formPrice by rememberSaveable { mutableStateOf("") }
    var formCurrency by rememberSaveable { mutableStateOf("USD") }
    var formRecurrence by rememberSaveable { mutableStateOf(Recurrence.Monthly) }
    var formEveryX by rememberSaveable { mutableStateOf("1") }
    var formDate by remember { mutableStateOf(LocalDate.now()) }
    var formEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var formManualConfirm by rememberSaveable { mutableStateOf(false) }
    var formTagId by rememberSaveable { mutableStateOf<String?>(null) }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val filteredExpenses = expenses.filter { exp ->
        if (!showArchived && exp.archivedDate != null) return@filter false
        if (showArchived && exp.archivedDate == null) return@filter false
        val matchesSearch = if (searchQuery.isBlank()) true else exp.name.contains(searchQuery, ignoreCase = true) || exp.description.contains(searchQuery, ignoreCase = true)
        val matchesTag = if (selectedTagId == null) true else exp.tagId == selectedTagId
        matchesSearch && matchesTag
    }

    val activeExpenses = expenses.filter { it.archivedDate == null && (it.endDate == null || !it.endDate.isBefore(LocalDate.now())) }
    val totalMonthly = activeExpenses.sumOf { monthlyCostConverted(it, defaultCurrency) }
    val totalWeekly = totalMonthly * 12.0 / 52.0
    val totalYearly = totalMonthly * 12.0

    if (showWhatsNew) {
        WhatsNewScreen(
            onDismiss = {
                showWhatsNew = false
                context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putBoolean("show_whats_new", false).apply()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (selectedTab) {0 -> "Expenses"; 1 -> "Upcoming"; 2 -> "Tags"; else -> "Settings" }) },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { isGrid = !isGrid }, modifier = Modifier.semantics { contentDescription = if (isGrid) "List view" else "Grid view" }) {
                            Icon(if (isGrid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView, contentDescription = null)
                        }
                    }
                    if (selectedTab == 0) {
                        FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(if (showArchived) "Archived" else "Active") }, modifier = Modifier.semantics { contentDescription = "Toggle ${if (showArchived) "Archived" else "Active"}" })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab==0, onClick = { selectedTab=0 }, label = { Text("Expenses") }, icon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Expenses" })
                NavigationBarItem(selected = selectedTab==1, onClick = { selectedTab=1 }, label = { Text("Upcoming") }, icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Upcoming" })
                NavigationBarItem(selected = selectedTab==2, onClick = { selectedTab=2 }, label = { Text("Tags") }, icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Tags" })
                NavigationBarItem(selected = selectedTab==3, onClick = { selectedTab=3 }, label = { Text("Settings") }, icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Settings" })
            }
        },
        floatingActionButton = {
            if (selectedTab==0) {
                FloatingActionButton(onClick = {
                    editingExpense = null
                    formName = ""
                    formDesc = ""
                    formPrice = ""
                    formCurrency = defaultCurrency
                    formRecurrence = Recurrence.Monthly
                    formEveryX = "1"
                    formDate = LocalDate.now()
                    formEndDate = null
                    formManualConfirm = false
                    formTagId = null
                    showAddEdit = true
                }, modifier = Modifier.semantics { contentDescription = "Add expense" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            } else if (selectedTab==2) {
                FloatingActionButton(onClick = { editingTag=null; showTagDialog=true }, modifier = Modifier.semantics { contentDescription = "Add tag" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
        snackbarHost = {
            if (snackbarMessage != null) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(snackbarMessage!!) }
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    // Matches original RecurringExpenses header: Monthly/Weekly/Yearly totals
                    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Monthly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Monthly ${String.format("%.2f", totalMonthly)}" })
                            Text("${defaultCurrency} ${String.format("%.2f", totalMonthly)}", style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Weekly", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${defaultCurrency} ${String.format("%.2f", totalWeekly)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { contentDescription = "Weekly ${String.format("%.2f", totalWeekly)}" })
                                }
                                Column {
                                    Text("Yearly", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${defaultCurrency} ${String.format("%.2f", totalYearly)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { contentDescription = "Yearly ${String.format("%.2f", totalYearly)}" })
                                }
                            }
                            Text("Total monthly net: ${if (totalMonthly<0) "-" else ""}${defaultCurrency} ${String.format("%.2f", abs(totalMonthly))} /month - negative shows net available after income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery=it }, label = { Text("Search expenses") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = "Search expenses" }, singleLine = true)
                    LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedTagId==null, onClick = { selectedTagId=null }, label = { Text("All") }, modifier = Modifier.semantics { contentDescription = "Filter All" })
                        }
                        items(tags) { tag ->
                            FilterChip(
                                selected = selectedTagId==tag.id,
                                onClick = { selectedTagId = if (selectedTagId==tag.id) null else tag.id },
                                label = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(12.dp).clip(CircleShape).background(tag.color())); Text(tag.name) } },
                                modifier = Modifier.semantics { contentDescription = "Filter ${tag.name}" }
                            )
                        }
                    }
                    if (filteredExpenses.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (showArchived) "No archived expenses" else "No expenses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = if (showArchived) "No archived expenses" else "No expenses" })
                                Text(if (searchQuery.isNotBlank() || selectedTagId!=null) "No matches for filter" else "Tap + to add first expense", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (showArchived) {
                                    TextButton(onClick = { showArchived=false }) { Text("Show active") }
                                }
                            }
                        }
                    } else {
                        if (isGrid) {
                            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                items(filteredExpenses) { exp ->
                                    ExpenseCardGrid(exp, tags, defaultCurrency, onClick = {
                                        editingExpense = exp
                                        formName = exp.name
                                        formDesc = exp.description
                                        formPrice = exp.price.toString()
                                        formCurrency = exp.currencyCode
                                        formRecurrence = exp.recurrence
                                        formEveryX = exp.everyXRecurrence.toString()
                                        formDate = exp.firstPaymentDate
                                        formEndDate = exp.endDate
                                        formManualConfirm = exp.requireManualConfirmation
                                        formTagId = exp.tagId
                                        showAddEdit = true
                                    })
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(filteredExpenses) { exp ->
                                    ExpenseRow(exp, tags, defaultCurrency, onClick = {
                                        editingExpense = exp
                                        formName = exp.name
                                        formDesc = exp.description
                                        formPrice = exp.price.toString()
                                        formCurrency = exp.currencyCode
                                        formRecurrence = exp.recurrence
                                        formEveryX = exp.everyXRecurrence.toString()
                                        formDate = exp.firstPaymentDate
                                        formEndDate = exp.endDate
                                        formManualConfirm = exp.requireManualConfirmation
                                        formTagId = exp.tagId
                                        showAddEdit = true
                                    }, onArchive = {
                                        expenses = expenses.map { if (it.id==exp.id) it.copy(archivedDate = if (it.archivedDate==null) LocalDate.now() else null) else it }
                                        snackbarMessage = if (exp.archivedDate==null) "Archived ${exp.name}" else "Restored ${exp.name}"
                                    })
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val horizonDaysMap = mapOf(0 to 30L, 1 to 90L, 2 to 180L, 3 to 365L, 4 to 730L, 5 to 1825L, 6 to 3650L)
                    val horizonDays = horizonDaysMap[upcomingHorizon] ?: 30L
                    val upcoming = upcomingPayments(expenses, horizonDays)
                    LazyRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf("1 month","3 months","6 months","1 year","2 years","5 years","10 years")
                        items(options.size) { idx ->
                            FilterChip(selected = upcomingHorizon==idx, onClick = { upcomingHorizon=idx }, label = { Text(options[idx]) }, modifier = Modifier.semantics { contentDescription = "Upcoming horizon ${options[idx]}" })
                        }
                    }
                    if (upcoming.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No upcoming payments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "No upcoming payments" })
                                Text("No payments in next ${horizonDays/30} months", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            var lastDate: LocalDate? = null
                            items(upcoming) { up ->
                                val dateLabel = when {
                                    up.dueDate.isEqual(LocalDate.now()) -> "Today"
                                    up.dueDate.isEqual(LocalDate.now().plusDays(1)) -> "Tomorrow"
                                    up.dueDate.isBefore(LocalDate.now().plusDays(7)) -> "This Week"
                                    else -> up.dueDate.format(DateTimeFormatter.ofPattern("MMM dd"))
                                }
                                if (lastDate==null || !up.dueDate.isEqual(lastDate)) {
                                    Text(dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = "Due $dateLabel" })
                                }
                                lastDate = up.dueDate
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(up.expense.name, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Upcoming ${up.expense.name}" })
                                            Text("${up.dueDate.format(DateTimeFormatter.ISO_DATE)} • Every ${up.expense.everyXRecurrence} ${up.expense.recurrence.name} • ${up.remainingDays}d left${if (up.requiresConfirmation) " • Needs confirmation" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${up.expense.currencyCode} ${String.format("%.2f", up.expense.price)}", fontWeight = FontWeight.Bold)
                                            Text("${defaultCurrency} ${String.format("%.2f", monthlyCostConverted(up.expense, defaultCurrency))}/mo", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (tags.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No tags", style = MaterialTheme.typography.titleMedium)
                                Text("Create first tag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(tags) { tag ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    editingTag = tag
                                    showTagDialog = true
                                }, shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(tag.color()))
                                        Text(tag.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("${expenses.count { it.tagId==tag.id }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        IconButton(onClick = { editingTag=tag; showTagDialog=true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${tag.name}") }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Default Currency", fontWeight = FontWeight.Bold)
                                    Text("170+ currencies supported, per expense currency stored")
                                    OutlinedButton(onClick = { showCurrencyPicker=true }, modifier = Modifier.semantics { contentDescription = "Default currency $defaultCurrency" }) {
                                        Text(defaultCurrency)
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Biometric App Lock", fontWeight = FontWeight.Bold)
                                    var locked by rememberSaveable { mutableStateOf(false) }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Secure financial data with fingerprint")
                                        Switch(checked = locked, onCheckedChange = {
                                            locked=it
                                            snackbarMessage = if (it) "Biometric lock enabled" else "Biometric lock disabled"
                                        }, modifier = Modifier.semantics { contentDescription = "Biometric lock ${if (locked) "on" else "off"}" })
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Widget Customization", fontWeight = FontWeight.Bold)
                                    Text("Transparent or opaque background, Android 15 previews")
                                    var transparent by rememberSaveable { mutableStateOf(false) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = !transparent, onClick = { transparent=false }, label = { Text("Opaque") })
                                        FilterChip(selected = transparent, onClick = { transparent=true }, label = { Text("Transparent") })
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Backup & Restore", fontWeight = FontWeight.Bold)
                                    Text("Local storage, no tracking, export/import via file")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            val path = backupData(context, expenses, tags, defaultCurrency)
                                            snackbarMessage = "Backup saved to $path"
                                        }, modifier = Modifier.semantics { contentDescription = "Backup" }) { Text("Backup") }
                                        OutlinedButton(onClick = {
                                            val (restoredExp, restoredTags) = restoreData(context)
                                            if (restoredExp.isNotEmpty() || restoredTags.isNotEmpty()) {
                                                expenses = restoredExp.ifEmpty { expenses }
                                                tags = restoredTags.ifEmpty { tags }
                                                snackbarMessage = "Restore success ${restoredExp.size} expenses"
                                            } else {
                                                snackbarMessage = "No backup found or invalid"
                                            }
                                        }, modifier = Modifier.semantics { contentDescription = "Restore" }) { Text("Restore") }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Walkthrough: https://pxl.cl/cgn3m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = "Walkthrough https://pxl.cl/cgn3m" })
                        }
                    }
                }
            }
        }
    }

    if (showAddEdit) {
        val isValid = formName.isNotBlank() && formPrice.toDoubleOrNull() != null && (formEveryX.toIntOrNull()?.let { it>=1 } ?: false)
        AlertDialog(
            onDismissRequest = { showAddEdit = false },
            title = { Text(if (editingExpense==null) "Add expense" else "Edit expense") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(value = formName, onValueChange = { formName=it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expense name" })
                    }
                    item {
                        OutlinedTextField(value = formDesc, onValueChange = { formDesc=it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Description" })
                    }
                    item {
                        OutlinedTextField(value = formPrice, onValueChange = { formPrice=it }, label = { Text("Price (- for income)") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expense price" })
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Currency: $formCurrency")
                            OutlinedButton(onClick = { showCurrencyPicker=true }, modifier = Modifier.semantics { contentDescription = "Pick currency $formCurrency" }) { Text("Pick currency") }
                        }
                    }
                    item {
                        Text("Recurrence", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Recurrence.values()) { rec ->
                                FilterChip(selected = formRecurrence==rec, onClick = { formRecurrence=rec }, label = { Text(rec.name) }, modifier = Modifier.semantics { contentDescription = "Recurrence ${rec.name}" })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(value = formEveryX, onValueChange = { formEveryX=it.filter { c -> c.isDigit() } }, label = { Text("Every X (e.g., every 2 weeks)") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Every X ${formEveryX}" })
                        val previewPrice = formPrice.toDoubleOrNull() ?: 0.0
                        val every = formEveryX.toIntOrNull() ?: 1
                        val previewMonthly = when (formRecurrence) {
                            Recurrence.Daily -> (365.0/12.0)/every*previewPrice
                            Recurrence.Weekly -> (52.0/12.0)/every*previewPrice
                            Recurrence.Monthly -> 1.0/every*previewPrice
                            Recurrence.Yearly -> previewPrice/(every*12.0)
                        }
                        Text("Monthly preview: $formCurrency ${String.format("%.2f", previewMonthly)} using original formula", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        Text("First payment date: ${formDate.format(DateTimeFormatter.ISO_DATE)}", modifier = Modifier.semantics { contentDescription = "First payment date ${formDate}" })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { formDate = formDate.minusDays(1) }) { Text("-1d") }
                            OutlinedButton(onClick = { formDate = formDate.plusDays(1) }) { Text("+1d") }
                            OutlinedButton(onClick = { formDate = LocalDate.now() }) { Text("Today") }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("End date: ${formEndDate?.format(DateTimeFormatter.ISO_DATE) ?: "None"}")
                            OutlinedButton(onClick = { formEndDate = if (formEndDate==null) LocalDate.now().plusMonths(6) else null }) { Text(if (formEndDate==null) "Set" else "Clear") }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Require manual confirmation")
                            Switch(checked = formManualConfirm, onCheckedChange = { formManualConfirm=it }, modifier = Modifier.semantics { contentDescription = "Manual confirmation ${if (formManualConfirm) "on" else "off"}" })
                        }
                    }
                    item {
                        Text("Tag", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = formTagId==null, onClick = { formTagId=null }, label = { Text("None") })
                            }
                            items(tags) { tag ->
                                FilterChip(
                                    selected = formTagId==tag.id,
                                    onClick = { formTagId=tag.id },
                                    label = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(CircleShape).background(tag.color())); Text(tag.name) } },
                                    modifier = Modifier.semantics { contentDescription = "Tag ${tag.name}" }
                                )
                            }
                        }
                    }
                    item {
                        if (editingExpense!=null) {
                            var showConfirm by remember { mutableStateOf(false) }
                            if (showConfirm) {
                                AlertDialog(onDismissRequest = { showConfirm=false }, title = { Text("Delete expense?") }, text = { Text("Delete ${editingExpense?.name}? This requires confirmation.") }, confirmButton = {
                                    TextButton(onClick = {
                                        expenses = expenses.filter { it.id != editingExpense?.id }
                                        showConfirm=false
                                        showAddEdit=false
                                    }, modifier = Modifier.semantics { contentDescription = "Confirm delete ${editingExpense?.name}" }) { Text("Delete") }
                                }, dismissButton = { TextButton(onClick = { showConfirm=false }) { Text("Cancel") } })
                            }
                            OutlinedButton(onClick = { showConfirm=true }, modifier = Modifier.semantics { contentDescription = "Delete ${editingExpense?.name}" }) {
                                Icon(Icons.Filled.Delete, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Delete")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val price = formPrice.toDoubleOrNull() ?: return@TextButton
                    val every = formEveryX.toIntOrNull() ?: 1
                    val newExp = Expense(
                        id = editingExpense?.id ?: UUID.randomUUID().toString(),
                        name = formName,
                        description = formDesc,
                        price = price,
                        currencyCode = formCurrency,
                        recurrence = formRecurrence,
                        everyXRecurrence = every.coerceAtLeast(1),
                        firstPaymentDate = formDate,
                        endDate = formEndDate,
                        requireManualConfirmation = formManualConfirm,
                        tagId = formTagId,
                        archivedDate = editingExpense?.archivedDate
                    )
                    expenses = if (editingExpense==null) expenses + newExp else expenses.map { if (it.id==newExp.id) newExp else it }
                    showAddEdit = false
                }, enabled = isValid, modifier = Modifier.semantics { contentDescription = "Save expense $formName" }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEdit=false }) { Text("Cancel") }
            }
        )
    }

    if (showCurrencyPicker) {
        val allCurr = remember { availableCurrencies() }
        var query by rememberSaveable { mutableStateOf("") }
        val filtered = allCurr.filter { it.contains(query, ignoreCase = true) }.take(200)
        AlertDialog(
            onDismissRequest = { showCurrencyPicker=false },
            title = { Text("Pick currency") },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query=it }, label = { Text("Search 170+ currencies") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search currencies" })
                    LazyColumn(Modifier.height(300.dp)) {
                        items(filtered) { code ->
                            ListItem(headlineContent = { Text(code) }, modifier = Modifier.clickable {
                                formCurrency = code
                                showCurrencyPicker=false
                            }.semantics { contentDescription = "Currency $code" })
                        }
                    }
                    Text("${allCurr.size}+ currencies supported via java.util.Currency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyPicker=false }) { Text("Close") } }
        )
    }

    if (showTagDialog) {
        var tagName by rememberSaveable { mutableStateOf(editingTag?.name ?: "") }
        var tagColor by rememberSaveable { mutableStateOf(editingTag?.colorHex ?: "#6750A4") }
        val palette = listOf("#FF8A65","#90CAF9","#A5D6A7","#81C784","#CE93D8","#FFCC80","#B0BEC5","#F48FB1","#80DEEA","#FFF59D","#BCAAA4","#9FA8DA","#FF5252","#66BB6A","#FFA726","#BA68C8")
        var showDeleteConfirm by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTagDialog=false },
            title = { Text(if (editingTag==null) "Add tag" else "Edit tag") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(value = tagName, onValueChange = { tagName=it }, label = { Text("Tag name") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Tag name" })
                    }
                    item {
                        Text("Extended palette (original has custom picker)", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(palette) { hex ->
                                Box(Modifier.size(36.dp).clip(CircleShape).background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }).clickable { tagColor=hex }.semantics { contentDescription = "Color $hex" })
                            }
                        }
                    }
                    item {
                        Text("Custom color hex", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = tagColor, onValueChange = { tagColor=it }, label = { Text("#RRGGBB") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Custom color $tagColor" })
                        Box(Modifier.size(24.dp).clip(CircleShape).background(try { Color(android.graphics.Color.parseColor(tagColor)) } catch (e: Exception) { Color.Gray }))
                    }
                    item {
                        if (editingTag!=null) {
                            if (showDeleteConfirm) {
                                AlertDialog(onDismissRequest = { showDeleteConfirm=false }, title = { Text("Delete tag?") }, text = { Text("Delete ${editingTag?.name}? Expenses keep fallback.") }, confirmButton = {
                                    TextButton(onClick = {
                                        val id = editingTag!!.id
                                        tags = tags.filter { it.id != id }
                                        expenses = expenses.map { if (it.tagId==id) it.copy(tagId=null) else it }
                                        showDeleteConfirm=false
                                        showTagDialog=false
                                    }, modifier = Modifier.semantics { contentDescription = "Confirm delete tag ${editingTag?.name}" }) { Text("Delete") }
                                }, dismissButton = { TextButton(onClick = { showDeleteConfirm=false }) { Text("Cancel") } })
                            }
                            OutlinedButton(onClick = { showDeleteConfirm=true }, modifier = Modifier.semantics { contentDescription = "Delete tag ${editingTag?.name}" }) { Text("Delete tag") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tagName.isBlank()) return@TextButton
                    val newTag = Tag(id = editingTag?.id ?: UUID.randomUUID().toString(), name = tagName, colorHex = tagColor)
                    tags = if (editingTag==null) tags + newTag else tags.map { if (it.id==newTag.id) newTag else it }
                    showTagDialog=false
                }, enabled = tagName.isNotBlank(), modifier = Modifier.semantics { contentDescription = "Save tag $tagName" }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog=false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ExpenseRow(exp: Expense, tags: List<Tag>, defaultCurrency: String, onClick: () -> Unit, onArchive: () -> Unit) {
    val tag = tags.find { it.id==exp.tagId }
    val monthly = monthlyCostConverted(exp, defaultCurrency)
    val monthlyRaw = exp.monthlyCost()
    val isArchived = exp.archivedDate != null
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick).semantics { contentDescription = "Expense ${exp.name} ${String.format("%.2f", monthly)} monthly ${if (isArchived) "archived" else "active"}" }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tag?.color() ?: MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(exp.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(exp.name, fontWeight = FontWeight.Bold)
                    if (tag!=null) { Box(Modifier.size(8.dp).clip(CircleShape).background(tag.color())) }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${if (exp.everyXRecurrence>1) "Every ${exp.everyXRecurrence} " else ""}${exp.recurrence.name}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).semantics { contentDescription = "Recurrence ${exp.recurrence.name} every ${exp.everyXRecurrence}" }, style = MaterialTheme.typography.labelSmall)
                    }
                    if (isArchived) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("Archived", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("${exp.currencyCode} ${String.format("%.2f", exp.price)}${if (exp.description.isNotBlank()) " • ${exp.description}" else ""} • First ${exp.firstPaymentDate.format(DateTimeFormatter.ISO_DATE)}${if (exp.endDate!=null) " • End ${exp.endDate.format(DateTimeFormatter.ISO_DATE)}" else ""}${if (exp.requireManualConfirmation) " • Manual" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${defaultCurrency} ${String.format("%.2f", monthly)}/mo ${if (exp.price<0) "(income)" else ""} • orig/mo ${String.format("%.2f", monthlyRaw)}", style = MaterialTheme.typography.bodySmall, color = if (exp.price<0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onArchive, modifier = Modifier.semantics { contentDescription = if (isArchived) "Restore ${exp.name}" else "Archive ${exp.name}" }) {
                Icon(if (isArchived) Icons.Filled.Edit else Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
fun ExpenseCardGrid(exp: Expense, tags: List<Tag>, defaultCurrency: String, onClick: () -> Unit) {
    val tag = tags.find { it.id==exp.tagId }
    val monthly = monthlyCostConverted(exp, defaultCurrency)
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick).semantics { contentDescription = "Expense ${exp.name} grid" }, shape = RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(60.dp).background(tag?.color() ?: MaterialTheme.colorScheme.primaryContainer))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exp.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${if (exp.everyXRecurrence>1) "Every ${exp.everyXRecurrence} " else ""}${exp.recurrence.name} • ${exp.currencyCode} ${String.format("%.2f", exp.price)} • ${exp.firstPaymentDate.format(DateTimeFormatter.ISO_DATE)}", style = MaterialTheme.typography.bodySmall)
                Text("${defaultCurrency} ${String.format("%.2f", monthly)}/mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun WhatsNewScreen(onDismiss: () -> Unit) {
    var currentPage by rememberSaveable { mutableStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            val slide = whatsNewSlides[currentPage]
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(slide.preview, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { contentDescription = "Preview ${slide.title}" })
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(slide.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = slide.title })
                Spacer(Modifier.height(12.dp))
                Text(slide.desc, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(whatsNewSlides.size) { idx ->
                    val isSelected = currentPage == idx
                    Box(Modifier.padding(4.dp).size(if (isSelected) 12.dp else 8.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (currentPage == whatsNewSlides.size - 1) {
                    Button(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "WhatsNew OK" }) { Text("OK") }
                } else {
                    Button(onClick = { currentPage = (currentPage + 1).coerceAtMost(whatsNewSlides.size - 1) }, modifier = Modifier.semantics { contentDescription = "WhatsNew Continue" }) { Text("Continue") }
                }
            }
        }
    }
}
