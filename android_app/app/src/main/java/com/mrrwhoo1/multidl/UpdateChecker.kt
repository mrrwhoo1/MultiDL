
package com.mrrwhoo1.multidl

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private const val VERSION_URL =
        "https://raw.githubusercontent.com/mrrwhoo1/MultiDL/main/version.json"

    fun checkForUpdate(currentVersionCode: Int): UpdateInfo? {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(VERSION_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(response)

            val latestVersionCode = json.getInt("versionCode")
            val versionName = json.getString("versionName")
            val downloadUrl = json.getString("downloadUrl")
            val releaseNotes = json.optString("releaseNotes", "")

            if (latestVersionCode <= currentVersionCode) {
                return null
            }

            if (!downloadUrl.startsWith("https://github.com/")) {
                return null
            }

            UpdateInfo(
                versionCode = latestVersionCode,
                versionName = versionName,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes
            )
        } catch (_: Exception) {
            // An update check failure must not stop MultiDL from working.
            null
        } finally {
            connection?.disconnect()
        }
    }
}
