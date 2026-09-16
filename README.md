# 🍐 Pear Music

> **Note:** This project is just for my boredom. **I know this app still has a lot of bugs!** If you run into any issues, please report them to **zevenn07@outlook.com**.
> 
> 🌐 **This App Also Has A Web Version!** Check it out here: [pearmusic-web.vercel.app](https://pearmusic-web.vercel.app)

A modern, high-performance, lightweight Android audio player built with Kotlin, Jetpack Compose, and advanced audio pipeline caching. Optimized for near-instant library scanning, smooth 60fps scrolling, and zero-bandwidth cloud directory browsing.

---

## ✨ Features

### ⚡ Performance & Scanning Architecture
- 🚀 **Two-Phase Fast Indexing:** Ultra-fast initial scan using direct `MediaStore` batch queries for local storage and database cursor projections for SAF tree traversing (`MusicScanner.kt`, `DocumentUtils.kt`).
- ☁️ **Cloud Storage Optimization (Google Drive SAF):** Zero-bandwidth directory enumeration. Reads database metadata rows without downloading cloud audio streams during browsing.
- 🎯 **On-Demand Deep Metadata Loading:** Heavy metadata parsing (ReplayGain, bit depth, PCM encoding, sampling rate) is deferred until a track is explicitly played.

### 🎨 Modern UI & Compose Optimization
- 📱 **Jetpack Compose & Adaptive Layouts:** Built with modern declarative UI components, featuring a two-pane responsive layout (`ListDetailPaneScaffold`) for tablets and large screens (`LibraryScreen.kt`).
- ⚡ **Zero-Flicker Lists:** Enforced persistent item keying across `LazyColumn` and `LazyGrid` to eliminate view re-binding and list stutter during rapid scrolling.
- 🖼️ **Smart Artwork Fetcher & LRU Cache:** Custom Coil image loader pipeline (`MediaArtFetcher.kt`) utilizing an in-memory byte cache and system-optimized album art URIs (`content://media/external/audio/albumart/{album_id}`). Includes fallback to folder cover images (`cover.jpg`, `folder.jpg`).

### 🎵 Playback & Lyric Synchronization
- 🔍 **Automatic Lyrics Search:** Automatically fetches synced and plain text lyrics from LRCLIB based on track title, artist, album, and duration when a song begins playing (`LrcLibService.kt`).
- ⏱️ **Fine-Tuned Lyrics Sync:** On-the-fly timing adjustments for synchronized lyrics via the built-in `LyricsDelayDialog`.
- 🎼 **Comprehensive Organization:** Filter and view library content by Songs, Albums, Artists, Playlists, and File Folders.
- 📝 **Playlist Management:** Complete support for creating, editing, reordering, and deleting local playlists, alongside custom `.m3u8` playlist file importing.
- 🎛️ **Advanced Audio Tagging & ReplayGain:** Native ReplayGain track gain parsing via low-level file descriptor access without thread blocking.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3 & Adaptive Design)
- **Architecture:** MVVM (Model-View-ViewModel) + Clean Repository Pattern
- **Async & Reactive Data:** Kotlin Coroutines, StateFlow, SharedFlow
- **Image Loading:** Coil (Custom Fetcher & Memory Caching)
- **Database:** Room Database
- **Networking & API:** Retrofit 2, Moshi, OkHttp 3 (LRCLIB Integration)
- **Storage:** Storage Access Framework (SAF) & Android `MediaStore`
- **Build System:** Gradle (Kotlin DSL `.gradle.kts`)
- **Package Name:** `com.endfield.pearmusic`

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17+
- Android Device (Android 7.0+ / API level 24+)

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/ZigeeeZeven/pearmusic.git](https://github.com/ZigeeeZeven/pearmusic.git)
   cd pearmusic
