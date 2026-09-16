package com.satcop.smartvisitor.kiosk.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

@Composable
fun SignaturePad(
    strokes: List<List<Offset>>,
    onStrokes: (List<List<Offset>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(KioskColors.bg, ControlShape)
            .border(1.dp, KioskColors.border, ControlShape)
            .onSizeChanged { size = it },
    ) {
        if (strokes.isEmpty() && current.isEmpty()) {
            Text(
                text = "Sign here with finger / stylus",
                color = KioskColors.textDim,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> current = listOf(offset) },
                        onDragEnd = {
                            if (current.isNotEmpty()) {
                                onStrokes(strokes + listOf(current))
                            }
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + change.position
                        },
                    )
                },
        ) {
            val stroke = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            (strokes + listOf(current).filter { it.isNotEmpty() }).forEach { pts ->
                if (pts.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, Color(0xFFA78BFA), style = stroke)
            }
        }
    }
}

fun strokesToBitmap(strokes: List<List<Offset>>, width: Int, height: Int): Bitmap? {
    if (strokes.isEmpty() || width <= 0 || height <= 0) return null
    val bmp = Bitmap.createBitmap(width.coerceAtLeast(200), height.coerceAtLeast(80), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(0xFF0F1115.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA78BFA.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        if (pts.size < 2) return@forEach
        val path = AndroidPath()
        path.moveTo(pts.first().x, pts.first().y)
        pts.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    return bmp
}
