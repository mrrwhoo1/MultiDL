package com.mrrwhoo1.multidl

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Home screen: URL box, quality picker, recent downloads and the Downloads / Settings icons.

@Composable
internal fun HomeScreen(
    url: String,
    onUrlChange: (String) -> Unit,
                       selectedFormat: String,
                       onFormatSelected: (String) -> Unit,
                       files: List<DownloadedMedia>,
                       isDownloading: Boolean,
                       queuedDownloads: Int,
                       downloadProgress: Int,
                       downloadStatus: String,
                       message: String,
                       onOpenFile: (DownloadedMedia) -> Unit,
                       onDeleteFile: (DownloadedMedia) -> Unit,
                       onRenameFile: (DownloadedMedia, String) -> Unit,
                       onViewAll: () -> Unit,
                       unfinishedDownloads: Int,
                       onOpenDownloads: () -> Unit,
                       onOpenSettings: () -> Unit,
                       onDownload: () -> Unit
) {
    val colors = LocalAppColors.current
    var dropdownExpanded by remember { mutableStateOf(false) }
    val formats = listOf("HD", "Data Saver", "Audio")

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 20.dp)
    ) {
        // Pinned header: title on the left, settings gear on the right.
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MultiDL",
                     color = colors.text,
                     fontSize = 32.sp,
                     fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Download media from your favourite platforms",
                     color = colors.muted,
                     fontSize = 14.sp
                )
            }

            // Downloads icon with a badge counting downloads that are still running or queued.
            IconButton(onClick = onOpenDownloads) {
                BadgedBox(
                    badge = {
                        if (unfinishedDownloads > 0) {
                            Badge(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ) {
                                Text(
                                    if (unfinishedDownloads > 99) "99+" else unfinishedDownloads.toString()
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                         contentDescription = "Downloads",
                         tint = colors.text
                    )
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                     contentDescription = "Settings",
                     tint = colors.text
                )
            }
        }

        // Everything except the header and the button lives in this scrolling area.
        Column(
            modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Video URL",
                 color = colors.text,
                 fontWeight = FontWeight.SemiBold,
                 fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                              placeholder = {
                                  Text("https://example.com/video...", color = colors.muted)
                              },
                              singleLine = true,
                              trailingIcon = {
                                  if (url.isNotEmpty()) {
                                      IconButton(onClick = { onUrlChange("") }) {
                                          Icon(
                                              imageVector = Icons.Filled.Clear,
                                               contentDescription = "Clear URL",
                                               tint = colors.muted
                                          )
                                      }
                                  }
                              },
                              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                              shape = RoundedCornerShape(14.dp),
                              colors = OutlinedTextFieldDefaults.colors(
                                  focusedTextColor = colors.text,
                                  unfocusedTextColor = colors.text,
                                  focusedBorderColor = colors.accent,
                                  unfocusedBorderColor = colors.border,
                                  cursorColor = colors.accent,
                                  focusedContainerColor = colors.panel.copy(alpha = 0.95f),
                                                                        unfocusedContainerColor = colors.panel.copy(alpha = 0.95f)
                              )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Download quality",
                 color = colors.text,
                 fontWeight = FontWeight.SemiBold,
                 fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                               shape = RoundedCornerShape(12.dp),
                               colors = ButtonDefaults.outlinedButtonColors(
                                   contentColor = colors.text,
                                   containerColor = colors.panel.copy(alpha = 0.95f)
                               )
                ) {
                    Text(selectedFormat, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                         contentDescription = "Select download quality"
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(colors.panel)
                ) {
                    formats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format, color = colors.text) },
                                         onClick = {
                                             onFormatSelected(format)
                                             dropdownExpanded = false
                                         }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Supported platforms",
                 color = colors.text,
                 fontWeight = FontWeight.SemiBold,
                 fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformLogo(R.drawable.yt, "YouTube")
                PlatformLogo(R.drawable.instagram, "Instagram")
                PlatformLogo(R.drawable.tiktok, "TikTok")
                PlatformLogo(R.drawable.facebook, "Facebook")
                PlatformLogo(R.drawable.x, "X")
                //                         PlatformLogo(R.drawable.linkedin, "LinkedIn")
                PlatformLogo(R.drawable.twitch, "Twitch")
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently downloaded",
                     color = colors.text,
                     fontWeight = FontWeight.Bold,
                     fontSize = 18.sp,
                     modifier = Modifier.weight(1f)
                )

                Text(
                    text = "View all  ›",
                     color = colors.accent,
                     fontSize = 14.sp,
                     modifier = Modifier
                     .clip(RoundedCornerShape(8.dp))
                     .clickable { onViewAll() }
                     .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // One single panel. The rows inside are lighter than the panel.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.panel.copy(alpha = 0.93f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                       verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (files.isEmpty()) {
                        Text(
                            text = "No downloads yet",
                             color = colors.text,
                             fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Your downloaded files will appear here.",
                             color = colors.muted,
                             fontSize = 13.sp
                        )
                    } else {
                        files.take(3).forEach { file ->
                            FileRow(
                                file = file,
                                containerColor = colors.row,
                                onOpen = onOpenFile,
                                onDelete = onDeleteFile,
                                onRename = onRenameFile
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show download activity in the same area where status messages appear.
        if (isDownloading) {
            Column(
                modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
                   verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                                        color = colors.accent,
                                        trackColor = colors.panel
                )
                Text(
                    text = "$downloadStatus · $downloadProgress%",
                     color = colors.muted,
                     fontSize = 13.sp
                )
                if (queuedDownloads > 0) {
                    Text(
                        text = "$queuedDownloads waiting in queue",
                         color = colors.muted,
                         fontSize = 12.sp
                    )
                }
            }
        } else if (message.isNotBlank()) {
            Text(
                text = message,
                 color = if (message == "Completed") Color(0xFF66BB6A) else colors.message,
                 fontSize = 13.sp,
                 modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }

        Button(
            onClick = onDownload,
            enabled = true,
            modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
               shape = RoundedCornerShape(14.dp),
               colors = ButtonDefaults.buttonColors(
                   containerColor = colors.accent,
                   contentColor = Color.White
               )
        ) {
            Text(
                text = if (isDownloading) "Add to queue" else "Download",
                 fontSize = 16.sp,
                 fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
