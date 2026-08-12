# PRD - Recurring Expense Tracker

## 1. Overview

**One-liner:** Material You recurring expense tracker for rent, insurance, subscriptions, and other regular payments, with monthly cost conversion, tags, multi-currency, upcoming payments, notifications, biometric lock, and home widget.

**Product:** Offline-first personal finance utility for users who want to understand where money goes each month. Users add expenses with name, description, price, currency per expense, recurrence pattern daily, weekly, monthly, yearly, first payment date, tag with custom color palette or color picker, and view total monthly cost. Negative price represents income, enabling net available funds. All data stays on device with backup and restore, no tracking, biometric lock, notifications with multiple reminders.

## 2. Goals

- Track recurring expenses with daily, weekly, monthly, yearly recurrence and first payment date.
- Convert all expenses to monthly equivalents for comparison.
- Organize with tags evolved from color categories, extended color palette plus custom picker, list and grid views.
- Support 170+ currencies, define different currency per expense, automatic conversion to default with regularly updated exchange rates.
- Provide upcoming payments tab with payment overview and timeline.
- Provide notifications with multiple reminders per expense, customizable alerts.
- Privacy: biometric lock, local storage, backup and restore, no analytics.
- Android features: home screen widget with transparent or opaque background, Android 15 widget previews.
- Internationalization: 20+ languages via Weblate community.
- Advanced use case: net income tracking via negative expenses, unified income/expenses view.

**Non-goals:**
- Cloud sync or account that requires backend.
- Bank linking or automatic transaction import.
- Advanced budgeting beyond recurring monthly equivalents.

**Users:**
- Renters, subscribers wanting monthly spend overview.
- Multi-currency freelancers.
- Privacy-focused users wanting on-device finance.

## 3. Data Model (Product View)

**Expense:**
- Name — display title.
- Description — optional details.
- Price — decimal, negative allowed for income.
- Currency per expense — chosen from 170+ list, e.g., USD, EUR, INR.
- Recurrence — Daily, Weekly, Monthly, Yearly.
- First payment date — start date that anchors timeline.
- Monthly cost — derived: daily*30, weekly*52/12, monthly*1, yearly/12, converted via exchange rate to default currency if different.
- Tag — reference to tag id, color dot, custom color palette.
- Reminders — zero or more times per expense.
- Created time — for sorting.

**Tag:**
- Display name — e.g., Housing, Subscriptions, Insurance.
- Color — from extended palette or custom picker hex.
- Count — how many expenses reference tag.

**Currency:**
- Code — ISO 4217, 170+ entries.
- Symbol — $ € ₹ .
- Exchange rate to default — updated periodically, offline fallback uses last known.

**Upcoming Payment:**
- Expense reference.
- Due date — calculated by advancing firstPaymentDate by recurrence until after today, within next 30 days.
- Price — original price in its own currency plus converted monthly equivalent.

**Total:**
- Sum of all monthly costs in default currency, income negative subtracts, shows net available funds.

**Seed / Empty States:**
- Fresh install empty list shows illustration plus Add expense action.
- No upcoming shows no payments in next 30 days.
- No tags shows placeholder with create tag action.
- No search matches shows honest empty.

## 4. User Journeys

**J1 First Launch — Empty to First Expense:**
User installs, sees empty expense list with total zero and Add FAB. Tap FAB, form shows name field focused, price, currency picker default USD, recurrence chips Daily/Weekly/Monthly/Yearly default Monthly, first payment date picker default today, tag picker optional, description optional, save disabled until name non-empty and price valid. Enter name Rent price 1200 monthly first date 2026-08-01 tag Housing save. Returns to list, Rent shows $1200 monthly, total $1200, tag dot Housing color.

**J2 Recurrence and Monthly Conversion:**
Add weekly gym $25 weekly, monthly shows ~$108.33 (25*52/12). Add yearly insurance $600 yearly monthly $50. Add daily coffee $3 daily monthly $90. List shows all monthly equivalents, total sums. Switch list to grid view via top toggle, same content in grid cards with color header.

**J3 Negative for Income:**
Add income salary -3000 monthly, total becomes negative indicating net available funds -1650 after rent 1200 + insurance 150. List shows salary in different visual (green or negative sign). Helps monthly financial situation clarity.

**J4 Upcoming Payments:**
Open Upcoming tab, see timeline of next 30 days sorted by due date. Rent due 2026-09-01, Gym due each Monday, Insurance due yearly date. Each row shows expense name, due date, price in own currency and converted. Today section and next weeks grouped. Tapping an upcoming row opens detail/edit for that expense.

**J5 Tags and Filtering:**
Open Tags tab, see tag list with colors and counts, create new tag Groceries purple via color palette or custom hex picker. Return to Expenses, filter chips show All plus tag names plus search field for title substring. Selecting Housing chip filters to rent only, count badge updates, active filter visible, clear returns to all. Tags editable.

**J6 Multi-Currency:**
Settings or default currency picker shows 170+ currencies list with search. Add expense in EUR 50 monthly while default USD, exchange rate applied e.g., 1 EUR=1.08 USD monthly $54. App shows original €50 and converted $54. Changing default currency recalculates total.

