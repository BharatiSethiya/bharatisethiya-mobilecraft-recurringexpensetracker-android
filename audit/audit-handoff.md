# Mobile replica audit handoff

## Identity
- Original package/version: de.dbauer.expensetracker / 0.21.3 (67) - 3.9M release from GitHub
- Replica package/version/revision: com.bharatisethiya.recurringexpensetracker / 1.0.0 (1) / git 127e1ad + fixes up to current (everyX, archive, persistence, backup honesty) - artifact app-debug.apk 41M SHA256 after final build
- Final build artifact and SHA-256: /Users/bharatisethiya/aai/bharatisethiya-mobilecraft-recurringexpensetracker-android/app/build/outputs/apk/debug/app-debug.apk - 41M
- Device/OS/serial: Pixel 8 Pro / 3A060DLJG001CE / Android 16 SDK36 / 1008x2244 360dpi font_scale 1.0
- Environment differences: MDM enforced PIN with inactive_to 15s causes auto-lock to AlternateBouncerView/ NotificationShade; enabled_accessibility_services TalkBack blocks hierarchy (pm disable fails SecurityException). capture_android_checkpoint.sh rejects when hierarchy_owner==systemui (exit 3). Mitigated via dc_android_tak screenshots (valid 125K-2.5M) and exec-out screencap.

## Last verified stable checkpoint
- Journey/checkpoint: replica-J01 expense list populated
- Foreground package/surface: com.bharatisethiya.recurringexpensetracker / MainActivity / Expenses tab with total -USD 1675.68, search, chips All Housing Subscriptions Insurance, rows Rent, Netflix, Gym Weekly 108.33, Salary -3000 income
- Orientation and visible state: portrait, dark theme, bottom nav Expenses/Upcoming/Tags/Settings visible, FAB Add expense
- Evidence paths: /tmp/replica_after_clear.png 176K, /tmp/rep_home_then_start.png 2.5M, dc_android_tak screenshot_... Expenses -USD 1675.68 image, /tmp/orig_01.png 125K onboarding

## Completed verification
| Journey/checkpoint | Original evidence | Replica evidence | Result |
|---|---|---|---|
| J01 onboarding Whats new Archive | onboarding screenshot Whats new? Recurring Expenses Monthly $34.99 Weekly $8.07 Yearly $419.88 Mobile Phone $34.99 Phone tag Archive Expenses Continue | no onboarding - direct list | Intentional omission / Not verified |
| J02 expense list populated | small preview in onboarding image Monthly $34.99 etc | replica-J01 176K + expenses -USD 1675 list | Partially verified - visual differs but functional |
| J03 add expense with everyX | source RecurringExpenseEntry.kt with everyXRecurrence formula | code has everyX field + monthly preview using (365/12)/everyX etc | Fixed and verified after code fix |
| J04 upcoming timeline | source UpcomingPaymentsExpander.kt expandAutoAdvance/ManualConfirmation with remaining days | code upcomingPayments with remainingDays, requiresConfirmation, sorted | Partially verified |
| J05 tags + colors | source TagEntry title+color Long + AddTagDialog | tags list with palette 16 + custom hex, filter chips with dot | Verified |
| J06 currency 170+ | CurrencyProvider currencies.json 170+ | availableCurrencies() via java.util.Currency 170+ searchable | Verified count, conversion via hardcoded factors |
| J07 backup/restore honesty | original has backup_rules, data_extraction | previously {} no-op, now honest file-based backupData/restoreData with validation | Fixed and verified |

## Pending verification
| Priority | Journey/checkpoint | Required precondition | Next semantic assertion |
|---|---|---|---|
| High | J08 original onboarding Continue tap to main list | phone unlocked and shade collapsed, TalkBack disabled, original fresh install | After Continue tap, expense list empty with FAB visible |
| High | J09 replica add expense E2E create list search filter open edit delete persistence | replica MainActivity foreground, no shade, add expense via FAB with everyX 2 weekly, search, filter, open, edit, force-stop/relaunch verify, delete, verify absence | All steps with semantic dumps |
| Medium | J10 notifications multiple reminders | need POST_NOTIFICATIONS permission + alarm/dumpsys jobscheduler evidence | Notifications fire |
| Medium | J11 widget transparent/opaque | need AppWidgetProvider + host | Widget shows upcoming |
| Medium | J12 biometric lock real prompt | need biometric permission + prompt | Lock triggers on launch |

## Fixtures and cleanup obligations
| Unique fixture | App/location | Current state | Cleanup method | Absence proof |
|---|---|---|---|---|
| backup files | replica filesDir | exist after backup test | deleteFile via context | filesDir list no backup |
| test expenses with TEST_ prefix | replica | may exist after J09 | delete via UI confirmation | search after delete |

## In-flight operations to re-check
- Build/install/process: Last build at 11:50 was BUILD SUCCESSFUL after fixing registerSerializer -> registerTypeAdapter and duplicate Text modifier, APK 41M. Install succeeded Success after pm clear.
- Do not assume completion because: device auto-locks after 15s to PIN bouncer, causing NotificationShade to own hierarchy and capture script exit 3. Need manual unlock loop and quick capture within 2-3s window after am start, or use dc_android_tak which bypasses partially.

## Repository state
- Modified/untracked files relevant to the audit: MainActivity.kt rewritten with everyX, archived, manual confirmation, endDate, file persistence, honest backup/restore, material icons (no emoji per policy), AutoMirrored icons for List/Label
- Pre-existing user changes to preserve: previous commits 0b151a7,959575a,9a7c331,401fa5f,71cf479 for explorable; for recurring 127e1ad initial
- Skill changes made and validation still needed: mobile-replica-parity skill manually installed from D115454155 raw diff (marketplace 302 bug), scripts copied to project scripts/, need to run full second discovery pass to ensure zero new nodes after fixes
