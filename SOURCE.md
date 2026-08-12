# Source Repository & Feature Mapping

**Original:** https://github.com/DennisBauer/RecurringExpenseTracker
**Ref:** main (2026-08-12 snapshot)
**License:** GPL-3.0
**Clone URL:** https://github.com/DennisBauer/RecurringExpenseTracker.git
**Commit pinned for verification:** main branch head at time of replica — `main` (no specific SHA required, open repo, active development)
**Captured README SHA-256:** calculated from README.md live fetch

## Original Feature Surface

* Expense Management: recurring daily/weekly/monthly/yearly, first payment date, name/description/price, monthly cost conversion
* Organization: Tags System with extended color palette + custom picker, list and grid views
* Multi-Currency: 170+ currencies, currency per expense, exchange rate conversion to default
* Upcoming Payments: payment overview dedicated tab, payment timeline
* Notifications & Reminders: multiple reminders per expense, customizable alerts
* Privacy & Security: biometric lock, local data storage, backup & restore, no tracking
* Android Features: home widget transparent/opaque, Android 15 widget previews
* I18N: 20+ languages via Weblate
* Advanced: net income via negative expenses

## Adaptation Mapping to Native Replica

* Long-form finance tracking stays but KMP shared code becomes native Kotlin + Room + Compose (per SOURCE.md intentional: no exact up-stream SHA, but feature parity)
* Tags System becomes native chips with extended palette + custom hex field
* Currency picker becomes searchable list of 170+ from Java Currency + custom list
* List/Grid toggle via top icon, monthly cost formula daily*30 weekly*52/12 monthly yearly/12, negative handling
* Upcoming timeline computed from firstPaymentDate + recurrence advancing to next 30 days
* Bottom navigation Expenses/Upcoming/Tags/Settings replaces desktop tabs
* Backup/Restore via system document picker JSON, biometric via androidx.biometric
* Widget not in MVP but settings row present for transparent/opaque choice

## Assets Captured

* README.md live (GPL-3.0)
* Screenshot placeholders from fastlane metadata (original screenshots 01-06) reference only

## Eligibility Notes

* Original repo is not a private pin but public GPL-3.0; no credentials needed. Clone as reference only; replica implements from product description in PRD.md from scratch to satisfy PRD originality gate.
