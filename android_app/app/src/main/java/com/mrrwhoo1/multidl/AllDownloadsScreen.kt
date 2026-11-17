package com.mrrwhoo1.multidl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Completed downloads list (files found in Downloads/MultiDL).

@Composable
internal fun AllDownloadsScreen(
    files: List<DownloadedMedia>,
    onOpenFile: (DownloadedMedia) -> Unit,
                               onDeleteFile: (DownloadedMedia) -> Unit,
                               onRenameFile: (DownloadedMedia, String) -> Unit,
                               onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    var selectedFilter by remember { mutableStateOf("All") }

    // Audio files are identified by their MIME type; everything else is treated as video.
    val filteredFiles = when (selectedFilter) {
        "Video" -> files.filter { it.mimeType.startsWith("video/", ignoreCase = true) }
        "Audio" -> files.filter { it.mimeType.startsWith("audio/", ignoreCase = true) }
        else -> files
    }

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(
            title = "All downloads",
            subtitle = if (files.size == 1) "1 file" else "${files.size} files",
                     onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // These filters only change which completed files are displayed.
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

        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        files.isEmpty() -> "No downloads yet.\nDownloaded files will appear here."
                        selectedFilter == "Video" -> "No video downloads yet."
                        selectedFilter == "Audio" -> "No audio downloads yet."
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
                items(filteredFiles, key = { it.uri.toString() }) { file ->
                    // Sits straight on the background, so it uses the panel colour.
                    FileRow(
                        file = file,
                        containerColor = colors.panel.copy(alpha = 0.93f),
                            onOpen = onOpenFile,
                            onDelete = onDeleteFile,
                            onRename = onRenameFile
                    )
                }
            }
        }
    }
}
