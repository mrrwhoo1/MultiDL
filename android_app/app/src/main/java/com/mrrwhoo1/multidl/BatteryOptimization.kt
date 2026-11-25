package com.mrrwhoo1.multidl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * Asks, once, to be allowed to run in the background without battery limits.
 *
 * Android shows its own pop-up ("Let app always run in the background?"). If the person
 * taps Allow, the app is "Unrestricted" and the phone is much less likely to kill
 * downloads when the screen is off.
 *
 * "Once" is remembered with SharedPreferences: a tiny file of key/value pairs that the
 * phone keeps even after the app is closed. Without it we would ask on every download.
 */
object BatteryOptimization {

    private const val PREFS_NAME = "multidl_prefs"
    private const val KEY_ASKED = "asked_unrestricted_battery"

    /** Call right after a download is added. Does nothing if we already asked before. */
    fun askOnce(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_ASKED, false)) return

        // Remember first, so even if the person taps Deny we never ask a second time.
        prefs.edit().putBoolean(KEY_ASKED, true).apply()

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(appContext.packageName)) return

        // A short delay lets the download and its notification start first,
        // so the system pop-up does not interrupt that.
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                appContext.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                // A few phones have no such pop-up: open this app's settings page instead.
                try {
                    appContext.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${appContext.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (ignored: Exception) {
                    // Nothing more we can do.
                }
            }
        }, 1500)
    }
}
