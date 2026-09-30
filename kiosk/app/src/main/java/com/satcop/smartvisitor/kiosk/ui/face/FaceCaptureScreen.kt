package com.satcop.smartvisitor.kiosk.ui.face

import com.satcop.smartvisitor.kiosk.qa.QaHooks
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.satcop.smartvisitor.kiosk.data.face.FaceImage
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
import androidx.compose.runtime.key
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

/** User-visible hint lines on the face capture screen. Plain copy only: never an internal resource key. */
object FaceCaptureCopy {
    const val LOCATION_HINT_EN =
        "Face photos on school duty may record time and approximate GPS for campus safety and audit. " +
            "Outside the school campus may block. Denying location is OK — coordinates are never invented."
    const val LOCATION_HINT_HI =
        "स्कूल ड्यूटी पर फेस फोटो के साथ समय और अनुमानित GPS दर्ज हो सकता है। जियो-फेंस के बाहर कार्रवाई ब्लॉक हो सकती है।"
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun FaceCaptureScreen(
    mode: FaceCaptureMode,
    busy: Boolean,
    message: String?,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    messageIsError: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(QaHooks.fakeCamera || hasPermission()) }
    var asked by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
        val act = context.findActivity()
        // Denied AND the system will no longer show the dialog => only Settings can fix it.
        permanentlyDenied = !ok && act != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!granted && !QaHooks.fakeCamera) permission.launch(Manifest.permission.CAMERA)
    }
    // Coming back from Settings: pick up a newly granted permission.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasPermission()
                if (now != granted) granted = now
                if (now) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraKey by remember { mutableStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    // Crashfix 1046: unbind camera on leave; never touch Compose state off main.
    DisposableEffect(Unit) {
        onDispose {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { future.get().unbindAll() }
            }, mainExecutor)
            cameraExecutor.shutdown()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (mode == FaceCaptureMode.ENROLL) "Enroll face" else "Face verification",
            color = KioskColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Look at the front camera, then tap Capture. Staff only.",
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontFamily = KioskFont,
        )
        Text(
            text = FaceCaptureCopy.LOCATION_HINT_EN,
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
        )
        Text(
            text = FaceCaptureCopy.LOCATION_HINT_HI,
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(KioskColors.secondaryFill)
                .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !granted -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (permanentlyDenied) {
                            "Camera access is turned off. Open Settings → Permissions → Camera and allow it, then come back."
                        } else {
                            "Camera permission is needed to take your face photo for sign-in."
                        },
                        color = KioskColors.text,
                        fontFamily = KioskFont,
                        fontSize = 14.sp,
                    )
                    if (permanentlyDenied) {
                        KioskPrimaryButton(
                            text = "Open settings",
                            onClick = { openAppSettings(context) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                    } else {
                        KioskPrimaryButton(
                            text = "Allow camera",
                            onClick = { permission.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                        if (asked) {
                            KioskGhostButton(
                                text = "Open settings",
                                onClick = { openAppSettings(context) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            )
                        }
                    }
                }
                previewBmp != null -> Image(
                    bitmap = previewBmp!!.asImageBitmap(),
                    contentDescription = "Captured face",
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentScale = ContentScale.Crop,
                )
                cameraError != null -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(cameraError!!, color = KioskColors.text, fontFamily = KioskFont, fontSize = 14.sp)
                    KioskGhostButton(
                        text = "Retry camera",
                        onClick = { cameraError = null; cameraKey++ },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
                QaHooks.fakeCamera -> Text(QaHooks.bannerText, color = KioskColors.text, fontFamily = KioskFont, fontSize = 14.sp)
                else -> key(cameraKey) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    runCatching {
                                        val provider = future.get()
                                        // Front camera first; fall back to back, then to whatever exists.
                                        val selector = when {
                                            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                                                CameraSelector.DEFAULT_FRONT_CAMERA
                                            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                                                CameraSelector.DEFAULT_BACK_CAMERA
                                            provider.availableCameraInfos.isNotEmpty() ->
                                                CameraSelector.Builder().addCameraFilter { it.take(1) }.build()
                                            else -> error("no camera")
                                        }
                                        val preview = Preview.Builder()
                                            .setTargetResolution(Size(640, 480))
                                            .build()
                                            .also { it.setSurfaceProvider(pv.surfaceProvider) }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .setTargetResolution(Size(640, 480))
                                            .build()
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                        imageCapture = capture
                                        cameraError = null
                                    }.onFailure { e ->
                                        cameraError = if (e.message == "no camera") {
                                            "No camera was found on this phone."
                                        } else {
                                            "Could not start the camera. Close other apps using it and try again."
                                        }
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }
            }
        }
        if (!message.isNullOrBlank()) {
            Text(
                message,
                color = if (messageIsError) KioskColors.errorText else KioskColors.systemBlue,
                fontSize = 14.sp,
                fontWeight = if (messageIsError) FontWeight.Medium else FontWeight.Normal,
                fontFamily = KioskFont,
            )
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
                    messageIsError -> "Retry"
                    mode == FaceCaptureMode.ENROLL -> "Use for enroll"
                    else -> "Verify face"
                },
                onClick = {
                    if (previewBmp == null) {
                        QaHooks.frame("Face")?.let { previewBmp = it; return@KioskPrimaryButton }
                        val cap = imageCapture ?: return@KioskPrimaryButton
                        if (capturing) return@KioskPrimaryButton
                        capturing = true
                        cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = runCatching { imageProxyToBitmap(image) }.getOrNull()
                                image.close()
                                // Compose state only on main — off-thread set crashes ("keeps stopping").
                                mainExecutor.execute {
                                    capturing = false
                                    if (bmp != null) previewBmp = bmp
                                    else cameraError = "Could not read the photo. Please try again."
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                mainExecutor.execute {
                                    capturing = false
                                    cameraError = "Could not take the photo. Please try again."
                                }
                            }
                        })
                    } else {
                        // <=1024px, JPEG q80: small, fast upload over mobile data.
                        onCaptured(FaceImage.prepareJpeg(previewBmp!!))
                    }
                },
                enabled = !busy && granted && (previewBmp != null || imageCapture != null || QaHooks.fakeCamera),
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
    return FaceImage.decodeCapture(bytes, image.imageInfo.rotationDegrees)
        ?: error("undecodable capture")
}
