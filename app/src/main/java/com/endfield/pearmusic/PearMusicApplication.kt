package com.endfield.pearmusic

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.endfield.pearmusic.data.MusicRepository
import com.endfield.pearmusic.data.local.MusicDatabase
import com.endfield.pearmusic.data.repository.FolderRepository
import com.endfield.pearmusic.data.repository.SettingsRepository
import com.endfield.pearmusic.data.scanner.MusicScanner
import com.endfield.pearmusic.api.LrcLibService
import com.endfield.pearmusic.playback.PlaybackManager
import com.endfield.pearmusic.ui.coil.MediaArtFetcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.google.firebase.FirebaseApp

class PearMusicApplication : Application(), ImageLoaderFactory {

    lateinit var database: MusicDatabase
    lateinit var repository: MusicRepository
    lateinit var folderRepository: FolderRepository
    lateinit var settingsRepository: SettingsRepository
    lateinit var playbackManager: PlaybackManager
    lateinit var lrcLibService: LrcLibService
    lateinit var driveRepository: com.endfield.pearmusic.data.repository.DriveAudioRepository
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(MediaArtFetcher.Factory(this@PearMusicApplication))
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        database = Room.databaseBuilder(
            this,
            MusicDatabase::class.java,
            "pear_music_db"
        ).fallbackToDestructiveMigration().build()

        folderRepository = FolderRepository(
            this,
            database.scanFolderDao()
        )

        settingsRepository = SettingsRepository(this)

        val musicScanner = MusicScanner(this)

        repository = MusicRepository(
            this,
            database.songDao(),
            database.playlistDao(),
            musicScanner
        )

        playbackManager = PlaybackManager(this, repository)

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "PearMusic (https://github.com/endfield/pearmusic)")
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        lrcLibService = retrofit.create(LrcLibService::class.java)

        val driveRetrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/drive/v3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        val driveApi = driveRetrofit.create(com.endfield.pearmusic.api.GoogleDriveApi::class.java)

        val driveApiKey: String? = "AIzaSyC0sftPOEBF1tty9iLWcdWxjb0IBnpHMQ4"
        driveRepository = com.endfield.pearmusic.data.repository.DriveAudioRepository(
            this,
            driveApi,
            database.songDao(),
            okHttpClient,
            driveApiKey
        )
    }
}