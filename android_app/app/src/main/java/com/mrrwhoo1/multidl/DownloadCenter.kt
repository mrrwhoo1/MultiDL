package com.mrrwhoo1.multidl

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import java.util.concurrent.Executors

/**
 * The one place where the download queue lives.
 *
 * Before, the queue and the code that runs downloads lived inside MultiDLScreen. When
 * Android rebuilt that screen (leaving the app for a while is enough), a brand new empty
 * queue was created, even though the old downloads were still running underneath.
 *
 * An `object` exists once for the whole life of the app's process, no matter how many
 * times the screen is rebuilt. The screen now only READS the queue from here, so it always
 * shows what is really happening.
 */
object DownloadCenter {

    private var started = false
    private lateinit var appContext: Context

    private val mainHandler = Handler(Looper.getMainLooper())

    // One download at a time: this thread runs the Python downloader.
    private val executor = Executors.newSingleThreadExecutor()

    // ---- What the screen reads. Compose redraws whenever these change. ----

    /** Every download in the queue (waiting, running, paused, finished, failed). */
    var queue by mutableStateOf<List<DownloadItem>>(emptyList())
        private set

    /** The latest "Completed" / "Download failed: …" text for the message line. */
    var lastMessage by mutableStateOf("")
        private set

    /** Goes up by one every time lastMessage changes, so the screen can notice. */
    var messageVersion by mutableIntStateOf(0)
        private set

    /** Goes up by one every time a file is saved, so the screen reloads its file list. */
    var filesVersion by mutableIntStateOf(0)
        private set

    /** Add, pause, resume and cancel downloads through this. */
    lateinit var manager: MultiDLDownloadManager
        private set

    /** Call once at app start. Calling it again does nothing. */
    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext

        manager = MultiDLDownloadManager(
            onQueueChanged = { updatedQueue -> handleQueueChanged(updatedQueue) },
            onStartDownload = { item, onProgress, onComplete, onStopped ->
                runDownload(item, onProgress, onComplete, onStopped)
            },
            onStopRequest = { id, reason -> sendStopRequest(id, reason) },
            onDiscardPartial = { id -> discardPartialFiles(id) }
        )
    }

    private fun postMessage(text: String) {
        mainHandler.post {
            lastMessage = text
            messageVersion++
        }
    }

    private fun handleQueueChanged(updatedQueue: List<DownloadItem>) {
        mainHandler.post {
            queue = updatedQueue

            val activeDownload = updatedQueue.firstOrNull {
                it.state == DownloadState.DOWNLOADING
            }

            // Keep Android from freezing the app while there is work to do.
            val hasWork = updatedQueue.any {
                it.state == DownloadState.QUEUED || it.state == DownloadState.DOWNLOADING
            }

            if (hasWork) {
                val waiting = updatedQueue.count { it.state == DownloadState.QUEUED }
                val text = when {
                    activeDownload == null -> "Starting next download…"
                    waiting > 0 -> "Downloading ${activeDownload.progress}% · $waiting waiting"
                    else -> "Downloading ${activeDownload.progress}%"
                }
                DownloadService.show(appContext, text, activeDownload?.progress ?: -1)
            } else {
                DownloadService.stop(appContext)
            }
        }
    }

    /** Runs one download on the executor: Python downloads it, then it is saved to Downloads/MultiDL. */
    private fun runDownload(
        item: DownloadItem,
        onProgress: (Int, String) -> Unit,
        onComplete: (success: Boolean, title: String, error: String?) -> Unit,
        onStopped: (reason: String) -> Unit
    ) {
        executor.execute {
            try {
                val python = Python.getInstance()
                val downloader = python.getModule("android_download_handler")

                val downloadDir = File(
                    appContext.getExternalFilesDir(null),
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
                    downloadDir.absolutePath,
                    item.id
                )

                // Python reports "pause" or "cancel" here when the user stopped it.
                val stopped = result.callAttr("get", "stopped", "")?.toString() ?: ""
                if (stopped.isNotEmpty()) {
                    onStopped(stopped)
                    return@execute
                }

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

                        val resolver = appContext.contentResolver
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
                    mainHandler.post { filesVersion++ }
                    postMessage("Completed")
                    onComplete(true, title, null)
                } else {
                    // When Python fails it puts the real reason in "title".
                    val error = if (!success && title.isNotBlank()) {
                        title
                    } else {
                        "Couldn't save the downloaded file to Downloads/MultiDL."
                    }
                    postMessage("Download failed: $error")
                    onComplete(false, title, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Could not start Python downloader."
                postMessage("Download failed: $error")
                onComplete(false, "", error)
            }
        }
    }

    // The executor is busy downloading, so stop requests are sent to Python
    // from their own short-lived thread.
    private fun sendStopRequest(id: String, reason: String) {
        Thread {
            try {
                Python.getInstance()
                    .getModule("android_download_handler")
                    .callAttr("request_stop", id, reason)
            } catch (e: Exception) {
                Log.w("MultiDL", "Could not send stop request: ${e.message}")
            }
        }.start()
    }

    private fun discardPartialFiles(id: String) {
        Thread {
            try {
                Python.getInstance()
                    .getModule("android_download_handler")
                    .callAttr("discard_partial", id)
            } catch (e: Exception) {
                Log.w("MultiDL", "Could not discard partial files: ${e.message}")
            }
        }.start()
    }
}
