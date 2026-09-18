package com.satcop.smartvisitor.kiosk.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Device-local demo template store until POST /auth/face/enroll|verify returns 200 on valley.
 * Staff only (Gate|Host|Guard) — never visitor. AC-FL1/FL2/FL3.
 */
class LocalFaceTemplateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(context.applicationContext.filesDir, "face_templates").also { it.mkdirs() }

    fun isEnrolled(username: String): Boolean {
        val key = normalize(username)
        if (key.isBlank()) return hasAny()
        return File(dir, "$key.jpg").isFile && prefs.contains(metaKey(key))
    }

    fun hasAny(): Boolean = dir.listFiles()?.any { it.extension == "jpg" } == true

    fun enrolledUsername(): String? =
        prefs.getString(KEY_LAST_USER, null)?.takeIf { isEnrolled(it) }
            ?: dir.listFiles()?.firstOrNull { it.extension == "jpg" }?.nameWithoutExtension

    fun consentVersion(username: String): String? =
        prefs.getString(metaKey(normalize(username)) + ".ver", null)

    fun consentAt(username: String): String? =
        prefs.getString(metaKey(normalize(username)) + ".at", null)

    fun enroll(
        username: String,
        jpegBytes: ByteArray,
        consentVersion: String,
        consentAt: String,
    ) {
        val key = normalize(username).ifBlank { "staff" }
        File(dir, "$key.jpg").writeBytes(jpegBytes)
        prefs.edit()
            .putString(metaKey(key) + ".ver", consentVersion)
            .putString(metaKey(key) + ".at", consentAt)
            .putString(metaKey(key) + ".hash", averageHash(jpegBytes))
            .putString(KEY_LAST_USER, key)
            .apply()
    }

    /** Soft local match for desk demo (no ML Kit). Hamming on 8×8 avg-hash. */
    fun localVerify(username: String?, jpegBytes: ByteArray): LocalVerifyResult {
        val key = normalize(username ?: "").ifBlank { enrolledUsername() ?: return LocalVerifyResult(false, null, "No local template") }
        val file = File(dir, "$key.jpg")
        if (!file.isFile) return LocalVerifyResult(false, null, "No template for $key — use password (AC-FL3)")
        val enrolledHash = prefs.getString(metaKey(key) + ".hash", null)
            ?: averageHash(file.readBytes())
        val probeHash = averageHash(jpegBytes)
        val distance = hamming(enrolledHash, probeHash)
        // Desk demo: lenient threshold; clear miss still falls back to password.
        val matched = distance <= DEMO_HAMMING_MAX
        return LocalVerifyResult(
            matched = matched,
            username = key,
            message = if (matched) {
                "Local demo match (d=$distance) · Backend verify pending"
            } else {
                "Face mismatch (d=$distance) — use password (AC-FL3)"
            },
        )
    }

    fun clear(username: String? = null) {
        if (username.isNullOrBlank()) {
            dir.listFiles()?.forEach { it.delete() }
            prefs.edit().clear().apply()
        } else {
            val key = normalize(username)
            File(dir, "$key.jpg").delete()
            prefs.edit()
                .remove(metaKey(key) + ".ver")
                .remove(metaKey(key) + ".at")
                .remove(metaKey(key) + ".hash")
                .apply()
        }
    }

    companion object {
        private const val PREFS = "staff_face_templates"
        private const val KEY_LAST_USER = "last_user"
        /** Lenient for lighting/angle on desk demo; not production biometrics. */
        private const val DEMO_HAMMING_MAX = 18

        fun normalize(username: String): String =
            username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")

        fun metaKey(username: String) = "u.$username"

        fun jpegToBase64(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return out.toByteArray()
        }

        fun decodeJpeg(bytes: ByteArray): Bitmap? =
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

        fun averageHash(jpeg: ByteArray): String {
            val bmp = decodeJpeg(jpeg) ?: return "0".repeat(64)
            val scaled = Bitmap.createScaledBitmap(bmp, 8, 8, true)
            val grays = IntArray(64)
            var sum = 0L
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val p = scaled.getPixel(x, y)
                    val g = ((p shr 16 and 0xff) + (p shr 8 and 0xff) + (p and 0xff)) / 3
                    grays[y * 8 + x] = g
                    sum += g
                }
            }
            val avg = (sum / 64).toInt()
            return buildString(64) {
                grays.forEach { append(if (it >= avg) '1' else '0') }
            }
        }

        fun hamming(a: String, b: String): Int {
            val n = minOf(a.length, b.length)
            var d = abs(a.length - b.length)
            for (i in 0 until n) if (a[i] != b[i]) d++
            return d
        }
    }
}

data class LocalVerifyResult(
    val matched: Boolean,
    val username: String?,
    val message: String,
)
