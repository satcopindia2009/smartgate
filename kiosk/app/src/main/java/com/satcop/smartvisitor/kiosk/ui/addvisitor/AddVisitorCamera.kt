package com.satcop.smartvisitor.kiosk.ui.addvisitor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.satcop.smartvisitor.kiosk.data.face.FaceImage
import com.satcop.smartvisitor.kiosk.qa.QaHooks
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.util.concurrent.Executors

enum class AvCameraTarget { PHOTO, ID }

/**
 * Screens 24 / 25: dark camera with a guide (circle for the face, rounded rectangle for the ID) and a round
 * shutter. Returns a real camera bitmap or nothing. No picture is ever invented.
 */
@Composable
fun AddVisitorCamera(
    target: AvCameraTarget,
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var granted by remember {
        mutableStateOf(
            QaHooks.fakeCamera || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) {
        onDispose {
            val f = ProcessCameraProvider.getInstance(context)
            f.addListener({ runCatching { f.get().unbindAll() } }, mainExecutor)
            cameraExecutor.shutdown()
        }
    }
    val title = if (target == AvCameraTarget.PHOTO) "Visitor photo" else "ID photo"
    val hint = if (target == AvCameraTarget.PHOTO) "Fit the face inside the circle." else "Take a photo of the ID. Fit the whole card inside the frame."

    Column(
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(Color(0xFF0B1110)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Box(
            modifier = Modifier.fillMaxWidth().height(380.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF17211E)),
            contentAlignment = Alignment.Center,
        ) {
            if (!granted || error != null) {
                Text(
                    error ?: if (denied) "Camera access is off. Allow the camera in Settings to take this photo." else "Allow the camera to take this photo.",
                    color = Color.White, fontFamily = KioskFont, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            } else if (QaHooks.fakeCamera) {
                Text(QaHooks.bannerText, color = Color.White, fontFamily = KioskFont, fontSize = 14.sp)
            } else {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val wanted = if (target == AvCameraTarget.PHOTO) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                                    val other = if (target == AvCameraTarget.PHOTO) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                                    val selector = when {
                                        provider.hasCamera(wanted) -> wanted
                                        provider.hasCamera(other) -> other
                                        else -> error("no camera")
                                    }
                                    val preview = Preview.Builder().setTargetResolution(Size(1280, 960)).build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetResolution(Size(1280, 960))
                                        .build()
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                    imageCapture = capture
                                }.onFailure { e ->
                                    error = if (e.message == "no camera") "No camera was found on this phone." else "Could not start the camera. Close other apps using it and try again."
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                val stroke = Stroke(width = 4f, pathEffect = dash)
                if (target == AvCameraTarget.PHOTO) {
                    val r = size.minDimension * 0.36f
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = r, center = center, style = stroke)
                } else {
                    val w = size.width * 0.86f
                    val h = w * 0.63f
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f),
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = GSize(w, h),
                        cornerRadius = CornerRadius(28f, 28f),
                        style = stroke,
                    )
                }
            }
        }
        Text(hint, color = Color.White, fontSize = 14.sp, fontFamily = KioskFont, textAlign = TextAlign.Center)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = !capturing) {
                    QaHooks.frame(title)?.let { onCaptured(it); return@clickable }
                    val cap = imageCapture ?: return@clickable
                    capturing = true
                    cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = runCatching {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                FaceImage.decodeCapture(bytes, image.imageInfo.rotationDegrees)
                            }.getOrNull()
                            image.close()
                            mainExecutor.execute {
                                capturing = false
                                if (bmp != null) onCaptured(bmp) else error = "Could not read the photo. Please try again."
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            mainExecutor.execute { capturing = false; error = "Could not take the photo. Please try again." }
                        }
                    })
                },
        )
        SgSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
