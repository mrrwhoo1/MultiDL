package com.mrrwhoo1.multidl

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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Audio files are identified by their MIME type; everything else is treated as video.
    val filteredFiles = when (selectedFilter) {
        "Video" -> files.filter { it.mimeType.startsWith("video/", ignoreCase = true) }
        "Audio" -> files.filter { it.mimeType.startsWith("audio/", ignoreCase = true) }
        else -> files
    }

    // Drop selections for files that have disappeared (for example after deletion).
    LaunchedEffect(files) {
        val existingUris = files.map { it.uri.toString() }.toSet()
        selectedUris = selectedUris.intersect(existingUris)
        if (files.isEmpty()) {
            selectionMode = false
            selectedUris = emptySet()
        }
    }

    fun leaveSelectionMode() {
        selectionMode = false
        selectedUris = emptySet()
    }

    fun toggleSelection(file: DownloadedMedia) {
        val key = file.uri.toString()
        selectedUris = if (key in selectedUris) {
            selectedUris - key
        } else {
            selectedUris + key
        }
    }

    val allVisibleSelected = filteredFiles.isNotEmpty() &&
    filteredFiles.all { it.uri.toString() in selectedUris }

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ScreenHeader(
                    title = "All downloads",
                    subtitle = if (selectionMode) {
                        "${selectedUris.size} selected"
                    } else if (files.size == 1) {
                        "1 file"
                    } else {
                        "${files.size} files"
                    },
                    onBack = onBack
                )
            }

            TextButton(
                onClick = {
                    if (selectionMode) leaveSelectionMode()
                        else selectionMode = true
                }
            ) {
                Text(if (selectionMode) "Cancel" else "Select")
            }
        }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = file.uri.toString() in selectedUris,
                                     onCheckedChange = { toggleSelection(file) }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            FileRow(
                                file = file,
                                containerColor = colors.panel.copy(alpha = 0.93f),
                                    onOpen = { selectedFile ->
                                        if (selectionMode) {
                                            toggleSelection(selectedFile)
                                        } else {
                                            onOpenFile(selectedFile)
                                        }
                                    },
                                    onDelete = onDeleteFile,
                                    onRename = onRenameFile
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selectedUris = if (allVisibleSelected) {
                            selectedUris - filteredFiles.map { it.uri.toString() }.toSet()
                        } else {
                            selectedUris + filteredFiles.map { it.uri.toString() }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (allVisibleSelected) "Deselect all" else "Select all")
                }

                Button(
                    onClick = { showBulkDeleteDialog = true },
                    enabled = selectedUris.isNotEmpty(),
                       colors = ButtonDefaults.buttonColors(
                           containerColor = MaterialTheme.colorScheme.error
                       ),
                       modifier = Modifier.weight(1f)
                ) {
                    Text("Delete (${selectedUris.size})")
                }
            }
        }
    }

    if (showBulkDeleteDialog) {
        val filesToDelete = files.filter { it.uri.toString() in selectedUris }
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete selected files?") },
                    text = {
                        Text(
                            if (filesToDelete.size == 1) {
                                "This file will be permanently deleted."
                            } else {
                                "${filesToDelete.size} files will be permanently deleted."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                filesToDelete.forEach(onDeleteFile)
                                showBulkDeleteDialog = false
                                leaveSelectionMode()
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBulkDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
        )
    }
}
