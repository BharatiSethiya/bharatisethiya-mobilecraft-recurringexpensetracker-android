package com.bharatisethiya.recurringexpensetracker

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.UUID
import kotlin.math.abs

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
    val firstPaymentDate: LocalDate = LocalDate.now(),
    val tagId: String? = null,
)

fun Expense.monthlyCost(): Double {
    val base = when (recurrence) {
        Recurrence.Daily -> price * 30.0
        Recurrence.Weekly -> price * 52.0 / 12.0
        Recurrence.Monthly -> price
        Recurrence.Yearly -> price / 12.0
    }
    return base
}

fun monthlyCostConverted(expense: Expense, defaultCurrency: String, rates: Map<String, Double>): Double {
    val rateFrom = rates[expense.currencyCode] ?: 1.0
    val rateTo = rates[defaultCurrency] ?: 1.0
    // rates map is amount of default per unit? Simplify: conversion = monthlyCost * (rateFrom / rateTo) inverted? We'll define rates as USD per foreign.
    // For simplicity: assume rates map contains factor to convert to default: if default USD, EUR 1.08 means 1 EUR = 1.08 USD
    // So converted = monthly * factor
    val factor = exchangeFactor(expense.currencyCode, defaultCurrency)
    return expense.monthlyCost() * factor
}

fun exchangeFactor(from: String, to: String): Double {
    if (from == to) return 1.0
    // hardcoded approximate rates to USD as base, then convert
    val toUsd = mapOf(
        "USD" to 1.0,
        "EUR" to 1.08,
        "GBP" to 1.27,
        "INR" to 0.012,
        "JPY" to 0.0068,
        "CAD" to 0.74,
        "AUD" to 0.66,
        "CHF" to 1.12,
        "CNY" to 0.14,
        "SEK" to 0.095,
        "NZD" to 0.61
    )
    val fromUsd = toUsd[from] ?: 1.0
    val targetUsd = toUsd[to] ?: 1.0
    // from -> USD -> to : if rates are USD per unit, then to convert from->to: fromUsd / targetUsd
    // e.g., EUR (1.08 USD) to USD (1.0) => 1.08/1.0 =1.08 correct
    // USD to EUR => 1.0/1.08 =0.925
    return fromUsd / targetUsd
}

data class UpcomingPayment(
    val expense: Expense,
    val dueDate: LocalDate,
)

fun upcomingPayments(expenses: List<Expense>, daysAhead: Long = 30): List<UpcomingPayment> {
    val today = LocalDate.now()
    val end = today.plusDays(daysAhead)
    val result = mutableListOf<UpcomingPayment>()
    for (exp in expenses) {
        var date = exp.firstPaymentDate
        // fast-forward to today or after
        var guard = 0
        while (date.isBefore(today) && guard < 1000) {
            date = advance(date, exp.recurrence)
            guard++
        }
        guard = 0
        while (!date.isAfter(end) && guard < 100) {
            if (!date.isBefore(today)) {
                result.add(UpcomingPayment(exp, date))
            }
            date = advance(date, exp.recurrence)
            guard++
        }
    }
    return result.sortedBy { it.dueDate }
}

fun advance(date: LocalDate, recurrence: Recurrence): LocalDate = when (recurrence) {
    Recurrence.Daily -> date.plusDays(1)
    Recurrence.Weekly -> date.plusWeeks(1)
    Recurrence.Monthly -> date.plusMonths(1)
    Recurrence.Yearly -> date.plusYears(1)
}

