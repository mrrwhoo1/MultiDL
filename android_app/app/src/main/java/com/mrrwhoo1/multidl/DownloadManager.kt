package com.mrrwhoo1.multidl

import java.util.UUID

/**
 * Represents the current state of a download.
 */
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

/**
 * Represents one download in the MultiDL queue.
 */
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val format: String,
    var title: String = "",
    var progress: Int = 0,
    var status: String = "Queued",
    var state: DownloadState = DownloadState.QUEUED,
    var error: String? = null
)

/**
 * Manages the MultiDL download queue.
 *
 * Only one download runs at a time for now. When a download finishes,
 * the next queued download starts automatically.
 */
class MultiDLDownloadManager(
    private val onQueueChanged: (List<DownloadItem>) -> Unit,
    private val onStartDownload: (
        item: DownloadItem,
        onProgress: (Int, String) -> Unit,
        onComplete: (success: Boolean, title: String, error: String?) -> Unit
    ) -> Unit
) {

    private val downloads = mutableListOf<DownloadItem>()

    private var currentDownload: DownloadItem? = null

    /**
     * Adds a new download to the queue.
     */
    @Synchronized
    fun addDownload(url: String, format: String) {
        val item = DownloadItem(
            url = url,
            format = format
        )

        downloads.add(item)
        notifyQueueChanged()

        startNextDownload()
    }

    /**
     * Returns a copy of all downloads.
     */
    @Synchronized
    fun getDownloads(): List<DownloadItem> {
        return downloads.toList()
    }

    /**
     * Returns the currently active download, if there is one.
     */
    @Synchronized
    fun getCurrentDownload(): DownloadItem? {
        return currentDownload
    }

    /**
     * Starts the next queued download if nothing is currently downloading.
     */
    @Synchronized
    private fun startNextDownload() {
        if (currentDownload != null) {
            return
        }

        val nextDownload = downloads.firstOrNull {
            it.state == DownloadState.QUEUED
        } ?: return

        currentDownload = nextDownload
        nextDownload.state = DownloadState.DOWNLOADING
        nextDownload.status = "Preparing download…"
        nextDownload.progress = 0

        notifyQueueChanged()

        onStartDownload(
            nextDownload,

            // Progress callback
            onProgress = { percent, status ->
                updateProgress(nextDownload.id, percent, status)
            },

            // Completion callback
            onComplete = { success, title, error ->
                completeDownload(
                    id = nextDownload.id,
                    success = success,
                    title = title,
                    error = error
                )
            }
        )
    }

    /**
     * Updates the progress of a download.
     */
    @Synchronized
    private fun updateProgress(
        id: String,
        percent: Int,
        status: String
    ) {
        val item = downloads.find { it.id == id } ?: return

        item.progress = percent.coerceIn(0, 100)
        item.status = status

        notifyQueueChanged()
    }

    /**
     * Marks a download as completed or failed.
     * The next queued download will then start automatically.
     */
    @Synchronized
    private fun completeDownload(
        id: String,
        success: Boolean,
        title: String,
        error: String?
    ) {
        val item = downloads.find { it.id == id } ?: return

        if (title.isNotBlank()) {
            item.title = title
        }

        if (success) {
            item.state = DownloadState.COMPLETED
            item.progress = 100
            item.status = "Completed"
            item.error = null
        } else {
            item.state = DownloadState.FAILED
            item.status = "Download failed"
            item.error = error
        }

        currentDownload = null

        notifyQueueChanged()

        // Automatically move to the next item in the queue.
        startNextDownload()
    }

    /**
     * Sends a copy of the current queue to the UI.
     */
    private fun notifyQueueChanged() {
        onQueueChanged(downloads.toList())
    }
}
