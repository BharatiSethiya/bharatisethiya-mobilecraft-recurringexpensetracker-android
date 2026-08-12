# Capture manifest

## Audit identity
- Capture UTC range: 2026-08-12T11:22Z to 2026-08-12T11:50Z
- Original package/version: de.dbauer.expensetracker / 0.21.3 (67) - 3.9M release
- Replica package/version/revision: com.bharatisethiya.recurringexpensetracker / 1.0.0 (1) - 41M debug after fixes - git 71cf479..127e1ad + fixes
- Replica artifact/SHA-256: app/build/outputs/apk/debug/app-debug.apk (41M) SHA256 computed after build
- Product documents/digests: PRD.md 1714 words, features.json 5 rubrics, SETUP.md, mobile.toml with pxl.cl/cgn3m walkthrough
- Walkthrough provenance: https://pxl.cl/cgn3m (public short for internal https://www.internalfb.com/intern/px/p/cgn3m) verified 302 redirect

## Environment
| Field | Value |
|---|---|
| Device / serial | Pixel 8 Pro / 3A060DLJG001CE |
| OS / API | Android 16 / 36 |
| Resolution / density | 1008x2244 / 360 |
| Font / display scale | 1.0 / default |
| Locale / timezone | en_US / UTC |
| Theme / orientation | Dark / portrait |
| Navigation / keyboard | Gesture / Gboard |
| Animations / accessibility services | enabled_accessibility_services initially accessibilitymenu+TalkBack, then null after disable attempt, TalkBack pm disable fails SecurityException shell cannot change |

## Capture index
| ID | App | Journey/checkpoint | Fixture/state | Screenshot | Tree | Metadata/video | Validated foreground |
|---|---|---|---|---|---|---|---|
| original-J01 | de.dbauer.expensetracker | J01 onboarding Whats new Archive | empty | /tmp/orig_01.png 125K + screenshot_..._Archive.png | systemui 55 nodes - blocked by PIN bouncer | dumpsys window mCurrentFocus MainActivity at launch then NotificationShade | Partially - screencap valid but uiautomator blocked |
| replica-J01 | com.bharatisethiya.recurringexpensetracker | J01 expense list populated | seeded 5 expenses | /tmp/replica_after_clear.png 176K + dc_tak Expenses -USD 1675.68 | systemui when locked, 0 nodes for replica when cleared | mCurrentFocus replica MainActivity after pm clear + home + start with TotalTime 798 | Validated via dc_android_tak despite overlay |
| replica-J02 | replica | J02 add FAB | tap | /tmp/recurr_add_final.png 14K black - shade | blocked | attempt | Not verified - shade |
| original-J02 | original | J02 Continue onboarding | tap 500 900 | /tmp/orig_cont1.png 13K black | shade | blocked | Not verified |
