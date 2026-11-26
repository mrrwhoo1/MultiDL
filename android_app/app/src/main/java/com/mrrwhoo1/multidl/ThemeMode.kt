package com.mrrwhoo1.multidl

// Which theme the person picked in Settings.
// SYSTEM means "do whatever the phone is set to", so the app switches by itself
// when the phone changes between light and dark (for example at night).

// The key under which the choice is saved, next to the old KEY_DARK_THEME in the same prefs file.
internal const val KEY_THEME_MODE = "theme_mode"

internal enum class ThemeMode(val saved: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** Turns the saved text back into a ThemeMode. Anything unknown means SYSTEM. */
        fun fromSaved(text: String?): ThemeMode =
            values().firstOrNull { it.saved == text } ?: SYSTEM
    }
}
