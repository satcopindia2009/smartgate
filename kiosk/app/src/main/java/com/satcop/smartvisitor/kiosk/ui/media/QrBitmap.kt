package com.satcop.smartvisitor.kiosk.ui.media

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Draws a real QR code for the server-issued pass token. Never invents a code. */
object QrBitmap {
    fun encode(payload: String, size: Int): Bitmap? = try {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }
}
