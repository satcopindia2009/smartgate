package com.satcop.smartvisitor.kiosk.qa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Deterministic colour-bar test picture with a "QA TEST IMAGE" caption (never looks like a real photo). */
object DebugQaTestPattern {
    fun create(label: String, width: Int = 480, height: Int = 640): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bars = intArrayOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.BLUE)
        val p = Paint()
        val bw = width / bars.size.toFloat()
        bars.forEachIndexed { i, col ->
            p.color = col
            c.drawRect(i * bw, 0f, (i + 1) * bw, height * 0.7f, p)
        }
        p.color = Color.DKGRAY
        c.drawRect(0f, height * 0.7f, width.toFloat(), height.toFloat(), p)
        p.color = Color.WHITE
        p.textSize = 34f
        p.isAntiAlias = true
        c.drawText("QA TEST IMAGE", 24f, height * 0.78f, p)
        p.textSize = 26f
        c.drawText(label.take(28), 24f, height * 0.85f, p)
        return bmp
    }
}
