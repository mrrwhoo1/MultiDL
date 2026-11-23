package com.mrrwhoo1.multidl

import java.util.UUID

/**
 * Represents the current state of a download.
 */
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    // Stopped by the user. The partial file is kept so Resume can continue it.
    PAUSED
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
                        var error: String? = null,
                        // True between pressing Pause/Cancel and Python actually stopping.
                        // The screen uses it to disable the buttons so they can't be pressed twice.
                        var stopping: Boolean = false
)

/**
 * True while a download still needs work: waiting in the queue, running, or paused.
 * The Downloads badge and the Downloads screen both use this, so they always agree.
 */
fun DownloadItem.isUnfinished(): Boolean =
state == DownloadState.QUEUED ||
state == DownloadState.DOWNLOADING ||
state == DownloadState.PAUSED

/**
 * Manages the MultiDL download queue.
 *
 * Only one download runs at a time for now. When a download finishes,
 * is paused or is cancelled, the next queued download starts automatically.
 *
 * Pause / Resume / Cancel work like this:
 *  - Pause: ask Python to stop. Python keeps the partial file. State becomes PAUSED.
 *  - Resume: put the item back in the queue. When its turn comes it is started again
 *    and yt-dlp continues from the partial file.
 *  - Cancel: ask Python to stop and delete the partial file, then remove the item.
 */
class MultiDLDownloadManager(
    private val onQueueChanged: (List<DownloadItem>) -> Unit,
        private val onStartDownload: (
            item: DownloadItem,
            onProgress: (Int, String) -> Unit,
                                      onComplete: (success: Boolean, title: String, error: String?) -> Unit,
                                      onStopped: (reason: String) -> Unit
        ) -> Unit,
        // Tells the Python downloader to stop a RUNNING download. reason = "pause" or "cancel".
        private val onStopRequest: (id: String, reason: String) -> Unit,
            // Deletes the partial files of a download that is paused (so nothing is running).
            private val onDiscardPartial: (id: String) -> Unit
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
         * Pauses a download.
         * A queued download is simply marked paused. A running one is asked to stop.
         */
        @Synchronized
        fun pause(id: String) {
            val item = downloads.find { it.id == id } ?: return

            when (item.state) {
                DownloadState.QUEUED -> {
                    item.state = DownloadState.PAUSED
                    item.status = "Paused"
                    notifyQueueChanged()
                }

                DownloadState.DOWNLOADING -> {
                    if (item.stopping) return
                        item.stopping = true
                        item.status = "Pausing…"
                        notifyQueueChanged()
                        onStopRequest(id, "pause")
                }

                else -> {
                    // Completed, failed or already paused: nothing to do.
                }
            }
        }

        /**
         * Puts a paused download back in the queue.
         */
        @Synchronized
        fun resume(id: String) {
            val item = downloads.find { it.id == id } ?: return

            if (item.state != DownloadState.PAUSED) return

                item.state = DownloadState.QUEUED
                item.status = "Queued"
                notifyQueueChanged()

                startNextDownload()
        }

        /**
         * Cancels a download and removes it from the queue.
         */
        @Synchronized
        fun cancel(id: String) {
            val item = downloads.find { it.id == id } ?: return

            when (item.state) {
                DownloadState.QUEUED -> {
                    downloads.remove(item)
                    notifyQueueChanged()
                }

                DownloadState.PAUSED -> {
                    // Nothing is running, but its partial file is still on disk.
                    downloads.remove(item)
                    notifyQueueChanged()
                    onDiscardPartial(id)
                }

                DownloadState.DOWNLOADING -> {
                    if (item.stopping) return
                        item.stopping = true
                        item.status = "Cancelling…"
                        notifyQueueChanged()
                        onStopRequest(id, "cancel")
                }

                else -> {
                    // Completed or failed: nothing to cancel.
                }
            }
        }

        /**
         * Returns a copy of all downloads.
         */
        @Synchronized
        fun getDownloads(): List<DownloadItem> {
            return downloads.map { it.copy() }
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
            nextDownload.stopping = false
            // Progress is not reset here, so a resumed download keeps its bar position.

            notifyQueueChanged()

            onStartDownload(
                nextDownload,

                { percent, status ->
                    updateProgress(nextDownload.id, percent, status)
                },

                { success, title, error ->
                    completeDownload(
                        id = nextDownload.id,
                        success = success,
                        title = title,
                        error = error
                    )
                },

                { reason ->
                    stopDownload(nextDownload.id, reason)
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

            // While a stop is in progress, keep showing "Pausing…" / "Cancelling…".
            if (!item.stopping) {
                item.status = status
            }

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

            item.stopping = false

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
         * Called when Python confirms that a running download was stopped.
         * "pause" keeps the item (as PAUSED), "cancel" removes it.
         * Either way the next queued download can start.
         */
        @Synchronized
        private fun stopDownload(id: String, reason: String) {
            val item = downloads.find { it.id == id }

            if (item != null) {
                item.stopping = false

                if (reason == "cancel") {
                    downloads.remove(item)
                } else {
                    item.state = DownloadState.PAUSED
                    item.status = "Paused"
                }
            }

            if (currentDownload?.id == id) {
                currentDownload = null
            }

            notifyQueueChanged()

            startNextDownload()
        }

        /**
         * Sends a snapshot of the current queue to the UI.
         * The items are copied so Compose sees a real change whenever one of them changes.
         */
        private fun notifyQueueChanged() {
            onQueueChanged(downloads.map { it.copy() })
        }
}
