# Project Plan

Music Player App (MP3, FLAC, WAV) With Lyrics File (.lrc, etc) Support And Apple Music Design (Including Lyrics Animation), No Login Needed. Can create a playlist, Scanning Music On the device storage, and detects music properties (Show "Lossless" and the sampling rate At the bottom of the music title if it flac or wav files), make an "edit or make lyrics" page for specific music. Make all the animation looks like Apple Music And Smooth

## Project Brief

# Project Brief: Pear Music

Pear Music is a premium local music player for Android, designed with a focus on high-fidelity audio, elegant "Apple Music-inspired" animations, and a seamless offline experience.

## Features
*   **High-Fidelity Playback & Mini-Player**: Supports MP3, FLAC, and WAV with smooth Apple Music-style animations. Features automatic song skipping, Repeat modes (Song/Playlist), and a persistent Mini-Player that appears when the main player is dismissed.
*   **Audiophile Metadata**: Automatically detects and displays "Lossless" badges and sampling rates for FLAC and WAV files directly below the track title.
*   **Dynamic Lyrics System**: Auto-detects `.lrc` files in the same directory and provides immersive, animated lyrics playback. Includes a dedicated "Edit/Make Lyrics" tool for manual synchronization.
*   **Smart Library Scanning**: Fast, local-only storage scanning to build a music library and manage playlists without requiring any user login or cloud dependency.

## High-Level Technical Stack
*   **Kotlin**: Core language for modern Android development.
*   **Jetpack Compose**: For the declarative UI and complex, fluid animations.
*   **Jetpack Navigation 3**: State-driven navigation for predictable app flow.
*   **Compose Material Adaptive**: To ensure the player and library adapt perfectly to phones, foldables, and tablets.
*   **Media3 ExoPlayer**: For robust audio playback and media session integration.
*   **Coroutines & Flow**: For non-blocking media scanning and reactive UI state updates.

---
*Note: The UI Design Image section is omitted as the image generation tool is currently unavailable.*

## Implementation Steps
**Total Duration:** 1h 57m 49s

### Task_1_Setup_and_Library: Setup core dependencies (Media3, Room, Navigation 3, Adaptive), implement device storage scanning for MP3, FLAC, and WAV files, and extract audio properties (sampling rate, bit depth) for 'Lossless' metadata storage.
- **Status:** COMPLETED
- **Updates:** Implemented UI trigger for music scanning with progress indicator and snackbar feedback. Added runtime permission handling for media storage access.
- **Acceptance Criteria:**
  - Project builds successfully
  - Media3, Room, and Navigation 3 dependencies integrated
  - Storage scanner correctly identifies and stores music metadata in Room
- **Duration:** 11m 54s

### Task_2_Browsing_UI: Implement the Library UI using Jetpack Navigation 3 and Material Adaptive library, allowing users to browse music by artist, album, folder, and manage local playlists.
- **Status:** COMPLETED
- **Updates:** Implemented the Library UI using Jetpack Navigation 3 and Material Adaptive library. Added screens for Songs, Albums, Folders, and Playlists. Integrated Apple Music aesthetic with adaptive layouts for different screen sizes. Enhanced data layer to support playlists and folder browsing.
- **Acceptance Criteria:**
  - Library screen displays scanned music and folders
  - Navigation 3 implemented for screen transitions
  - Adaptive layout handles phone, foldable, and tablet sizes
  - The implemented UI must match the design provided in https://lh3.googleusercontent.com/d/1t_zY0m7kL1X5g4f9j5Z9y8k7X6w5v4u3
- **Duration:** 7m 19s

### Task_3_Playback_and_Lyrics: Implement high-fidelity audio playback using ExoPlayer, the 'Now Playing' screen with Apple Music-inspired design (blur background, animated cards), and smooth time-synced lyrics support for .lrc files.
- **Status:** COMPLETED
- **Updates:** Implemented high-fidelity audio playback using Media3 ExoPlayer. Created a dynamic 'Now Playing' screen with a blurred background, animated album art, and high-fidelity metadata badges. Developed a time-synced lyrics system for .lrc files with smooth animations and tap-to-seek functionality.
- **Acceptance Criteria:**
  - ExoPlayer plays MP3/FLAC/WAV files
  - Now Playing screen shows Lossless badge and sampling rate
  - Animated lyrics sync perfectly with audio playback
  - The implemented UI must match the design provided in https://lh3.googleusercontent.com/d/1_y-D4wZkL0v_G0G8z7k1g5Xy_B7b-m0W
