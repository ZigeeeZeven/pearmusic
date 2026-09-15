# Fix Library Navigation and Interactivity

The goal is to fix unresponsive navigation categories, unclickable album items, and missing detail screens in the Library UI.

## User Review Required
> [!IMPORTANT]
> I will be implementing basic detail screens (Album, Artist, Playlist, Folder) to ensure navigation has a destination. If there are specific designs for these screens, they might need further refinement later.

## Proposed Changes

### Library UI Components
#### [MODIFY] [LibraryScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/library/LibraryScreen.kt)
- Add `onAlbumClick: (AlbumInfo) -> Unit` parameter to `LibraryHome`.
- Update `RecentAlbumItem` to accept an `onClick` lambda and apply `Modifier.clickable`.
- Wire up the album click in `LibraryHome`.

### Navigation & Main Entry
#### [MODIFY] [MainActivity.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/MainActivity.kt)
- Update `LibraryHome` call in `entryProvider` to handle `onAlbumClick`.
- Add missing `entry` for `LibraryDestination.Artists`.
- Add entries for detail destinations: `AlbumDetail`, `ArtistDetail`, `PlaylistDetail`, `FolderDetail`.
- Wire up `onAlbumClick`, `onFolderClick`, and `onPlaylistClick` in their respective screens to navigate to the correct detail screen.

### New Detail Screens (Stub Implementations)
#### [NEW] [AlbumDetailScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/library/AlbumDetailScreen.kt)
#### [NEW] [ArtistDetailScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/library/ArtistDetailScreen.kt)
#### [NEW] [PlaylistDetailScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/library/PlaylistDetailScreen.kt)
#### [NEW] [FolderDetailScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/library/FolderDetailScreen.kt)

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure compilation.

### Manual Verification
- Verify that clicking "Songs", "Albums", etc. in Library Home navigates correctly.
- Verify that clicking an album in "Recently Added" navigates to Album Detail.
- Verify that clicking an album in the Albums screen navigates to Album Detail.
- Verify that the MiniPlayer appears when a song is played.
