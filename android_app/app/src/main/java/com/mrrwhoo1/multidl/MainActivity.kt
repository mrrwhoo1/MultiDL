package com.mrrwhoo1.multidl

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.android.AndroidPlatform

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.util.concurrent.Executors
import java.io.File

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore


private val BackgroundColor = Color(0xFF111421)
private val PanelColor = Color(0xFF202438)
private val AccentColor = Color(0xFF7C83FF)
private val MutedText = Color(0xFFB8BDCF)

private data class DownloadedMedia(
    val name: String,
    val uri: android.net.Uri,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BackgroundColor,
                    surface = PanelColor,
                    primary = AccentColor
                )
            ) {
                MultiDLScreen()
            }
        }
    }
}

@Composable
private fun MultiDLScreen() {
    var url by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("HD") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showAllDownloads by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var isDownloading by remember { mutableStateOf(false) }
    val downloadExecutor = remember { Executors.newSingleThreadExecutor() }
    val activityContext = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var downloadedFiles by remember {
        mutableStateOf<List<DownloadedMedia>>(emptyList())
    }

    fun refreshDownloads() {
        val resolver = activityContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val selection =
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"

        val selectionArgs = arrayOf("Download/MultiDL/")

        val sortOrder =
        "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val files = mutableListOf<DownloadedMedia>()

        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns._ID
            )
            val mimeColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.MIME_TYPE
            )
            val sizeColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.SIZE
            )
            val dateColumn = cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.DATE_ADDED
            )

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val id = cursor.getLong(idColumn)

                files.add(
                    DownloadedMedia(
                        name = name,
                        uri = android.content.ContentUris.withAppendedId(
                            collection, id
                        ),
                        mimeType = cursor.getString(mimeColumn)
                        ?: "application/octet-stream",
                        size = cursor.getLong(sizeColumn),
                                    dateAdded = cursor.getLong(dateColumn)
                    )
                )
            }
        }

        downloadedFiles = files
    }
    LaunchedEffect(Unit) {
        refreshDownloads()
    }

    val formats = listOf("HD", "Data Saver", "Audio")

    Box(
        modifier = Modifier
        .fillMaxSize()
        .background(BackgroundColor)
    ) {
        Image(
            painter = painterResource(R.drawable.background),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
        )

        // Dark overlay keeps text readable over the background image.
        Box(
            modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB111421))
        )

        Column(
            modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "MultiDL",
                 color = Color.White,
                 fontSize = 32.sp,
                 fontWeight = FontWeight.Bold
            )

            Text(
                text = "Download media from your favourite platforms",
                 color = MutedText,
                 fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Video URL",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                              placeholder = {
                                  Text(
                                      "https://example.com/video...",
                                       color = MutedText
                                  )
                              },
                              singleLine = true,
                              keyboardOptions = KeyboardOptions(
                                  keyboardType = KeyboardType.Uri
                              ),
                              shape = RoundedCornerShape(14.dp),
                              colors = OutlinedTextFieldDefaults.colors(
                                  focusedTextColor = Color.White,
                                  unfocusedTextColor = Color.White,
                                  focusedBorderColor = AccentColor,
                                  unfocusedBorderColor = Color(0xFF555B72),
                                                                        cursorColor = AccentColor,
                                                                        focusedContainerColor = PanelColor.copy(alpha = 0.95f),
                                                                        unfocusedContainerColor = PanelColor.copy(alpha = 0.95f)
                              )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Download quality",
                color = Color.White,
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
                                   contentColor = Color.White,
                                   containerColor = PanelColor.copy(alpha = 0.95f)
                               )
                ) {
                    Text(
                        selectedFormat,
                         modifier = Modifier.weight(1f)
                    )
                    Text("▾", fontSize = 18.sp)
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                    .background(PanelColor)
                ) {
                    formats.forEach { format ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    format,
                                        color = Color.White
                                )
                            },
                            onClick = {
                                selectedFormat = format
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Supported platforms",
                color = Color.White,
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
                PlatformLogo(R.drawable.linkedin, "LinkedIn")
                PlatformLogo(R.drawable.twitch, "Twitch")
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently downloaded",
                     color = Color.White,
                     fontWeight = FontWeight.Bold,
                     fontSize = 18.sp,
                     modifier = Modifier.weight(1f)
                )

                Text(
                    text = "View all  ›",
                     color = AccentColor,
                     fontSize = 14.sp,
                     modifier = Modifier.clickable {
                         showAllDownloads = true
                     }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PanelColor.copy(alpha = 0.93f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                       horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                            color = PanelColor,
                            shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                               verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (downloadedFiles.isEmpty()) {
                                Text(
                                    text = "No downloads yet",
                                     color = Color.White,
                                     fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = "Your downloaded files will appear here.",
                                     color = MutedText,
                                     fontSize = 13.sp
                                )
                            } else {
                                downloadedFiles.take(3).forEach { file ->
                                    Column(
                                        modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BackgroundColor)
                                        .padding(12.dp)
                                    ) {
                                        Text(
                                            text = file.name,
                                             color = Color.White,
                                             fontWeight = FontWeight.Medium
                                        )

                                        Text(
                                            text = when {
                                                file.size < 1024L ->
                                                "${file.size} B"
                                                file.size < 1024L * 1024L ->
                                                String.format(
                                                    "%.1f KB",
                                                    file.size / 1024.0
                                                )
                                                else ->
                                                    String.format(
                                                        "%.1f MB",
                                                        file.size / (1024.0 * 1024.0)
                                                    )
                                            },
                                             color = MutedText,
                                             fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (message.isNotBlank()) {
                Text(
                    text = message,
                     color = Color(0xFFFFC1C1),
                     fontSize = 13.sp,
                     modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    val submittedUrl = url.trim()

                    when {
                        submittedUrl.isBlank() -> {
                            message = "Enter a video URL first."
                        }

                        !submittedUrl.startsWith("https://") &&
                        !submittedUrl.startsWith("http://") -> {
                            message = "Enter a valid URL starting with http:// or https://."
                        }

                        isDownloading -> Unit

                        else -> {
                            isDownloading = true
                            message = "Starting download..."

                            downloadExecutor.execute {
                                try {
                                    val python = Python.getInstance()
                                    val downloader = python.getModule("android_download_handler")

                                    val downloadDir = File(
                                        activityContext.getExternalFilesDir(null),
                                                           "MultiDL"
                                    ).apply {
                                        mkdirs()
                                    }

                                    val result: PyObject = downloader.callAttr(
                                        "download",
                                        selectedFormat,
                                        submittedUrl,
                                        null,
                                        downloadDir.absolutePath
                                    )

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
                                                else -> "application/octet-stream"
                                            }

                                            val values = ContentValues().apply {
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                                                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                                put(
                                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                                    "Download/MultiDL"
                                                )
                                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                                            }

                                            val resolver = activityContext.contentResolver
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


                                    mainHandler.post {
                                        isDownloading = false

                                        if (savedToPublicDownloads) {
                                            refreshDownloads()
                                            message = "Saved to Downloads/MultiDL: $title"
                                        } else {
                                            message = "Downloaded, but could not save to public Downloads: $title"
                                        }
                                    }



                                } catch (e: Exception) {
                                    mainHandler.post {
                                        isDownloading = false
                                        message = "Error: ${e.message ?: "Could not start Python downloader."}"
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
                   shape = RoundedCornerShape(14.dp),
                   colors = ButtonDefaults.buttonColors(
                       containerColor = AccentColor,
                       contentColor = Color.White
                   )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                                              color = Color.White,
                                              strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Downloading...")
                } else {
                    Text(
                        text = "Download",
                         fontSize = 16.sp,
                         fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAllDownloads) {
        AlertDialog(
            onDismissRequest = { showAllDownloads = false },
            containerColor = PanelColor,
            title = {
                Text("All downloads", color = Color.White)
            },

            text = {
                if (downloadedFiles.isEmpty()) {
                    Text(
                        "No downloads yet. Downloaded files will appear here.",
                         color = MutedText
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                               verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(downloadedFiles) { file ->
                            Column(
                                modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BackgroundColor)
                                .padding(12.dp)
                            ) {
                                Text(
                                    text = file.name,
                                     color = Color.White,
                                     fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = when {
                                        file.size < 1024L ->
                                        "${file.size} B"

                                        file.size < 1024L * 1024L ->
                                        String.format(
                                            "%.1f KB",
                                            file.size / 1024.0
                                        )

                                        else ->
                                            String.format(
                                                "%.1f MB",
                                                file.size / (1024.0 * 1024.0)
                                            )
                                    },
                                     color = MutedText,
                                     fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAllDownloads = false }
                ) {
                    Text("Close", color = AccentColor)
                }
            }
        )
    }
}

@Composable
private fun PlatformLogo(
    imageId: Int,
    description: String
) {
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
             color = MutedText,
             fontSize = 8.sp,
             maxLines = 1
        )
    }
}
