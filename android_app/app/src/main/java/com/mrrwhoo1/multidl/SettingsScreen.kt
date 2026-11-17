package com.mrrwhoo1.multidl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Settings screen.

@Composable
internal fun SettingsScreen(
    isDark: Boolean,
    onDarkChange: (Boolean) -> Unit,
                           onOpenAbout: () -> Unit,
                           onOpenLicences: () -> Unit,
                           onBack: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp)
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.panel.copy(alpha = 0.93f)
        ) {
            Column {
                // Theme switch
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark theme",
                             color = colors.text,
                             fontWeight = FontWeight.SemiBold,
                             fontSize = 16.sp
                        )

                        Text(
                            text = if (isDark) "On (switch off for the light theme)" else "Off (light theme)",
                             color = colors.muted,
                             fontSize = 13.sp
                        )
                    }

                    Switch(
                        checked = isDark,
                        onCheckedChange = onDarkChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent
                        )
                    )
                }

                // Thin divider
                Box(
                    modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(colors.border.copy(alpha = 0.4f))
                )

                // About row
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAbout() }
                    .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About",
                         color = colors.text,
                         fontWeight = FontWeight.SemiBold,
                         fontSize = 16.sp,
                         modifier = Modifier.weight(1f)
                    )

                    Text("›", color = colors.muted, fontSize = 22.sp)
                }

                // Divider between About and Licences.
                Box(
                    modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(colors.border.copy(alpha = 0.4f))
                )

                // Open-source licences row.
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLicences() }
                    .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open-source licences",
                             color = colors.text,
                             fontWeight = FontWeight.SemiBold,
                             fontSize = 16.sp
                        )
                        Text(
                            text = "View MultiDL's licence",
                             color = colors.muted,
                             fontSize = 13.sp
                        )
                    }

                    Text("›", color = colors.muted, fontSize = 22.sp)
                }
            }
        }
    }
}
