package com.satcop.smartvisitor.kiosk.ui.guardhome

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.satcop.smartvisitor.kiosk.data.face.LocalFaceTemplateStore
import com.satcop.smartvisitor.kiosk.data.geo.GpsPolicy
import com.satcop.smartvisitor.kiosk.data.geo.GpsState
import com.satcop.smartvisitor.kiosk.data.geo.LiveGpsTracker
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellNav
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellSub
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellTitle
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private val clockFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · hh:mm:ss a", Locale.ENGLISH)

/**
 * One screen for check-in / clock-out: live camera, a GPS status line and the device date/time.
 * There is no separate photo screen or location screen. The photo and the location reading used
 * for a request are captured together and sent in one request; a retry always uses a fresh reading.
 */
@Composable
fun AttendancePanel(
    mode: AttendanceMode,
    state: GuardHomeState,
    controller: GuardHomeController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker = remember { LiveGpsTracker(context) }

    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var locationGranted by remember { mutableStateOf(tracker.hasPermission()) }
    var permissionAsked by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationGranted = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA)
        if (!locationGranted) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        permissionAsked = true
    }

    controller.hasLocationPermission = { locationGranted }

    // Location feed only while the panel is open.
    DisposableEffect(locationGranted) {
        if (locationGranted) tracker.start()
        onDispose { tracker.stop() }
    }
    val fix by tracker.fix.collectAsState()

    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var clockText by remember { mutableStateOf(ZonedDateTime.now(GuardHomeLogic.IST).format(clockFmt)) }
    LaunchedEffect(Unit) {
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            clockText = ZonedDateTime.now(GuardHomeLogic.IST).format(clockFmt) + " IST"
            delay(1000)
        }
    }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoAt by remember { mutableStateOf<Long?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    DisposableEffect(Unit) {
        onDispose {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ runCatching { future.get().unbindAll() } }, mainExecutor)
            cameraExecutor.shutdown()
        }
    }

    val gps = GpsPolicy.evaluate(fix, nowElapsed)
    val gpsLine = when {
        !locationGranted -> "Location permission is off."
        else -> GpsPolicy.statusLine(gps, nowElapsed)
    }
    val gpsOk = locationGranted && gps is GpsState.Ready
    val title = if (mode == AttendanceMode.CHECK_IN) "Check in" else "Clock out"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AppleShellNav(leading = "Cancel", trailing = " ", onLeading = { if (!state.panelBusy) controller.closePanel() })
        AppleShellTitle(title)
        AppleShellSub(
            if (mode == AttendanceMode.CHECK_IN) "Take your photo. Your location is read at the same time."
            else "Confirm you are on campus. A photo is optional.",
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(KioskColors.card)
                .border(1.dp, KioskColors.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !cameraGranted -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "Camera permission is off. Please allow camera access to take your photo.",
                        color = KioskColors.orange, fontFamily = KioskFont, fontSize = 14.sp,
                    )
                    Spacer(Modifier.padding(4.dp))
                    KioskGhostButton(
                        text = "Allow camera",
                        onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                photo != null -> Image(
                    bitmap = photo!!.asImageBitmap(),
                    contentDescription = "Your photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                cameraError != null -> Text(
                    cameraError!!, color = KioskColors.orange, fontFamily = KioskFont, fontSize = 14.sp,
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
                                    val preview = Preview.Builder().setTargetResolution(Size(640, 480)).build()
                                        .also { it.setSurfaceProvider(pv.surfaceProvider) }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetResolution(Size(640, 480))
                                        .build()
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                                    imageCapture = capture
                                    cameraError = null
                                }.onFailure {
                                    cameraError = "The camera could not start. Please close other camera apps and try again."
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.padding(4.dp))
        StatusLine(label = "Location", text = gpsLine, ok = gpsOk)
        StatusLine(label = "Device time", text = clockText, ok = true)
        if (!locationGranted && permissionAsked) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                KioskGhostButton(
                    text = "Allow location",
                    onClick = {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val msg = state.panelMessage
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                color = if (state.panelError) KioskColors.red else KioskColors.textMuted,
                fontSize = 14.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KioskGhostButton(
                text = if (photo == null) "Take photo" else "Retake",
                enabled = cameraGranted && !state.panelBusy && (photo != null || imageCapture != null),
                onClick = {
                    if (photo != null) {
                        photo = null
                        photoAt = null
                        controller.onPhotoRetaken()
                    } else {
                        val cap = imageCapture ?: return@KioskGhostButton
                        cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = attendanceProxyToBitmap(image)
                                image.close()
                                mainExecutor.execute {
                                    photo = bmp
                                    photoAt = SystemClock.elapsedRealtime()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                mainExecutor.execute {
                                    cameraError = "The photo could not be taken. Please try again."
                                }
                            }
                        })
                    }
                },
                modifier = Modifier.weight(1f),
            )
            KioskPrimaryButton(
                text = if (state.panelBusy) "Please wait…" else title,
                enabled = !state.panelBusy && (photo != null || mode == AttendanceMode.CLOCK_OUT),
                onClick = {
                    val b64 = photo?.let { bmp ->
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        LocalFaceTemplateStore.jpegToBase64(out.toByteArray())
                    }
                    controller.submit(photoBase64 = b64, photoAtElapsedMs = photoAt, fix = fix)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.padding(8.dp))
    }
}

@Composable
private fun StatusLine(label: String, text: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (ok) "●" else "○",
            color = if (ok) KioskColors.green else KioskColors.orange,
            fontSize = 14.sp,
        )
        Column {
            Text(label, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
            Text(text, color = KioskColors.text, fontSize = 14.sp, fontFamily = KioskFont, fontWeight = FontWeight.Medium)
        }
    }
}

private fun attendanceProxyToBitmap(image: ImageProxy): Bitmap {
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
