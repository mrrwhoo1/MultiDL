package com.mrrwhoo1.multidl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One video inside a playlist. */
data class PlaylistVideo(
    val url: String,
    val title: String
)

/** A playlist that has been read and is waiting for the person to choose videos. */
data class PlaylistPreview(
    val title: String,
    val videos: List<PlaylistVideo>,
    // True when the playlist had more videos than the app reads at once.
    val truncated: Boolean,
    // The format (HD, Data Saver, Audio…) that was selected when the link was pasted.
    val format: String
)

/**
 * Pop-up that lists every video in a playlist with a tick box beside each one.
 * Everything starts ticked, so pressing the Download button gets the whole playlist.
 * [onDownload] receives only the videos that are still ticked.
 */
@Composable
fun PlaylistPickerDialog(
    preview: PlaylistPreview,
    onDownload: (List<PlaylistVideo>) -> Unit,
    onCancel: () -> Unit
) {
    val videos = preview.videos

    // One true/false per video, in the same order as the list. All true at first.
    val selected = remember(preview) {
        mutableStateListOf<Boolean>().apply {
            repeat(videos.size) { add(true) }
        }
    }

    val selectedCount = selected.count { it }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = preview.title.ifBlank { "Playlist" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = "$selectedCount of ${videos.size} videos selected",
                    fontSize = 14.sp
                )

                if (preview.truncated) {
                    Text(
                        text = "This playlist is longer, so only the first ${videos.size} videos are shown.",
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { for (i in selected.indices) selected[i] = true }
                    ) {
                        Text("Select all")
                    }
                    TextButton(
                        onClick = { for (i in selected.indices) selected[i] = false }
                    ) {
                        Text("Select none")
                    }
                }

                // The list scrolls by itself. heightIn stops it from filling the screen.
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(videos) { index, video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected[index] = !selected[index] },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected[index],
                                onCheckedChange = { selected[index] = it }
                            )
                            Text(
                                text = video.title.ifBlank { video.url },
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDownload(videos.filterIndexed { index, _ -> selected[index] })
                },
                // Nothing ticked means nothing to download.
                enabled = selectedCount > 0
            ) {
                Text(
                    if (selectedCount == videos.size) "Download all"
                    else "Download ($selectedCount)"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
