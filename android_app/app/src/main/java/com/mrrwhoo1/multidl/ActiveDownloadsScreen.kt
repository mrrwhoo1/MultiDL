package com.mrrwhoo1.multidl

import androidx.compose.foundation.background
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

// Running and queued downloads. Pause / Resume buttons will plug into the itemActions slot.

/**
 * Shows every download that is not finished yet: the one running now and the
 * ones waiting in the queue. It only draws what it is given.
 *
 * Pause / Resume later: MultiDLScreen can pass [itemActions] to draw buttons on each
 * row, and the row decides what to show from item.state (QUEUED, DOWNLOADING, PAUSED).
 * Nothing else on this screen needs to change.
 */
@Composable
internal fun ActiveDownloadsScreen(
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
                                  itemActions: @Composable (DownloadItem) -> Unit = {}
) {
    val colors = LocalAppColors.current
    var selectedFilter by remember { mutableStateOf("All") }

    // Running download first, then the queue in the order it was added.
    val unfinished = downloads
    .filter { it.isUnfinished() }
    .sortedBy { if (it.state == DownloadState.QUEUED) 1 else 0 }

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
                        unfinished.isEmpty() -> "No active downloads.\nRunning and queued downloads appear here."
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
                                      actions = { itemActions(item) }
                    )
                }
            }
        }
    }
}

/** One row of the Downloads screen. [actions] is an empty slot for the future Pause / Resume buttons. */
@Composable
internal fun ActiveDownloadRow(
    item: DownloadItem,
    containerColor: Color,
    actions: @Composable () -> Unit = {}
) {
    val colors = LocalAppColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = containerColor
    ) {
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
            }

            actions()
        }
    }
}
