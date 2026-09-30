package com.satcop.smartvisitor.kiosk.data.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/** Capture -> upload image pipeline: decode with subsampling, rotate, downscale to <=1024px, JPEG q80. */
object FaceImage {
    const val MAX_SIDE = 1024
    const val QUALITY = 80

    /** Pure: size after scaling so the longest side is <= [max] (never upscales). */
    fun targetSize(w: Int, h: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        val longest = maxOf(w, h)
        if (longest <= max) return w to h
        val f = max.toDouble() / longest
        return maxOf(1, Math.round(w * f).toInt()) to maxOf(1, Math.round(h * f).toInt())
    }

    /** Pure: power-of-two inSampleSize that keeps the longest side >= [max]. */
    fun sampleSize(w: Int, h: Int, max: Int = MAX_SIDE): Int {
        var s = 1
        var longest = maxOf(w, h)
        while (longest / 2 >= max) { s *= 2; longest /= 2 }
        return s
    }

    /** JPEG bytes from ImageCapture (already upright-EXIF-less) -> upright Bitmap <=1024px, or null if undecodable. */
    fun decodeCapture(jpeg: ByteArray, rotationDegrees: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return null
        return scaleAndRotate(bmp, rotationDegrees)
    }

    fun scaleAndRotate(bmp: Bitmap, rotationDegrees: Int): Bitmap {
        val (tw, th) = targetSize(bmp.width, bmp.height)
        val m = Matrix()
        if (tw != bmp.width) m.postScale(tw.toFloat() / bmp.width, th.toFloat() / bmp.height)
        if (rotationDegrees != 0) m.postRotate(rotationDegrees.toFloat())
        if (m.isIdentity) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Final upload bytes: <=1024px longest side, JPEG quality 80. */
    fun prepareJpeg(bmp: Bitmap): ByteArray {
        val scaled = scaleAndRotate(bmp, 0)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return out.toByteArray()
    }
}