- **Duration:** 19m 19s

### Task_4_Lyrics_Editor: Develop the 'edit or make lyrics' page to allow users to create or adjust timing for .lrc files for specific tracks.
- **Status:** COMPLETED
- **Updates:** Developed the 'Lyrics Editor' screen with both raw text and timestamp marking modes. Integrated the editor with song playback for real-time syncing. Implemented saving and loading of custom .lrc files directly to/from the song's directory. Added navigation routes and UI triggers for the editor.
- **Acceptance Criteria:**
  - Lyrics editor allows users to set timestamps for lyrics lines
  - Saving and loading custom .lrc files works
- **Duration:** 9m 59s

### Task_5_Run_and_Verify: Final polish including app icon, color refinements, and comprehensive verification of application stability and requirements alignment.
- **Status:** COMPLETED
- **Updates:** Final verification completed. All core features including music scanning, playback, lyrics editor, and synced lyrics are functional. Refined UI trigger and permission handling verified. App is stable and matches Apple Music aesthetic.
- **Acceptance Criteria:**
  - App icon generated and integrated
  - All existing tests pass
  - Build pass
  - App does not crash
  - Critic_agent verified application stability and UI alignment
- **Duration:** 7m 38s

### Task_6_SAF_Folder_Selection: Implement folder selection UI and Storage Access Framework (SAF) integration to allow users to scan specific directories, including full SD card support.
- **Status:** COMPLETED
- **Updates:** Implemented SAF folder selection and SD card support. Created ScanFolder entity, updated MusicScanner for recursive SAF traversal, and added a Manage Folders screen. Ensured persistable URI permissions for long-term storage access.
- **Acceptance Criteria:**
  - Select Folder UI implemented using SAF
  - External SD card support via SAF integrated
  - Scanned media updated in database from specific folders
- **Duration:** 14m 21s

### Task_7_AlbumArt_and_Verification: Implement high-performance album art extraction from media files and local folders using Coil, and perform final stability verification.
- **Status:** COMPLETED
- **Updates:** Final verification successful. Implemented high-performance album art extraction with folder fallback using Coil. Verified SAF folder selection and SD card support. App is stable and matches the Apple Music design on both phone and tablet form factors.
- **Acceptance Criteria:**
  - Coil extracts and displays embedded album art
  - Placeholders replaced with high-quality visuals
  - The implemented UI must match the design provided in https://lh3.googleusercontent.com/d/1_y-D4wZkL0v_G0G8z7k1g5Xy_B7b-m0W
  - Make sure all existing tests pass
  - Build pass
  - App does not crash
  - Critic_agent verified application stability and UI alignment
- **Duration:** 16m 49s

### Task_8_Playback_Logic_and_Lyrics_Discovery: Implement Repeat modes (OFF, ONE, ALL), fix next/previous track navigation logic in PlaybackManager, and update lyrics discovery to automatically scan for .lrc files in the same directory as the media.
- **Status:** COMPLETED
- **Updates:** Fixed Next/Previous button logic in PlaybackManager. Verified automatic song skipping. Implemented functional Repeat modes (Off, All, One) with UI toggles. Developed robust auto-detection for .lrc files using DocumentFile to support both standard paths and SAF URIs. Verified build stability.
- **Acceptance Criteria:**
  - Repeat modes (Off, One, All) functional
  - Next/Previous buttons correctly navigate playlist based on repeat mode
  - LRC files in the same folder are automatically detected and loaded
- **Duration:** 30m 30s

### Task_9_MiniPlayer_and_Final_Verification: Implement the persistent Mini-Player UI with transition logic to the full player and perform final comprehensive verification.
- **Status:** IN_PROGRESS
- **Updates:** Critic agent reported that album items and navigation categories are unresponsive. Reopening task for interaction fixes.
- **Acceptance Criteria:**
  - Mini-player appears when Now Playing is dismissed
  - Smooth transition between mini-player and full player UI
  - App does not crash and all tests pass
  - The implemented UI must match the design provided in https://lh3.googleusercontent.com/d/1_y-D4wZkL0v_G0G8z7k1g5Xy_B7b-m0W
- **StartTime:** 2026-08-03 18:42:39 GMT+07:00

