package com.satcop.smartvisitor.kiosk.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft

object PlaceholderBitmap {
    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "VS"
        }
    }

    fun livePhoto(name: String, width: Int = 480, height: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFF8B5CF6.toInt(), 0xFF6366F1.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = width * 0.28f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText(initials(name), width / 2f, height / 2f + paint.textSize / 3f, paint)
        return bmp
    }

    fun idCard(draft: RegistrationDraft, width: Int = 640, height: Int = 400): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF15171C.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = 0xFFA78BFA.toInt()
        paint.textSize = 36f
        canvas.drawText(draft.idType.ifBlank { "ID" }, 32f, 64f, paint)
        paint.color = 0xFFF3F4F6.toInt()
        paint.textSize = 28f
        canvas.drawText(draft.visitorName.ifBlank { "Visitor" }, 32f, 160f, paint)
        paint.color = 0xFF9CA3AF.toInt()
        paint.textSize = 22f
        canvas.drawText(draft.idNumber.ifBlank { "DEMO-ID" }, 32f, 210f, paint)
        paint.textSize = 18f
        canvas.drawText("DEMO · not a real government ID", 32f, height - 36f, paint)
        return bmp
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 82): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
