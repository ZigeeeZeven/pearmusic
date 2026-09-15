# ReplayGain Support and Settings Toggle

Implement ReplayGain support to normalize audio loudness across different tracks, and provide a toggle in the settings to enable/disable this feature.

## Proposed Changes

### Data Layer

#### [MODIFY] [Song.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/local/Song.kt)
- Add `replayGain: Float? = null` to the `Song` entity.

#### [MODIFY] [MusicScanner.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/scanner/MusicScanner.kt)
- Update `AudioMetadata` data class to include `replayGain: Float?`.
- Update `getFullMetadata` to extract ReplayGain tags using `jaudiotagger` (using the temp file strategy already established in `LyricsEmbedder`).
- Convert dB values from tags to linear gain values.

#### [MODIFY] [MusicRepository.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/MusicRepository.kt)
- Update `loadSongMetadata` to save the `replayGain` value into the database.

#### [MODIFY] [SettingsRepository.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/data/repository/SettingsRepository.kt)
- Add `REPLAY_GAIN` preference key.
- Add `replayGain: Flow<Boolean>` and `setReplayGain(Boolean)` method.

---

### Playback Layer

#### [MODIFY] [SoftLimiterAudioProcessor.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/playback/SoftLimiterAudioProcessor.kt)
- Add `trackGain: Float = 1.0f` and `isReplayGainEnabled: Boolean = false`.
- Update `queueInput` to apply the `trackGain` to PCM samples when `isReplayGainEnabled` is true.

#### [MODIFY] [PlaybackService.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/playback/PlaybackService.kt)
- Observe `settingsRepository.replayGain` and update `softLimiter.setReplayGainEnabled()`.
- Register a `Player.Listener` on the ExoPlayer instance to handle `onMediaItemTransition`.
- Extract `replay_gain` from `MediaItem` extras and update `softLimiter.setTrackGain()`.

#### [MODIFY] [PlaybackManager.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/playback/PlaybackManager.kt)
- In `playSong`, add the song's `replayGain` (defaulting to 1.0f) to the `MediaItem` extras.

---

### UI Layer

#### [MODIFY] [SettingsViewModel.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/settings/SettingsViewModel.kt)
- Expose `replayGain: StateFlow<Boolean>`.
- Add `setReplayGain(Boolean)` method.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/rayha/AndroidStudioProjects/PearMusic/app/src/main/java/com/endfield/pearmusic/ui/settings/SettingsScreen.kt)
- Add a `SwitchSettingsItem` for "ReplayGain" in the audio category.

## Verification Plan

### Manual Verification
1. Open Settings and verify the "ReplayGain" toggle appears.
2. Toggle ReplayGain on and off.
3. Play a song known to have a strong ReplayGain adjustment (e.g., -10dB) and verify that the volume decreases when the setting is enabled.
4. Verify that switching between tracks with different gain values maintains a consistent perceived loudness.
5. Check Logcat for any `jaudiotagger` errors during scanning.
