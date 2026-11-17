package com.mrrwhoo1.multidl

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Pieces used by more than one screen: header, file row, size formatting, platform logos.

/** Back arrow + title (+ optional subtitle), shared by every inner screen. */
@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.panel.copy(alpha = 0.95f))
            .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                 contentDescription = "Back",
                 tint = colors.text
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = title,
                 color = colors.text,
                 fontSize = 22.sp,
                 fontWeight = FontWeight.Bold
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                     color = colors.muted,
                     fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun FileRow(
    file: DownloadedMedia,
    containerColor: Color,
    onOpen: (DownloadedMedia) -> Unit,
                    onDelete: (DownloadedMedia) -> Unit,
                    onRename: (DownloadedMedia, String) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var menuExpanded by remember(file.uri) { mutableStateOf(false) }
    var showRenameDialog by remember(file.uri) { mutableStateOf(false) }
    var showDeleteDialog by remember(file.uri) { mutableStateOf(false) }
    var editedName by remember(file.uri, file.name) {
        mutableStateOf(file.name.substringBeforeLast('.', file.name))
    }

    val thumbnail by produceState<Bitmap?>(initialValue = null, file.uri) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(file.uri, Size(160, 100), null)
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = containerColor
    ) {
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(file) }
            .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                      contentDescription = "Preview of ${file.name}",
                      modifier = Modifier
                      .size(width = 82.dp, height = 62.dp)
                      .clip(RoundedCornerShape(8.dp)),
                      contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                    .size(width = 82.dp, height = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (file.mimeType.startsWith("video/")) "▶" else "♫",
                         color = colors.accent,
                         fontSize = 24.sp,
                         fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                     color = colors.text,
                     fontWeight = FontWeight.Medium,
                     maxLines = 2,
                     overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = formatSize(file.size),
                     color = colors.muted,
                     fontSize = 12.sp
                )
                Text(
                    text = if (file.mimeType.startsWith("video/")) "Tap to play" else "Tap to open",
                     color = colors.accent,
                     fontSize = 11.sp
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                         contentDescription = "File options",
                         tint = colors.muted
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                                     leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                     onClick = {
                                         menuExpanded = false
                                         editedName = file.name.substringBeforeLast('.', file.name)
                                         showRenameDialog = true
                                     }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                                     leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                     onClick = {
                                         menuExpanded = false
                                         showDeleteDialog = true
                                     }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename file") },
                    text = {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            singleLine = true,
                            label = { Text("File name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onRename(file, editedName)
                            showRenameDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete file?") },
                    text = { Text("${file.name} will be removed from Downloads/MultiDL.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDelete(file)
                            showDeleteDialog = false
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    }
        )
    }
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
internal fun PlatformLogo(
    imageId: Int,
    description: String
) {
    val colors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(42.dp)
    ) {
        Image(
            painter = painterResource(imageId),
              contentDescription = description,
              modifier = Modifier.size(30.dp),
              contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = description,
             color = colors.muted,
             fontSize = 8.sp,
             maxLines = 1
        )
    }
}
