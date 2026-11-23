package com.mrrwhoo1.multidl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Running, queued and paused downloads, with Pause / Resume / Cancel buttons on each row.

/**
 * Shows every download that is not finished yet: the one running now, the paused
 * ones and the ones waiting in the queue. It only draws what it is given; the
 * buttons just call [onPause], [onResume] and [onCancel], and MultiDLScreen
 * passes them on to the download manager.
 *
 * Each row decides which buttons to show from item.state:
 *  QUEUED      -> Cancel
 *  DOWNLOADING -> Pause, Cancel
 *  PAUSED      -> Resume, Cancel
 */
@Composable
internal fun ActiveDownloadsScreen(
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
                                   onPause: (DownloadItem) -> Unit = {},
                                   onResume: (DownloadItem) -> Unit = {},
                                   onCancel: (DownloadItem) -> Unit = {}
) {
    val colors = LocalAppColors.current
    var selectedFilter by remember { mutableStateOf("All") }

    // Cancel deletes the partial file, so ask first. Holds the download being confirmed.
    var cancelTarget by remember { mutableStateOf<DownloadItem?>(null) }

    // Running download first, then paused ones, then the queue in the order it was added.
    val unfinished = downloads
    .filter { it.isUnfinished() }
    .sortedBy {
        when (it.state) {
            DownloadState.DOWNLOADING -> 0
            DownloadState.PAUSED -> 1
            else -> 2
        }
    }

    // Audio downloads use the Audio format. HD and Data Saver are video downloads.
    val filteredDownloads = when (selectedFilter) {
        "Video" -> unfinished.filter { !it.format.equals("Audio", ignoreCase = true) }
        "Audio" -> unfinished.filter { it.format.equals("Audio", ignoreCase = true) }
        else -> unfinished
    }

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(
            title = "Downloads",
            subtitle = if (unfinished.size == 1) "1 unfinished download" else "${unfinished.size} unfinished downloads",
                     onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filter the list without changing or removing anything from the queue.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Video", "Audio").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                           colors = FilterChipDefaults.filterChipColors(
                               selectedContainerColor = colors.accent.copy(alpha = 0.22f),
                                                                        selectedLabelColor = colors.text,
                                                                        labelColor = colors.muted
                           )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        unfinished.isEmpty() -> "No active downloads.\nRunning, paused and queued downloads appear here."
                        selectedFilter == "Video" -> "No active video downloads."
                        selectedFilter == "Audio" -> "No active audio downloads."
                        else -> "No downloads to show."
                    },
                     color = colors.muted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                       verticalArrangement = Arrangement.spacedBy(10.dp),
                       contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    ActiveDownloadRow(
                        item = item,
                        containerColor = colors.panel.copy(alpha = 0.93f),
                                      onPause = { onPause(item) },
                                      onResume = { onResume(item) },
                                      onCancel = { cancelTarget = item }
                    )
                }
            }
        }
    }

    // "Are you sure?" pop-up for Cancel.
    cancelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel download?") },
                    text = {
                        Text("This removes the download and deletes anything already downloaded for it.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onCancel(target)
                                cancelTarget = null
                            }
                        ) {
                            Text("Cancel download")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { cancelTarget = null }) {
                            Text("Keep")
                        }
                    }
        )
    }
}

/** One row of the Downloads screen, with the buttons that fit the download's current state. */
@Composable
internal fun ActiveDownloadRow(
    item: DownloadItem,
    containerColor: Color,
    onPause: () -> Unit = {},
                               onResume: () -> Unit = {},
                               onCancel: () -> Unit = {}
) {
    val colors = LocalAppColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = containerColor
    ) {
        Column(
            modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
               verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // The real title is only known once a download finishes, so show the URL until then.
            Text(
                text = item.title.ifBlank { item.url },
                 color = colors.text,
                 fontWeight = FontWeight.Medium,
                 maxLines = 2,
                 overflow = TextOverflow.Ellipsis
            )

            if (item.state == DownloadState.QUEUED) {
                Text(
                    text = "Waiting in queue · ${item.format}",
                     color = colors.muted,
                     fontSize = 12.sp
                )
            } else {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                                        color = colors.accent,
                                        trackColor = colors.background.copy(alpha = 0.45f)
                )
                Text(
                    text = "${item.status} · ${item.progress}% · ${item.format}",
                     color = colors.muted,
                     fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pause (running) or Resume (paused). Queued items have neither.
                when (item.state) {
                    DownloadState.DOWNLOADING -> TextButton(
                        onClick = onPause,
                        enabled = !item.stopping
                    ) {
                        Text("Pause", color = if (item.stopping) colors.muted else colors.accent)
                    }

                    DownloadState.PAUSED -> TextButton(onClick = onResume) {
                        Text("Resume", color = colors.accent)
                    }

                    else -> {}
                }

                TextButton(
                    onClick = onCancel,
                    enabled = !item.stopping
                ) {
                    Text("Cancel", color = colors.muted)
                }
            }
        }
    }
}
