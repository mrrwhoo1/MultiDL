"""
security focused
"""
package com.mrrwhoo1.multidl

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private const val VERSION_URL =
    "https://raw.githubusercontent.com/mrrwhoo1/MultiDL/main/version.json"

    private const val SIGNATURE_URL =
    "https://raw.githubusercontent.com/mrrwhoo1/MultiDL/main/version.json.sig.b64"

    private const val MAX_RESPONSE_BYTES = 16 * 1024

    private const val PUBLIC_KEY_BASE64 =
    "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA0ryb+9eOp4u0XDXj2FsQH+1+518Vlm5jSSXcNTpY6nW9d8eL3Nqe13zwjn+fjg1c8XcDvwRJFkltWiqf78nUdsOrrTUi2b42NflcgRxgHhqt+ZheTgzpUc4DCVMplZyguPgGqK5Bhh0S1rSl4tzdIdgdkn8KeQakA2Q/RUo3e7a50XbBuCrHtL0nyWe2DmeYxkZR0Ds1XuVMgiwOTppRHpAfnl+BWWm/eig8w6vWQEguzpgKSTwUtRF1OKV11W/VMlNgUoSxWPW4pNrbNeBn1Zoezz8aaHdItrg6lTku46r31x5GQNJsXiWJZ0AHPOIN6Lj2v4pclC6iO9sSXa+D8dEjE7UTqFtElW437XQzx++sLkGcL99I7saSjXEx/VqXmy+cr9SN1iIQ/bDLcgDoUdMCNnfq15OOjE6TprzNbMFbpUdL+UhLq7/BTiS93Vc+w+cskrUSljXhs4ckp+JeWGVCnBa+wgtNuMxwFrUQDB0dDnHCs8So3DkXqDjLnQ/3AgMBAAE="

    fun checkForUpdate(currentVersionCode: Int): UpdateInfo? {
        return try {
            // Fetch the exact bytes first. Do not parse or re-serialize before verification.
            val metadataBytes = fetchBytes(VERSION_URL)
            val signatureText = fetchBytes(SIGNATURE_URL)
            .toString(Charsets.US_ASCII)
            .trim()

            val signatureBytes = Base64.decode(signatureText, Base64.DEFAULT)

            if (!verifySignature(metadataBytes, signatureBytes)) {
                return null
            }

            // Only trust metadata after its signature has been verified.
            val json = JSONObject(metadataBytes.toString(Charsets.UTF_8))

            val latestVersionCode = json.getInt("versionCode")
            val versionName = json.getString("versionName")
            val downloadUrl = json.getString("downloadUrl")
            val releaseNotes = json.optString("releaseNotes", "")

            if (latestVersionCode <= currentVersionCode) {
                return null
            }

            if (!isOfficialReleaseUrl(downloadUrl)) {
                return null
            }

            UpdateInfo(
                versionCode = latestVersionCode,
                versionName = versionName,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes
            )
        } catch (_: Exception) {
            // Failed network requests, malformed metadata, and invalid signatures
            // must not prevent the app from working.
            null
        }
    }

    private fun verifySignature(
        metadata: ByteArray,
        signatureBytes: ByteArray
    ): Boolean {
        val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(keyBytes))

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(metadata)

        return verifier.verify(signatureBytes)
    }

    private fun isOfficialReleaseUrl(value: String): Boolean {
        val uri = URI(value)

        return uri.scheme == "https" &&
        uri.host == "github.com" &&
        uri.rawUserInfo == null &&
        uri.port == -1 &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        uri.path == "/mrrwhoo1/MultiDL/releases/latest"
    }

    private fun fetchBytes(address: String): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Update metadata unavailable")
            }

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                var total = 0

                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break

                        total += count
                        if (total > MAX_RESPONSE_BYTES) {
                            throw IllegalStateException("Update metadata too large")
                        }

                        output.write(buffer, 0, count)
                }
            }

            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
