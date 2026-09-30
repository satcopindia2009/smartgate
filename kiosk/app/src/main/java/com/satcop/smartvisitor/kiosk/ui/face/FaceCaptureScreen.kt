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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
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
import com.satcop.smartvisitor.kiosk.ui.theme.AppleDark
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
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

/** Board 04 is always dark teal, whatever the app theme is (same palette as the DARK boards). */
private val D get() = AppleDark

@Composable
private fun FacePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier.heightIn(min = 52.dp).clip(PillShape)
            .background(if (enabled) D.primary else D.primary.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = D.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = D.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, maxLines = 2)
    }
}

@Composable
private fun FaceGhost(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier.heightIn(min = 48.dp).clip(PillShape).border(1.dp, D.separator, PillShape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) D.label else D.secondaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
    }
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

    Column(
        Modifier.fillMaxSize().background(D.bg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Board 04: back circle, centred "Face" pill, big title, plain sentence. Dark, teal accents.
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape).background(D.secondaryFill)
                    .clickable(enabled = !busy, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = D.label, modifier = Modifier.size(22.dp))
            }
            Text(
                text = "Face",
                color = D.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier.clip(PillShape).border(1.dp, D.separator, PillShape).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Text(
            text = if (mode == FaceCaptureMode.ENROLL) "Enroll your face" else "Verify your face",
            color = D.label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = KioskFont,
        )
        Text(
            text = if (mode == FaceCaptureMode.ENROLL) "Take a clear photo to enroll your face."
            else "Face verification required. Please verify your face to continue.",
            color = D.secondaryLabel,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(D.secondaryFill)
                .border(1.dp, D.separator, RoundedCornerShape(RadiusLg)),
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
                        color = D.label,
                        fontFamily = KioskFont,
                        fontSize = 14.sp,
                    )
                    if (permanentlyDenied) {
                        FacePill(
                            text = "Open settings",
                            onClick = { openAppSettings(context) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                    } else {
                        FacePill(
                            text = "Allow camera",
                            onClick = { permission.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                        if (asked) {
                            FaceGhost(
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                cameraError != null -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(cameraError!!, color = D.label, fontFamily = KioskFont, fontSize = 14.sp)
                    FaceGhost(
                        text = "Retry camera",
                        onClick = { cameraError = null; cameraKey++ },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
                QaHooks.fakeCamera -> Text(QaHooks.bannerText, color = D.label, fontFamily = KioskFont, fontSize = 14.sp)
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (granted && cameraError == null) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 0.66f
                    val h = size.height * 0.80f
                    drawOval(
                        color = D.primary,
                        topLeft = androidx.compose.ui.geometry.Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())),
                        ),
                    )
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Text(
                message,
                color = if (messageIsError) D.errorText else D.primary,
                fontSize = 14.sp,
                fontWeight = if (messageIsError) FontWeight.Medium else FontWeight.Normal,
                fontFamily = KioskFont,
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(D.brandSoft).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = D.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "Your photo is used only to confirm it is you and is stored securely for attendance.",
                color = D.label, fontSize = 13.sp, fontFamily = KioskFont,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FaceGhost(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            if (previewBmp != null) {
                FaceGhost(
                    text = "Retake",
                    onClick = { previewBmp = null },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
            FacePill(
                icon = Icons.Outlined.CameraAlt,
                text = when {
                    busy -> "Working…"
                    previewBmp == null -> "Capture"
                    messageIsError -> "Retry"
                    mode == FaceCaptureMode.ENROLL -> "Use for enroll"
                    else -> "Verify face"
                },
                onClick = {
                    if (previewBmp == null) {
                        QaHooks.frame("Face")?.let { previewBmp = it; return@FacePill }
                        val cap = imageCapture ?: return@FacePill
                        if (capturing) return@FacePill
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
        Text(text = FaceCaptureCopy.LOCATION_HINT_EN, color = D.secondaryLabel, fontSize = 11.sp, fontFamily = KioskFont)
        Text(text = FaceCaptureCopy.LOCATION_HINT_HI, color = D.secondaryLabel, fontSize = 11.sp, fontFamily = KioskFont)
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
