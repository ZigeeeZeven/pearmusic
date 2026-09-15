# 🍐 Pear Music

> **Note:** This project is just for my boredom.

A modern, lightweight Android audio player built with Kotlin and Jetpack Compose.

---

## ✨ Features

- 🎵 **Local Music Scanning:** Automatic background scanning and metadata indexing for local audio files (`MusicScanner.kt`).
- 🎨 **Jetpack Compose UI:** Built with modern Android declarative UI components (`NowPlayingScreen.kt`).
- ⏱️ **Lyrics Synchronization:** Fine-tune lyrics timing on the fly using the built-in `LyricsDelayDialog`.
- 🗂️ **Architecture:** Clean MVVM design pattern paired with a dedicated repository pattern (`MusicRepository.kt`, `LibraryViewModel.kt`).

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern
- **Package Name:** `com.endfield.pearmusic`
- **Build System:** Gradle (Kotlin DSL `.gradle.kts`)

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17+
- Android Device (Android 7.0+ / API level 26+)

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/ZigeeeZeven/pearmusic.git](https://github.com/ZigeeeZeven/pearmusic.git)
