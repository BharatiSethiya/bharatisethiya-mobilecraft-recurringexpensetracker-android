# Setup - Recurring Expense Tracker

Native Android replica of DennisBauer RecurringExpenseTracker with Material You, monthly conversion, tags, multi-currency, upcoming timeline, backup/restore, biometric lock, widget customization.

## Requirements

- Docker with Docker Compose plugin
- Android SDK platform-tools adb on host
- One authorized Android device or emulator for start.sh and smoke
- No credentials needed (offline app with local Room storage)

## How to clone

```sh
git clone https://github.com/codimango/bharatisethiya-mobilecraft-recurringexpensetracker-android.git
cd bharatisethiya-mobilecraft-recurringexpensetracker-android
```

## Source Repository and Feature Access

Pin original for product and visual reference:

```sh
git clone https://github.com/DennisBauer/RecurringExpenseTracker recurring-reference
git -C recurring-reference checkout main
ls recurring-reference/README.md
```

No API keys, backend, or account required. All replica features available after install. Exchange rates use cached fallback when offline. Biometric lock uses system biometric prompt.

## Commands

| Command | Purpose |
|---|---|
| ./verify.sh --plan | Print verification steps without running |
| ./verify.sh | Build debug APK inside Docker app-runner |
| ./start.sh | Install and launch debug app via Dockerized adb |
| ./stop.sh | Force-stop app via Dockerized adb |
| ./scripts/android/smoke.sh | Launch, verify total header, add expense, assert monthly appears, check upcoming |

## Local macOS Flow

1. Verify:
```sh
./verify.sh
# expect APK at app/build/outputs/apk/debug/app-debug.apk
```

2. Bridge host adb:
```sh
./scripts/adb-bridge.sh start
adb devices # must show device
```

3. Install and launch:
```sh
./start.sh
```

4. Smoke:
```sh
./scripts/android/smoke.sh
```

5. Manual exercise:
- Launch shows empty expenses with total 0 and FAB Add expense.
- Tap FAB, enter name Rent price 1200 monthly first date today tag Housing save. List shows Rent $1200 monthly and total header updates.
- Add weekly Gym $25 weekly -> monthly ~$108.33 and total sums.
- Add income Salary -3000 monthly -> total net negative -1650 showing net available funds.
- Toggle list/grid icon top bar.
- Tap expense row opens edit with name/description/price/currency/recurrence/date/tag/delete with confirmation.
- Open Upcoming tab, see timeline next 30 days sorted, each shows due date, original and converted price.
- Open Tags tab, create new tag Groceries purple via palette or custom hex, see count, filter chips in Expenses filter to Housing only then clear.
- Open currency picker searchable 170+ codes.
- Settings: default currency, biometric lock switch, backup/restore via document picker.

6. Stop:
```sh
./stop.sh
```

## Project Structure

- app/src/main/java/com/bharatisethiya/recurringexpensetracker/MainActivity.kt bottom nav Expenses/Upcoming/Tags/Settings
- model/Expense.kt Expense, Recurrence Daily/Weekly/Monthly/Yearly, monthly cost calculation, upcoming due calculation
- model/Tag.kt Tag with color
- model/CurrencyUtils.kt 170+ currencies list and exchange
- data/AppDatabase.kt Room entities
- ui/screens/ExpenseListScreen, AddEdit, UpcomingScreen, TagsScreen
- ui/components/ native cards, chips, palette
- environment/Dockerfile Android SDK 35 + Gradle 8.10.2
- mobile.toml manifest with PRD and video https://pxl.cl/cgn3m and screenshots
- PRD.md product requirements from scratch (>800 words)
- features.json 5 rubrics
- screenshots/ evidence

## Walkthrough Videos

- Walkthrough: https://pxl.cl/cgn3m — demonstrates adding expenses, monthly conversion, negative income net, list/grid, tag filter with custom color, currency picker 170+, upcoming timeline 30 days, edit/delete with confirmation, backup/restore.

Internal source: https://www.internalfb.com/intern/px/p/cgn3m

## No secrets

No API keys, no tracking.

## Verification

```sh
./verify.sh --plan
./verify.sh
```
