package com.satcop.smartvisitor.kiosk.ui.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import java.nio.ByteBuffer
import java.util.concurrent.Executors

enum class FaceCaptureMode { ENROLL, VERIFY }

@Composable
fun FaceCaptureScreen(
    mode: FaceCaptureMode,
    busy: Boolean,
    message: String?,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    // Crashfix 1046: unbind camera on leave; never touch Compose state off main.
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            cameraExecutor.shutdown()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (mode == FaceCaptureMode.ENROLL) "Enroll face · CameraX" else "Face unlock · CameraX",
            color = KioskColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Staff only (Gate · Host · Guard) · visitor face OUT · front camera",
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontFamily = KioskFont,
        )
        Text(
            text = "EN: Face photos on school duty may record time and approximate GPS for campus safety and audit. Outside geo-fence may block. Denying location is OK — coordinates are never invented. (guard_capture_location_hint_en_hi_v1)",
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
        )
        Text(
            text = "HI: स्कूल ड्यूटी पर फेस फोटो के साथ समय और अनुमानित GPS दर्ज हो सकता है। जियो-फेंस के बाहर कार्रवाई ब्लॉक हो सकती है।",
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(KioskColors.sidebar)
                .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !granted -> Text("Camera permission required", color = KioskColors.orange, fontFamily = KioskFont)
                previewBmp != null -> Image(
                    bitmap = previewBmp!!.asImageBitmap(),
                    contentDescription = "Captured face",
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentScale = ContentScale.Crop,
                )
                cameraError != null -> Text(
                    cameraError!!,
                    color = KioskColors.orange,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(12.dp),
                )
                else -> AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val preview = Preview.Builder()
                                        .setTargetResolution(Size(640, 480))
                                        .build()
                                        .also { it.setSurfaceProvider(pv.surfaceProvider) }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetResolution(Size(640, 480))
                                        .build()
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        capture,
                                    )
                                    imageCapture = capture
                                    cameraError = null
                                }.onFailure { e ->
                                    cameraError = "Camera failed: ${e.message ?: e.javaClass.simpleName}"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            }
        }
        if (!message.isNullOrBlank()) {
            Text(message, color = KioskColors.cyanBright, fontSize = 12.sp, fontFamily = KioskFont)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KioskGhostButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            if (previewBmp != null) {
                KioskGhostButton(
                    text = "Retake",
                    onClick = { previewBmp = null },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
            KioskPrimaryButton(
                text = when {
                    busy -> "Working…"
                    previewBmp == null -> "Capture"
                    mode == FaceCaptureMode.ENROLL -> "Use for enroll"
                    else -> "Verify face"
                },
                onClick = {
                    if (previewBmp == null) {
                        val cap = imageCapture ?: return@KioskPrimaryButton
                        cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = imageProxyToBitmap(image)
                                image.close()
                                // Compose state only on main — off-thread set crashes ("keeps stopping").
                                mainExecutor.execute { previewBmp = bmp }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                mainExecutor.execute {
                                    cameraError = "Capture failed: ${exception.message ?: "error"}"
                                }
                            }
                        })
                    } else {
                        val out = java.io.ByteArrayOutputStream()
                        previewBmp!!.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        onCaptured(out.toByteArray())
                    }
                },
                enabled = !busy && granted,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bmp
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
