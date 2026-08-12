# Fixture cleanup ledger

| Unique fixture | App/package | Container/location | Creation evidence | Expected side effects | Cleanup method | Absence evidence | Status |
|---|---|---|---|---|---|---|---|
| TEST_EXP_Rent_Seed | replica | MainActivity state | hardcoded in remember if load empty | appears in list | delete via row Archive then Delete with confirmation dialog | search Rent after delete shows no match | Verified after fix |
| TEST_TAG_Groceries | replica | Tags tab FAB | create tag dialog | count 0 initially | Delete tag dialog with confirmation reassigns expenses null | tag list after delete missing Groceries | Verified |
| backup_recurring_20260812.json | replica | filesDir + external files | backupData writes via openFileOutput | file exists | rm files via context.deleteFile + external file delete | file list empty after delete | Pending - needs manual cleanup |
