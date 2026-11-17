package com.mrrwhoo1.multidl

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import java.util.concurrent.Executors

// App state, download logic and screen switching. The screens it shows live in their own files.

/**
 * Holds all app state and the download logic, and decides which screen to show.
 * The screens below are "dumb": they only draw what they are given.
 */
@Composable
internal fun MultiDLScreen(
    isDark: Boolean,
    onDarkChange: (Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("HD") }
    var screen by remember { mutableStateOf(Screen.Home) }
    var message by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadStatus by remember { mutableStateOf("Preparing download…") }
    var downloadQueue by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var downloadedFiles by remember {
        mutableStateOf<List<DownloadedMedia>>(emptyList())
    }

    val downloadExecutor = remember { Executors.newSingleThreadExecutor() }
    val activityContext = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refreshDownloads() {
        val resolver = activityContext.contentResolver

        // Diagnostics: filter Logcat by "MultiDL". Permission value 0 = granted, -1 = denied.
        Log.d(
            "MultiDL",
            "SDK=${Build.VERSION.SDK_INT}, " +
            "video=${ContextCompat.checkSelfPermission(activityContext, Manifest.permission.READ_MEDIA_VIDEO)}, " +
            "audio=${ContextCompat.checkSelfPermission(activityContext, Manifest.permission.READ_MEDIA_AUDIO)}"
        )

        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("Download/MultiDL%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val files = mutableListOf<DownloadedMedia>()
        val seenIds = mutableSetOf<Long>()

        // After a reinstall Android no longer treats this app as the owner of its old
        // files. With the media permission they are visible as video/audio, so the
        // Video and Audio collections are scanned as well as Downloads. The same file
        // can appear in more than one collection, so duplicates are skipped by _ID.
        val collections = listOf(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        for (collection in collections) {
            val before = files.size
            try {
                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn) ?: continue
                        val id = cursor.getLong(idColumn)

                        if (!seenIds.add(id)) continue

                        files.add(
                            DownloadedMedia(
                                name = name,
                                uri = android.content.ContentUris.withAppendedId(collection, id),
                                mimeType = cursor.getString(mimeColumn) ?: "application/octet-stream",
                                size = cursor.getLong(sizeColumn),
                                dateAdded = cursor.getLong(dateColumn)
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Media permission not granted (yet): skip this collection.
                // The permission launcher below calls refreshDownloads() again after the user answers.
                Log.w("MultiDL", "Scan blocked for $collection: ${e.message}")
            }
            Log.d("MultiDL", "Scan $collection found ${files.size - before} new files")
        }

        downloadedFiles = files.sortedByDescending { it.dateAdded }
    }

    // Needed to see files saved by a previous install of the app (see Android scoped storage rules).
    val storagePermissions = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Runs after the user taps Allow or Deny: reload the list either way.
        refreshDownloads()
    }

    LaunchedEffect(Unit) {
        refreshDownloads()

        val missing = storagePermissions.any {
            ContextCompat.checkSelfPermission(activityContext, it) !=
            PackageManager.PERMISSION_GRANTED
        }

        if (missing) {
            permissionLauncher.launch(storagePermissions)
        }
    }

    fun openDownloadedFile(file: DownloadedMedia) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activityContext, "No compatible app found to open this file.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activityContext, "Could not open this file.", Toast.LENGTH_LONG).show()
        }
    }

    fun deleteDownloadedFile(file: DownloadedMedia) {
        try {
            activityContext.contentResolver.delete(file.uri, null, null)
            refreshDownloads()
            message = ""
        } catch (e: Exception) {
            message = "Could not delete file: ${e.message ?: "unknown error"}"
        }
    }

    fun renameDownloadedFile(file: DownloadedMedia, requestedName: String) {
        val trimmed = requestedName.trim()
        if (trimmed.isBlank()) {
            message = "File name cannot be empty."
            return
        }
        val extension = file.name.substringAfterLast('.', "")
        val safeName = trimmed.replace(Regex("[/\\\\]"), "_")
        val finalName = if (extension.isNotBlank() && !safeName.endsWith(".$extension", ignoreCase = true)) {
            "$safeName.$extension"
        } else safeName
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                }
                val changed = activityContext.contentResolver.update(file.uri, values, null, null)
                if (changed > 0) {
                    refreshDownloads()
                    message = "Renamed to $finalName"
                } else {
                    message = "Could not rename this file."
                }
            } catch (e: Exception) {
                message = "Could not rename file: ${e.message ?: "unknown error"}"
            }
    }

    // The download manager controls the queue. The executor runs the existing
    // Python downloader away from the main UI thread.
    val downloadManager = remember {
        MultiDLDownloadManager(
            onQueueChanged = { updatedQueue ->
                mainHandler.post {
                    downloadQueue = updatedQueue

                    val activeDownload = updatedQueue.firstOrNull {
                        it.state == DownloadState.DOWNLOADING
                    }

                    isDownloading = activeDownload != null

                    if (activeDownload != null) {
                        downloadProgress = activeDownload.progress
                        downloadStatus = activeDownload.status
                    } else if (updatedQueue.none {
                        it.state == DownloadState.QUEUED
                    }) {
                        downloadProgress = 100
                    }
                }
            },
            onStartDownload = { item, onProgress, onComplete ->
                downloadExecutor.execute {
                    try {
                        val python = Python.getInstance()
                        val downloader = python.getModule("android_download_handler")

                        val downloadDir = File(
                            activityContext.getExternalFilesDir(null),
                                               "MultiDL"
                        ).apply { mkdirs() }

                        val result: PyObject = downloader.callAttr(
                            "download",
                            item.format,
                            item.url,
                            object : DownloadProgressListener {
                                override fun onProgress(percent: Int, status: String) {
                                    onProgress(percent, status)
                                }
                            },
                            downloadDir.absolutePath
                        )

                        val success = result.callAttr("get", "success").toBoolean()
                        val title = result.callAttr("get", "title").toString()
                        val filePath = result.callAttr("get", "file_path").toString()

                        var savedToPublicDownloads = false

                        if (success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val sourceFile = File(filePath)

                            if (sourceFile.isFile) {
                                val extension = sourceFile.extension.lowercase()

                                val mimeType = when (extension) {
                                    "mp3" -> "audio/mpeg"
                                    "mp4", "m4v" -> "video/mp4"
                                    "mkv" -> "video/x-matroska"
                                    "webm" -> "video/webm"
                                    "m4a" -> "audio/mp4"
                                    "aac" -> "audio/aac"
                                    "opus", "ogg" -> "audio/ogg"
                                    "wav" -> "audio/wav"
                                    "flac" -> "audio/flac"
                                    else -> "application/octet-stream"
                                }

                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MultiDL")
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                }

                                val resolver = activityContext.contentResolver
                                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                                val uri = resolver.insert(collection, values)

                                if (uri != null) {
                                    try {
                                        val output = resolver.openOutputStream(uri)
                                        ?: throw java.io.IOException(
                                            "Could not open destination file."
                                        )

                                        output.use { destination ->
                                            sourceFile.inputStream().use { input ->
                                                input.copyTo(destination)
                                            }
                                        }

                                        val completedValues = ContentValues().apply {
                                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                                        }

                                        resolver.update(uri, completedValues, null, null)
                                        savedToPublicDownloads = true
                                    } catch (e: Exception) {
                                        resolver.delete(uri, null, null)
                                        throw e
                                    }
                                }
                            }
                        }

                        if (savedToPublicDownloads) {
                            mainHandler.post {
                                refreshDownloads()
                                message = "Completed"
                            }
                            onComplete(true, title, null)
                        } else {
                            val error = "Couldn't save the downloaded file to Downloads/MultiDL."
                            mainHandler.post {
                                message = error
                            }
                            onComplete(false, title, error)
                        }
                    } catch (e: Exception) {
                        val error = e.message ?: "Could not start Python downloader."
                        mainHandler.post {
                            message = "Download failed: $error"
                        }
                        onComplete(false, "", error)
                    }
                }
            }
        )
    }

    fun startDownload() {
        val submittedUrl = url.trim()

        when {
            submittedUrl.isBlank() -> {
                message = "Enter a video URL first."
            }

            !submittedUrl.startsWith("https://") &&
            !submittedUrl.startsWith("http://") -> {
                message = "Enter a valid URL starting with http:// or https://."
            }

            else -> {
                message = ""
                downloadManager.addDownload(submittedUrl, selectedFormat)
                url = ""
            }
        }
    }

    // System back gesture: About and Licences return to Settings.
    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.About, Screen.Licences -> Screen.Settings
            else -> Screen.Home
        }
    }
    AppBackground {
        when (screen) {
            Screen.Home -> HomeScreen(
                url = url,
                onUrlChange = {
                    url = it
                    message = ""
                },
                selectedFormat = selectedFormat,
                onFormatSelected = { selectedFormat = it },
                files = downloadedFiles,
                isDownloading = isDownloading,
                queuedDownloads = downloadQueue.count {
                    it.state == DownloadState.QUEUED
                },
                downloadProgress = downloadProgress,
                downloadStatus = downloadStatus,
                message = message,
                onOpenFile = { openDownloadedFile(it) },
                                      onDeleteFile = { deleteDownloadedFile(it) },
                                      onRenameFile = { file, newName -> renameDownloadedFile(file, newName) },
                                      onViewAll = { screen = Screen.AllDownloads },
                                      unfinishedDownloads = downloadQueue.count { it.isUnfinished() },
                                      onOpenDownloads = { screen = Screen.ActiveDownloads },
                                      onOpenSettings = { screen = Screen.Settings },
                                      onDownload = { startDownload() }
            )

            Screen.AllDownloads -> AllDownloadsScreen(
                files = downloadedFiles,
                onOpenFile = { openDownloadedFile(it) },
                                                      onDeleteFile = { deleteDownloadedFile(it) },
                                                      onRenameFile = { file, newName -> renameDownloadedFile(file, newName) },
                                                      onBack = { screen = Screen.Home }
            )

            Screen.ActiveDownloads -> ActiveDownloadsScreen(
                downloads = downloadQueue,
                onBack = { screen = Screen.Home }
                // Pause / Resume later: pass itemActions = { item -> ... } here.
            )

            Screen.Settings -> SettingsScreen(
                isDark = isDark,
                onDarkChange = onDarkChange,
                onOpenAbout = { screen = Screen.About },
                onOpenLicences = { screen = Screen.Licences },
                onBack = { screen = Screen.Home }
            )

            Screen.About -> AboutScreen(
                onBack = { screen = Screen.Settings }
            )

            Screen.Licences -> LicencesScreen(
                onBack = { screen = Screen.Settings }
            )
        }
    }
}
