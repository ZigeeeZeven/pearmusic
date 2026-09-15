# Implementation Plan - Features and Optimizations

This plan covers adding page transitions, LRCLIB lyrics integration, app optimizations, and queue reordering.

## User Review Required

> [!IMPORTANT]
> - **Page Transitions:** Implementing "Apple Music-like" transitions might require changing how `NavDisplay` is used or wrapping it in `AnimatedContent`. This may affect how back navigation feels.
> - **LRCLIB Search:** I will add a "Search Online" button in the Lyrics Editor. Would you also like an automatic search if local lyrics are missing?
> - **Optimization:** Enabling R8 (obfuscation/shrinking) can sometimes cause issues with reflection-based libraries (like Moshi/Retrofit). I will add necessary `@Keep` annotations or Proguard rules.

## Proposed Changes

### 1. Navigation & Transitions
Implement smooth transitions between screens, specifically a "slide up" effect for the Player and "slide horizontal" for others.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/MainActivity.kt)
- Wrap `NavDisplay` content or customize `sceneStrategy` to add slide/fade animations.

---

### 2. LRCLIB Lyrics Integration
Add support for searching and downloading synced lyrics from LRCLIB.

#### [NEW] [LrcLibService.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/api/LrcLibService.kt)
- Define Retrofit interface for LRCLIB API.

#### [MODIFY] [LyricsEditorViewModel.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/lyrics/LyricsEditorViewModel.kt)
- Add `searchLrcLib()` function to fetch synced lyrics.

#### [MODIFY] [LyricsEditorScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/lyrics/LyricsEditorScreen.kt)
- Add a "Search LRCLIB" button.

---

### 3. Queue Management & Reordering
Enable dragging and reordering songs in the queue and show the queue in `NowPlayingScreen`.

#### [MODIFY] [PlaybackManager.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/playback/PlaybackManager.kt)
- Add `reorderQueue(fromIndex: Int, toIndex: Int)` and expose the current queue as a `State`.

#### [MODIFY] [NowPlayingViewModel.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/player/NowPlayingViewModel.kt)
- Expose the queue and add a `reorderQueue` function.

#### [MODIFY] [NowPlayingScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/player/NowPlayingScreen.kt)
- Implement a `QueueBottomSheet`.
- Use a reorderable list for the songs in the queue.
- Hook up the Queue button to show the bottom sheet.

---

### 4. App Optimization
Reduce app size and improve performance.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/build.gradle.kts)
- Enable `minifyEnabled` and `shrinkResources` in release/debug builds (if desired).
- Optimize dependencies.

---

## Verification Plan

### Automated Tests
- Run `./gradlew assembleRelease` to verify R8 doesn't break the build.

### Manual Verification
- Verify page transitions are smooth.
- Search for a song on LRCLIB and check if synced lyrics appear.
- Open the queue on `NowPlayingScreen`, drag a song to a new position, and verify playback order changes.
- Check the APK size before and after optimizations.
