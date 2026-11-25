package com.mrrwhoo1.multidl

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import androidx.activity.result.IntentSenderRequest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import org.json.JSONObject
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

    // Set when a pasted link has list= in it and the person still has to choose between
    // "just this video" and "whole playlist". Holds that link while the pop-up is open.
    var playlistChoice by remember { mutableStateOf<String?>(null) }

    // The queue lives in DownloadCenter, not in this screen, so it survives this screen
    // being rebuilt. Everything below is just read from it.
    val downloadQueue = DownloadCenter.queue
    val activeDownload = downloadQueue.firstOrNull { it.state == DownloadState.DOWNLOADING }
    val isDownloading = activeDownload != null
    val downloadProgress = activeDownload?.progress ?: 100
    val downloadStatus = activeDownload?.status ?: "Preparing download…"
    var downloadedFiles by remember {
        mutableStateOf<List<DownloadedMedia>>(emptyList())
    }

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

    // Files saved by an earlier install of the app no longer belong to this install, so
    // Android will not let the app delete them silently. It shows its own pop-up asking the
    // person to allow it, and this launcher receives their answer.
    val deleteConfirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshDownloads()
            message = ""
        }
    }

    // Shows Android's "Allow MultiDL to delete this file?" pop-up.
    //  Android 11 and newer: MediaStore.createDeleteRequest builds it.
    //  Android 10: the SecurityException itself carries it (RecoverableSecurityException).
    fun askAndroidToDelete(file: DownloadedMedia, error: SecurityException?) {
        try {
            val intentSender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createDeleteRequest(
                    activityContext.contentResolver,
                    listOf(file.uri)
                ).intentSender

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                error is RecoverableSecurityException ->
                error.userAction.actionIntent.intentSender

                else -> null
            }

            if (intentSender != null) {
                deleteConfirmLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                message = "Could not delete file: ${error?.message ?: "permission denied"}"
            }
        } catch (e: Exception) {
            message = "Could not delete file: ${e.message ?: "unknown error"}"
        }
    }

    fun deleteDownloadedFile(file: DownloadedMedia) {
        try {
            val rowsDeleted = activityContext.contentResolver.delete(file.uri, null, null)

            if (rowsDeleted > 0) {
                refreshDownloads()
                message = ""
            } else {
                // Some phones delete nothing and raise no error for files this install
                // does not own. Treat that the same as being refused.
                askAndroidToDelete(file, null)
            }
        } catch (e: SecurityException) {
            askAndroidToDelete(file, e)
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

    // The download manager, and the code that actually runs downloads, live in
    // DownloadCenter (set up in MainActivity).
    val downloadManager = DownloadCenter.manager

    // DownloadCenter can finish a download while this screen is not on screen.
    // When the screen is on, it reacts to what the center reports.
    val seenMessageVersion = remember { DownloadCenter.messageVersion }
    LaunchedEffect(DownloadCenter.messageVersion) {
        if (DownloadCenter.messageVersion != seenMessageVersion) {
            message = DownloadCenter.lastMessage
        }
    }

    val seenFilesVersion = remember { DownloadCenter.filesVersion }
    LaunchedEffect(DownloadCenter.filesVersion) {
        if (DownloadCenter.filesVersion != seenFilesVersion) {
            refreshDownloads()
        }
    }

    // Opens the person's email app with a message to the developer ready to write.
    // Their email address is only shared if they choose to send the message.
    fun sendFeedbackEmail() {
        val feedbackEmail = "mrrwhoo3@gmail.com"

        val versionName = try {
            activityContext.packageManager
            .getPackageInfo(activityContext.packageName, 0)
            .versionName
        } catch (e: Exception) {
            "unknown"
        }

        // Blank space at the top to type in, details underneath that help with bug reports.
        val body = "\n\n\n---\n" +
        "App: MultiDL $versionName\n" +
        "Android: ${Build.VERSION.RELEASE}\n" +
        "Phone: ${Build.MANUFACTURER} ${Build.MODEL}\n"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(feedbackEmail))
            putExtra(Intent.EXTRA_SUBJECT, "MultiDL feedback")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            activityContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No email app on this phone: copy the address so they can send it from anywhere.
            val clipboard = activityContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Feedback email", feedbackEmail))
            Toast.makeText(
                activityContext,
                "No email app found. Address copied: $feedbackEmail",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // True for a YouTube link that has a list= part, e.g. watch?v=abc&list=xyz
    fun hasPlaylistParameter(link: String): Boolean {
        val uri = Uri.parse(link)
        val host = uri.host?.lowercase() ?: return false

        val isYouTube = host == "youtu.be" ||
        host == "youtube.com" ||
        host.endsWith(".youtube.com")

        return isYouTube && !uri.getQueryParameter("list").isNullOrBlank()
    }

    // youtube.com/playlist?list=xyz is only a playlist, there is no single video to pick.
    fun isPurePlaylistLink(link: String): Boolean {
        return Uri.parse(link).path?.startsWith("/playlist") == true
    }

    // Reads the playlist in Python (off the main thread, it needs the internet), then
    // adds every video to the queue. The queue downloads them one at a time.
    fun queuePlaylist(link: String, format: String) {
        message = "Reading playlist…"

        Thread {
            try {
                val raw = Python.getInstance()
                .getModule("android_download_handler")
                .callAttr("list_playlist", link)
                .toString()

                val result = JSONObject(raw)

                if (!result.optBoolean("success")) {
                    val error = result.optString("error", "Unknown error")
                    mainHandler.post { message = "Couldn't read the playlist: $error" }
                    return@Thread
                }

                val entries = result.getJSONArray("entries")
                val videos = (0 until entries.length()).map { i ->
                    val entry = entries.getJSONObject(i)
                    Pair(entry.getString("url"), entry.optString("title", ""))
                }
                val truncated = result.optBoolean("truncated")

                mainHandler.post {
                    if (videos.isEmpty()) {
                        message = "No downloadable videos found in that playlist."
                    } else {
                        downloadManager.addDownloads(videos, format)
                        BatteryOptimization.askOnce(activityContext)
                        url = ""
                        message = if (truncated) {
                            "Added the first ${videos.size} videos of the playlist."
                        } else {
                            "Added ${videos.size} videos to the queue."
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    message = "Couldn't read the playlist: ${e.message}"
                }
            }
        }.start()
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

            // A link with list= in it: it is a playlist, or a video inside one.
            hasPlaylistParameter(submittedUrl) -> {
                message = ""

                if (isPurePlaylistLink(submittedUrl)) {
                    queuePlaylist(submittedUrl, selectedFormat)
                } else {
                    playlistChoice = submittedUrl   // ask: just this video, or all of it?
                }
            }

            else -> {
                message = ""
                downloadManager.addDownload(submittedUrl, selectedFormat)
                BatteryOptimization.askOnce(activityContext)
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
    // Asked only for links that have list= in them (see startDownload).
    playlistChoice?.let { link ->
        AlertDialog(
            onDismissRequest = { playlistChoice = null },
            title = { Text("Download the playlist?") },
                    text = {
                        Text("This link is part of a playlist. Download just this video, or every video in the playlist?")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                playlistChoice = null
                                queuePlaylist(link, selectedFormat)
                            }
                        ) {
                            Text("Whole playlist")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                playlistChoice = null
                                downloadManager.addDownload(link, selectedFormat)
                                BatteryOptimization.askOnce(activityContext)
                                url = ""
                            }
                        ) {
                            Text("Just this video")
                        }
                    }
        )
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
                onBack = { screen = Screen.Home },
                onPause = { downloadManager.pause(it.id) },
                                                            onResume = { downloadManager.resume(it.id) },
                                                            onCancel = { downloadManager.cancel(it.id) }
            )

            Screen.Settings -> SettingsScreen(
                isDark = isDark,
                onDarkChange = onDarkChange,
                onOpenAbout = { screen = Screen.About },
                onOpenLicences = { screen = Screen.Licences },
                onSendFeedback = { sendFeedbackEmail() },
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
