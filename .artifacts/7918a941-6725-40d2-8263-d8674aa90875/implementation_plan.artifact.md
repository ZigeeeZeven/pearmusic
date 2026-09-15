# Implementation Plan - Task 1: Setup and Library

Setup core dependencies, implement device storage scanning for music files, and extract metadata for 'Lossless' features.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/gradle/libs.versions.toml)
- Add Media3 versions and libraries.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/build.gradle.kts)
- Add Media3 dependencies.
- Ensure Room and Navigation 3 are correctly configured.

### Database Layer

#### [NEW] [Song.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/Song.kt)
- Define the `Song` entity with fields for metadata including `samplingRate` and `bitDepth`.

#### [NEW] [SongDao.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/SongDao.kt)
- Define Room DAO for `Song` entity.

#### [NEW] [MusicDatabase.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/MusicDatabase.kt)
- Define the Room database class.

### Storage Scanner

#### [NEW] [MusicScanner.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/scanner/MusicScanner.kt)
- Implement logic to scan device storage for MP3, FLAC, and WAV files using `ContentResolver`.
- Extract metadata using `MediaMetadataRetriever` or Media3 `MetadataRetriever`.

### Application Logic

#### [NEW] [MusicRepository.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/MusicRepository.kt)
- Orchestrate scanning and database updates.

## Verification Plan

### Automated Tests
- Create a unit test for `MusicScanner` (mocking `ContentResolver` if possible, or testing metadata extraction logic).
- Test Room database operations with an in-memory database.

### Manual Verification
- Run the app and check logs to ensure the scanner identifies files (if any are on the device/emulator).
- Build the project to ensure all dependencies are resolved.
