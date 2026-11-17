package com.mrrwhoo1.multidl

import android.net.Uri

// Small types shared by the screens: the Screen list, completed-download model and the Python progress callback.

/** Called by yt-dlp on its worker thread; implementations should post UI updates to the main thread. */
interface DownloadProgressListener {
    fun onProgress(percent: Int, status: String)
}

// Settings storage keys (used by MainActivity).
internal const val PREFS_NAME = "multidl_settings"
internal const val KEY_DARK_THEME = "dark_theme"

internal enum class Screen { Home, AllDownloads, ActiveDownloads, Settings, About, Licences }

internal data class DownloadedMedia(
    val name: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long
)
