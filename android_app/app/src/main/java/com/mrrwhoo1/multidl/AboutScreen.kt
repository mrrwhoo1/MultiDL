package com.mrrwhoo1.multidl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// About screen.

// ---------- About screen details: change these in one place ----------
private const val DEVELOPER_NAME = "mrrwhoo"
private const val GITHUB_PROFILE_URL = "https://github.com/mrrwhoo1"
private const val GITHUB_REPO_URL = "https://github.com/mrrwhoo1/MultiDL"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed: nothing to open.
    }
}

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "2.0"
        } catch (e: Exception) {
            "2.0"
        }
    }

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(title = "About", onBack = onBack)

        Column(
            modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.panel.copy(alpha = 0.93f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "MultiDL",
                         color = colors.text,
                         fontSize = 28.sp,
                         fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Version $versionName",
                         color = colors.muted,
                         fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(text = "Developer", color = colors.muted, fontSize = 12.sp)

                    Text(
                        text = DEVELOPER_NAME,
                         color = colors.text,
                         fontSize = 18.sp,
                         fontWeight = FontWeight.SemiBold
                    )

                    //                             Spacer(modifier = Modifier.height(20.dp))
                    //
                    //                             Text(text = "Powered by", color = colors.muted, fontSize = 12.sp)
                    //
                    //                             Text(
                    //                                 text = "yt-dlp and FFmpeg",
                    //                                  color = colors.text,
                    //                                  fontSize = 16.sp
                    //                             )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enjoying MultiDL? A star on GitHub is the best way to rate it and support the project.",
                 color = colors.muted,
                 fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { openUrl(context, GITHUB_REPO_URL) },
                   modifier = Modifier
                   .fillMaxWidth()
                   .height(52.dp),
                   shape = RoundedCornerShape(14.dp),
                   colors = ButtonDefaults.buttonColors(
                       containerColor = colors.accent,
                       contentColor = Color.White
                   )
            ) {
                Text("★  Rate on GitHub", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { openUrl(context, GITHUB_PROFILE_URL) },
                           modifier = Modifier
                           .fillMaxWidth()
                           .height(52.dp),
                           shape = RoundedCornerShape(14.dp),
                           colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text)
            ) {
                Text("Developer's GitHub")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