**J7 Edit, Delete, Backup:**
Long press or tap expense opens detail with name, description, price, currency, recurrence, first date, tag, reminders, delete action with confirmation that leaves content unchanged until confirmed. Edit name, save immediate list update. More menu offers Backup and Restore, creates JSON file via system document picker, restore loads and replaces current data with validation, success feedback, cancel leaves unchanged. Biometric lock toggle in settings secures launch with fingerprint/face when enabled.

## 5. Screens Summary

| Screen | Key Elements | States |
|---|---|---|
| Expense List | Header total monthly cost net, list and grid toggle, search field, tag filter chips horizontal, FAB Add, expense rows/cards with name, monthly cost, original price and currency, recurrence badge Daily/Weekly/Monthly/Yearly, tag dot color, income negative styling | Empty no expenses, populated list, filtered by tag, search no matches, grid view, list view, total positive, total negative surplus |
| Add/Edit Expense | Name field, description, price decimal, currency picker 170+, recurrence chips, first payment date picker, tag picker with color dot, extended palette and custom hex input, reminders optional, Save enabled only when name non-empty and price valid, Delete with confirmation | Create new, edit existing, name empty save disabled, price invalid disabled, currency search, tag new, date past/future |
| Expense Detail | Name, description, price and currency, monthly converted cost, recurrence, first date, next due, tag with color, reminders list, edit and delete actions | View mode, edit mode |
| Upcoming Payments | Header next 30 days, sections Today/This Week/Next, payment rows with name, due date, price original and converted, tag dot, empty state | Empty no payments in 30 days, populated chronological |
| Tags | Tag list with color circle, name, count, create FAB, edit tag dialog with name and color palette and custom picker, delete with confirmation | Empty no tags, populated, editing, color picker open |
| Settings | Default currency, biometric lock switch, backup and restore, widget customization transparent/opaque, reminder defaults, language | Lock off/on, backup success/failure |
| Widget | Upcoming payments list on home screen, transparent or opaque background, tap opens app | No payments, has payments |

## 6. Interactions & Navigation

- App launches to Expense List with total header.
- Bottom navigation switches Expenses, Upcoming, Tags, Settings with selected state tint.
- FAB Add opens Add form with focus on name, keyboard shown, back first dismisses keyboard then form with discard confirmation if dirty.
- Save in Add/Edit validates required fields before persisting, returns to list with new total.
- List row tap opens detail/edit, long press shows quick actions edit/delete.
- List and grid toggle persists across relaunch, icon reflects current view.
- Tag chip tap filters, active chip distinct, clear via X or All chip, back clears filter before exiting.
- Currency picker shows searchable list of 170+ codes with symbol and name, tap selects.
- Date picker shows calendar, today default, past allowed.
- Recurrence chip selection updates monthly cost preview live: daily*30, weekly*4.345, monthly*1, yearly/12 with exchange applied visible.
- Negative price accepted, shown with minus sign and distinct styling, total calculates net.
- Upcoming timeline sorted ascending by due date, grouped by today, tomorrow, this week.
- Backup creates via system document picker, restore validates JSON structure before replacing, invalid file shows error and leaves data unchanged.
- Biometric lock when enabled prompts on launch, fallback to device credential, cancel exits app.

## 7. Visual Design

- Material You dynamic color, light and dark themes, dark-first for finance readability.
- Expense List: cards with rounded 24dp, surface color, tag color dot 12dp and left accent bar, monthly cost bold, recurrence badge secondary container, total header large typography.
- Add form: card with fields spaced 12dp, chips for recurrence, date field with calendar icon, tag picker with color circle, color palette grid and custom hex field with preview.
- Upcoming: timeline with vertical line, dot per payment, date header uppercase muted.
- Tags: list with color circle 20dp, count badge, FAB add.
- Empty states centered icon with guidance text and CTA button.
- Touch targets at least 48dp, dialogs rounded, primary actions reachable.

## 8. Accessibility

- Content descriptions for FAB Add expense, list/grid toggle, tag filter chips, currency picker, date picker, delete, backup, settings actions.
- Touch targets 48dp+, selection via tap and long press with haptic.
- Color not sole indicator — tag name plus color dot, recurrence badge text plus icon, negative income has minus sign and text label.
- TalkBack order header total, search, filter chips, list items, FAB.
- Text scaling respects system, no truncation via scroll.

## 9. Supported Devices & Platform Constraints

- Android phones and tablets portrait primary landscape supported.
- System document picker for backup/restore.
- Local Room storage, no network needed for core; exchange rates offline fallback uses cached.
- Biometric hardware optional — lock row hidden or disabled when unavailable.
- Widget via AppWidgetProvider with preview support.
- Config changes preserve form fields, filter state, list scroll.

## 10. Edge Cases & Validation

- Fresh install empty shows Add CTA, total zero.
- Invalid price empty or letters save disabled.
- Negative price allowed, total net can be negative showing surplus.
- Recurrence daily with first date future shows upcoming only after that date.
- Monthly recurrence on Feb 31 handles month-end clamping to last day.
- Currency conversion with missing rate shows original price and note.
- Tag delete with assigned expenses asks reassign or keep orphan with fallback color.
- Backup JSON with unknown fields ignored when known fields valid; empty file rejected.
- Search filters live substring case-insensitive, chip filter AND with search.
- Large list 500+ expenses still scrolls with sampling.
- Back handling unwinds dialog, picker, search, form, drawer, selection, list.
