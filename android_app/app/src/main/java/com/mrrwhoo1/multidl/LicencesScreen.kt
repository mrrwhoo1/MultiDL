package com.mrrwhoo1.multidl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Open-source licences screen.

@Composable
internal fun LicencesScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    val gplText = remember {
        context.resources.openRawResource(R.raw.gpl_3_0)
        .bufferedReader()
        .use { it.readText() }
    }

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(
            title = "Open-source licences",
            subtitle = "MultiDL and third-party software",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
               verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.panel.copy(alpha = 0.93f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MultiDL",
                         color = colors.text,
                         fontSize = 20.sp,
                         fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "GNU General Public License v3.0 (GPL-3.0)",
                         color = colors.accent,
                         fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Copyright © 2026 Maron H. Chilomo",
                         color = colors.muted,
                         fontSize = 13.sp
                    )
                    Text(
                        text = "MultiDL is distributed under the GNU GPL version 3. " +
                        "You may copy, modify, and redistribute it under the terms " +
                        "of that licence. The complete licence text follows.",
                         color = colors.text,
                         fontSize = 14.sp
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.panel.copy(alpha = 0.93f)
            ) {
                Text(
                    text = gplText,
                     modifier = Modifier.padding(16.dp),
                     color = colors.text,
                     fontSize = 12.sp
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.panel.copy(alpha = 0.93f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Third-party components",
                         color = colors.text,
                         fontSize = 17.sp,
                         fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MultiDL includes third-party software which retains " +
                        "its own copyright notices and licence terms. Refer to the " +
                        "project's NOTICE file and the respective component licence " +
                        "texts for details.",
                         color = colors.muted,
                         fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
