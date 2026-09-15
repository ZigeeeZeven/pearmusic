# Implementation Plan - SAF Folder Selection & SD Card Support

This plan outlines the steps to implement folder selection using Storage Access Framework (SAF), allowing users to manage scan directories and supporting SD cards for Pear Music.

## User Review Required

> [!IMPORTANT]
> The app will require `READ_EXTERNAL_STORAGE` (for legacy support if needed) and will use SAF for scoped storage compliance. Users will need to grant permission for each folder they add.

## Proposed Changes

### Data Layer

#### [NEW] [ScanFolder.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/ScanFolder.kt)
- Define `ScanFolder` entity with `uri` (primary key) and `displayName`.

#### [NEW] [ScanFolderDao.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/ScanFolderDao.kt)
- Create DAO for `ScanFolder` with basic CRUD operations.

#### [MODIFY] [MusicDatabase.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/MusicDatabase.kt)
- Add `ScanFolder` to `@Database` entities.
- Add `scanFolderDao()` abstract method.
- Increment database version if necessary (or handle migration).

#### [NEW] [FolderRepository.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/repository/FolderRepository.kt)
- Repository to handle folder persistence and SAF permission management (takePersistableUriPermission).

### Scanner Logic

#### [MODIFY] [MusicScanner.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/scanner/MusicScanner.kt)
- Update `scanStorage()` to iterate through `ScanFolder` URIs from the database.
- Use `DocumentFile` to traverse folders and scan for music files.
- Support recursive scanning if applicable.

### UI Layer

#### [NEW] [FolderManagementViewModel.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/settings/FolderManagementViewModel.kt)
- ViewModel to manage the state of the "Manage Folders" screen.

#### [NEW] [FolderManagementScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/settings/FolderManagementScreen.kt)
- Compose UI for listing and adding/removing scan folders.
- Integrate with SAF `ActivityResultLauncher`.

#### [MODIFY] [Navigation.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/navigation/Navigation.kt)
- Add route for Folder Management screen.

## Verification Plan

### Automated Tests
- Unit tests for `ScanFolderDao`.
- Mock tests for `MusicScanner` traversing a simulated file structure.

### Manual Verification
- Launch the app, navigate to "Manage Folders".
- Add a local folder and verify scanning.
- Add an SD card folder (if available in emulator/device) and verify scanning.
- Restart the app and ensure folders are still present and permissions persist.
