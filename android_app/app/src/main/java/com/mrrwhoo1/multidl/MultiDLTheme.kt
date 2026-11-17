package com.mrrwhoo1.multidl

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat

// Colours, light/dark palettes, the theme wrapper and the shared background.

// ---------- Colours ----------
// Every colour the UI uses lives here, once for dark and once for light.
// Rule in BOTH themes: file rows are LIGHTER than the panel they sit on.
internal data class AppColors(
    val background: Color,
    val panel: Color,
    val row: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val overlay: Color,
    val border: Color,
    val message: Color
)

internal val DarkPalette = AppColors(
    background = Color(0xFF111421),
                                    panel = Color(0xFF202438),
                                    row = Color(0xFF2B3050),
                                    accent = Color(0xFF7C83FF),
                                    text = Color.White,
                                    muted = Color(0xFFB8BDCF),
                                    overlay = Color(0xBB111421),
                                    border = Color(0xFF555B72),
                                    message = Color(0xFFFFC1C1)
)

internal val LightPalette = AppColors(
    background = Color(0xFFF4F5FA),
                                     panel = Color(0xFFE6E9F5),
                                     row = Color(0xFFFFFFFF),
                                     accent = Color(0xFF5560F0),
                                     text = Color(0xFF1B1E2E),
                                     muted = Color(0xFF5A6075),
                                     overlay = Color(0xCCF4F5FA),
                                     border = Color(0xFF9AA0B8),
                                     message = Color(0xFFB3261E)
)

internal val LocalAppColors = staticCompositionLocalOf { DarkPalette }

@Composable
internal fun MultiDLTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val palette = if (isDark) DarkPalette else LightPalette

    // Dark status-bar icons on the light theme, light icons on the dark theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    val scheme = if (isDark) {
        darkColorScheme(
            background = palette.background,
            surface = palette.panel,
            primary = palette.accent
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.panel,
            primary = palette.accent
        )
    }

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Background image + overlay, shared by every screen. */
@Composable
internal fun AppBackground(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current

    Box(
        modifier = Modifier
        .fillMaxSize()
        .background(colors.background)
    ) {
        Image(
            painter = painterResource(R.drawable.background),
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
        )

        // Overlay keeps text readable over the background image.
        Box(
            modifier = Modifier
            .fillMaxSize()
            .background(colors.overlay)
        )

        content()
    }
}