fun availableCurrencies(): List<String> {
    return try {
        Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
    } catch (e: Exception) {
        listOf("USD","EUR","GBP","INR","JPY","CAD","AUD","CHF","CNY","SEK","NZD","BRL","MXN","SGD","HKD","NOK","DKK","ZAR","AED","PLN","TRY","RUB","KRW","THB")
    }
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
    var defaultCurrency by rememberSaveable { mutableStateOf("USD") }
    var tags by remember { mutableStateOf(listOf(
        Tag(name = "Housing", colorHex = "#FF8A65"),
        Tag(name = "Subscriptions", colorHex = "#90CAF9"),
        Tag(name = "Insurance", colorHex = "#A5D6A7"),
        Tag(name = "Income", colorHex = "#81C784")
    )) }
    var expenses by remember { mutableStateOf(listOf(
        Expense(name = "Rent", description = "Monthly apartment", price = 1200.0, currencyCode = "USD", recurrence = Recurrence.Monthly, firstPaymentDate = LocalDate.now().plusDays(5), tagId = null),
        Expense(name = "Netflix", description = "Streaming", price = 15.99, currencyCode = "USD", recurrence = Recurrence.Monthly, firstPaymentDate = LocalDate.now().plusDays(10), tagId = null),
        Expense(name = "Gym", price = 25.0, currencyCode = "USD", recurrence = Recurrence.Weekly, firstPaymentDate = LocalDate.now(), tagId = null),
        Expense(name = "Salary", description = "Monthly income", price = -3000.0, currencyCode = "USD", recurrence = Recurrence.Monthly, firstPaymentDate = LocalDate.now(), tagId = null),
    )) }

    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 Expenses, 1 Upcoming, 2 Tags, 3 Settings
    var isGrid by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTagId by rememberSaveable { mutableStateOf<String?>(null) }

    var showAddEdit by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    var showTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }

    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencyPickerForExpense by remember { mutableStateOf(false) } // true for expense, false for default
    var tempExpenseCurrency by remember { mutableStateOf("USD") }

    // For expense form
    var formName by rememberSaveable { mutableStateOf("") }
    var formDesc by rememberSaveable { mutableStateOf("") }
    var formPrice by rememberSaveable { mutableStateOf("") }
    var formCurrency by rememberSaveable { mutableStateOf("USD") }
    var formRecurrence by rememberSaveable { mutableStateOf(Recurrence.Monthly) }
    var formDate by remember { mutableStateOf(LocalDate.now()) }
    var formTagId by rememberSaveable { mutableStateOf<String?>(null) }

    // rates map for conversion display (simplified)
    val rates = remember { mapOf(
        "USD" to 1.0,
        "EUR" to 1.08,
        "GBP" to 1.27,
        "INR" to 0.012
    ) }

    val filteredExpenses = expenses.filter { exp ->
        val matchesSearch = if (searchQuery.isBlank()) true else exp.name.contains(searchQuery, ignoreCase = true) || exp.description.contains(searchQuery, ignoreCase = true)
        val matchesTag = if (selectedTagId == null) true else exp.tagId == selectedTagId
        matchesSearch && matchesTag
    }

    val totalMonthly = expenses.sumOf { monthlyCostConverted(it, defaultCurrency, rates) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (selectedTab) {0 -> "Expenses"; 1 -> "Upcoming"; 2 -> "Tags"; else -> "Settings" }) },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { isGrid = !isGrid }, modifier = Modifier.semantics { contentDescription = if (isGrid) "List view" else "Grid view" }) {
                            Icon(if (isGrid) Icons.Filled.List else Icons.Filled.GridView, contentDescription = null)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab==0, onClick = { selectedTab=0 }, label = { Text("Expenses") }, icon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Expenses" })
                NavigationBarItem(selected = selectedTab==1, onClick = { selectedTab=1 }, label = { Text("Upcoming") }, icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Upcoming" })
                NavigationBarItem(selected = selectedTab==2, onClick = { selectedTab=2 }, label = { Text("Tags") }, icon = { Icon(Icons.Filled.Label, contentDescription = null) }, modifier = Modifier.semantics { contentDescription = "Tags" })
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
                    formDate = LocalDate.now()
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
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    // Total header card
                    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Total monthly net", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${if (totalMonthly<0) "-" else ""}${defaultCurrency} ${String.format("%.2f", abs(totalMonthly))} /month",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { contentDescription = "Total monthly ${String.format("%.2f", totalMonthly)}" }
                            )
                            Text("Negative represents net available funds after income", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    // Search + tag filter chips
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery=it }, label = { Text("Search expenses") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true)
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
                                Text("No expenses", style = MaterialTheme.typography.titleMedium)
                                Text(if (searchQuery.isNotBlank() || selectedTagId!=null) "No matches for filter" else "Tap + to add first expense", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        if (isGrid) {
                            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                items(filteredExpenses) { exp ->
                                    ExpenseCardGrid(exp, tags, defaultCurrency, rates, onClick = {
                                        editingExpense = exp
                                        formName = exp.name
                                        formDesc = exp.description
                                        formPrice = exp.price.toString()
                                        formCurrency = exp.currencyCode
                                        formRecurrence = exp.recurrence
                                        formDate = exp.firstPaymentDate
                                        formTagId = exp.tagId
                                        showAddEdit = true
                                    })
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(filteredExpenses) { exp ->
                                    ExpenseRow(exp, tags, defaultCurrency, rates, onClick = {
                                        editingExpense = exp
                                        formName = exp.name
                                        formDesc = exp.description
                                        formPrice = exp.price.toString()
                                        formCurrency = exp.currencyCode
                                        formRecurrence = exp.recurrence
                                        formDate = exp.firstPaymentDate
                                        formTagId = exp.tagId
                                        showAddEdit = true
                                    })
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val upcoming = upcomingPayments(expenses, 30)
                    if (upcoming.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No upcoming payments", style = MaterialTheme.typography.titleMedium)
                                Text("No payments in next 30 days", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text(dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                                }
                                lastDate = up.dueDate
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(up.expense.name, fontWeight = FontWeight.Bold)
                                            Text("${up.dueDate.format(DateTimeFormatter.ISO_DATE)} • ${up.expense.recurrence.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${up.expense.currencyCode} ${String.format("%.2f", up.expense.price)}", fontWeight = FontWeight.Bold)
                                            Text("${defaultCurrency} ${String.format("%.2f", monthlyCostConverted(up.expense, defaultCurrency, rates))}/mo", style = MaterialTheme.typography.bodySmall)
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
                                    Text("170+ currencies supported in picker")
                                    OutlinedButton(onClick = { currencyPickerForExpense=false; showCurrencyPicker=true }, modifier = Modifier.semantics { contentDescription = "Default currency ${defaultCurrency}" }) {
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
                                        Text("Secure financial data")
                                        Switch(checked = locked, onCheckedChange = { locked=it }, modifier = Modifier.semantics { contentDescription = "Biometric lock ${if (locked) "on" else "off"}" })
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
                                    FilterChip(selected = !transparent, onClick = { transparent=false }, label = { Text("Opaque") })
                                    FilterChip(selected = transparent, onClick = { transparent=true }, label = { Text("Transparent") })
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Backup & Restore", fontWeight = FontWeight.Bold)
                                    Text("Local storage, no tracking, export/import via document picker")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {}, modifier = Modifier.semantics { contentDescription = "Backup" }) { Text("Backup") }
                                        OutlinedButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Restore" }) { Text("Restore") }
                                    }
                                }
                            }
                        }
                        item {
                            Text("Walkthrough: https://pxl.cl/cgn3m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Expense dialog
    if (showAddEdit) {
        val isValid = formName.isNotBlank() && formPrice.toDoubleOrNull() != null
        AlertDialog(
            onDismissRequest = { showAddEdit = false },
            title = { Text(if (editingExpense==null) "Add expense" else "Edit expense") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(value = formName, onValueChange = { formName=it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expense name" })
                    }
                    item {
                        OutlinedTextField(value = formDesc, onValueChange = { formDesc=it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = formPrice, onValueChange = { formPrice=it }, label = { Text("Price (- for income)") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expense price" })
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Currency: ${formCurrency}")
                            OutlinedButton(onClick = { tempExpenseCurrency=formCurrency; currencyPickerForExpense=true; showCurrencyPicker=true }, modifier = Modifier.semantics { contentDescription = "Pick currency ${formCurrency}" }) { Text("Pick") }
                        }
                    }
                    item {
                        Text("Recurrence", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Recurrence.values()) { rec ->
                                FilterChip(selected = formRecurrence==rec, onClick = { formRecurrence=rec }, label = { Text(rec.name) }, modifier = Modifier.semantics { contentDescription = "Recurrence ${rec.name}" })
                            }
                        }
                        val previewPrice = formPrice.toDoubleOrNull() ?: 0.0
                        val previewMonthly = when (formRecurrence) {
                            Recurrence.Daily -> previewPrice*30
                            Recurrence.Weekly -> previewPrice*52/12
                            Recurrence.Monthly -> previewPrice
                            Recurrence.Yearly -> previewPrice/12
                        }
                        Text("Monthly preview: ${formCurrency} ${String.format("%.2f", previewMonthly)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                                AlertDialog(onDismissRequest = { showConfirm=false }, title = { Text("Delete?") }, text = { Text("Delete ${editingExpense?.name}?") }, confirmButton = {
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
                    val newExp = Expense(
                        id = editingExpense?.id ?: UUID.randomUUID().toString(),
                        name = formName,
                        description = formDesc,
                        price = price,
                        currencyCode = formCurrency,
                        recurrence = formRecurrence,
                        firstPaymentDate = formDate,
                        tagId = formTagId
                    )
                    expenses = if (editingExpense==null) expenses + newExp else expenses.map { if (it.id==newExp.id) newExp else it }
                    showAddEdit = false
                }, enabled = isValid, modifier = Modifier.semantics { contentDescription = "Save expense ${formName}" }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEdit=false }) { Text("Cancel") }
            }
        )
    }

    // Currency picker
    if (showCurrencyPicker) {
        val allCurr = remember { availableCurrencies() }
        var query by rememberSaveable { mutableStateOf("") }
        val filtered = allCurr.filter { it.contains(query, ignoreCase = true) }.take(200)
        AlertDialog(
            onDismissRequest = { showCurrencyPicker=false },
            title = { Text(if (currencyPickerForExpense) "Pick expense currency" else "Pick default currency") },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query=it }, label = { Text("Search 170+ currencies") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search currencies" })
                    LazyColumn(Modifier.height(300.dp)) {
                        items(filtered) { code ->
                            ListItem(headlineContent = { Text(code) }, modifier = Modifier.clickable {
                                if (currencyPickerForExpense) {
                                    formCurrency = code
                                } else {
                                    defaultCurrency = code
                                }
                                showCurrencyPicker=false
                            }.semantics { contentDescription = "Currency ${code}" })
                        }
                    }
                    Text("${allCurr.size}+ currencies supported", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyPicker=false }) { Text("Close") } }
        )
    }

    // Tag dialog
    if (showTagDialog) {
        var tagName by rememberSaveable { mutableStateOf(editingTag?.name ?: "") }
        var tagColor by rememberSaveable { mutableStateOf(editingTag?.colorHex ?: "#6750A4") }
        val palette = listOf("#FF8A65","#90CAF9","#A5D6A7","#81C784","#CE93D8","#FFCC80","#B0BEC5","#F48FB1","#80DEEA","#FFF59D","#BCAAA4","#9FA8DA")
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
                        Text("Extended palette", fontWeight = FontWeight.Bold)
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
                }, enabled = tagName.isNotBlank(), modifier = Modifier.semantics { contentDescription = "Save tag ${tagName}" }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog=false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ExpenseRow(exp: Expense, tags: List<Tag>, defaultCurrency: String, rates: Map<String, Double>, onClick: () -> Unit) {
    val tag = tags.find { it.id==exp.tagId }
    val monthly = monthlyCostConverted(exp, defaultCurrency, rates)
    val monthlyRaw = exp.monthlyCost()
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick).semantics { contentDescription = "Expense ${exp.name} ${String.format("%.2f", monthly)} monthly" }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tag?.color() ?: MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(exp.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(exp.name, fontWeight = FontWeight.Bold)
                    if (tag!=null) { Box(Modifier.size(8.dp).clip(CircleShape).background(tag.color())) }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(exp.recurrence.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).semantics { contentDescription = "Recurrence ${exp.recurrence.name}" }, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("${exp.currencyCode} ${String.format("%.2f", exp.price)}${if (exp.description.isNotBlank()) " • ${exp.description}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${defaultCurrency} ${String.format("%.2f", monthly)}/mo ${if (exp.price<0) "(income)" else ""}", style = MaterialTheme.typography.bodySmall, color = if (exp.price<0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${String.format("%.2f", monthlyRaw)}", style = MaterialTheme.typography.labelSmall)
                Text("orig/mo", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ExpenseCardGrid(exp: Expense, tags: List<Tag>, defaultCurrency: String, rates: Map<String, Double>, onClick: () -> Unit) {
    val tag = tags.find { it.id==exp.tagId }
    val monthly = monthlyCostConverted(exp, defaultCurrency, rates)
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick).semantics { contentDescription = "Expense ${exp.name} grid" }, shape = RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(60.dp).background(tag?.color() ?: MaterialTheme.colorScheme.primaryContainer))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exp.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${exp.recurrence.name} • ${exp.currencyCode} ${String.format("%.2f", exp.price)}", style = MaterialTheme.typography.bodySmall)
                Text("${defaultCurrency} ${String.format("%.2f", monthly)}/mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
