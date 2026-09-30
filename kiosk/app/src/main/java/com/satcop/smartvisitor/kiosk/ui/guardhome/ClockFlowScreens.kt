package com.satcop.smartvisitor.kiosk.ui.guardhome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.satcop.smartvisitor.kiosk.data.face.LocalFaceTemplateStore
import com.satcop.smartvisitor.kiosk.data.geo.LiveGpsTracker
import com.satcop.smartvisitor.kiosk.qa.QaHooks
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.LocalTime
import java.util.concurrent.Executors

private val LockGreen get() = KioskColors.green
private val DarkGreen = Color(0xFF1E7B3A)

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Guard-only start-of-day gate. Nothing inside [content] (visitor screens, patrol, courier...) is composed until the
 * SERVER says the guard is clocked in today, so there is no back/deep-link/notification path around it.
 */
@Composable
fun GuardClockInGate(
    controller: GuardHomeController,
    displayName: String,
    schoolName: String,
    onLogout: () -> Unit,
    content: @Composable (requestLogout: () -> Unit) -> Unit,
) {
    val state by controller.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var lastSelfie by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) controller.refreshAttendance() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val lock = ClockInLogic.lockState(state.attendance, state.attendanceLoaded)
    val result = state.result
    val panel = state.panel
    BackHandler(enabled = panel != null || result != null) {
        if (result != null) controller.clearResult() else controller.back()
    }

    when {
        result != null -> ClockResultScreen(
            result = result, displayName = displayName, schoolName = schoolName, selfie = lastSelfie,
            onDone = { controller.clearResult(); lastSelfie = null; controller.refresh() },
        )
        panel != null && state.step == ClockStep.INFO -> ClockInfoScreen(
            mode = panel, today = state.attendance, schoolName = schoolName,
            gateLabel = state.attendance?.attendance?.let { ClockInLogic.gateLabel(it) } ?: "Campus",
            onBack = controller::closePanel, onProceed = controller::proceedToSelfie,
        )
        panel != null -> SelfieScreen(
            mode = panel, state = state, controller = controller, onSelfie = { lastSelfie = it },
        )
        lock == LockState.UNLOCKED -> {
            content {
                // R2: signing out while still on duty asks first.
                confirmSignOut = true
            }
            if (confirmSignOut) {
                AlertDialog(
                    onDismissRequest = { confirmSignOut = false },
                    title = { Text("Sign out?", fontFamily = KioskFont) },
                    text = { Text(ClockInLogic.SIGN_OUT_ON_DUTY, fontFamily = KioskFont) },
                    confirmButton = { TextButton(onClick = { confirmSignOut = false; onLogout() }) { Text("Sign out") } },
                    dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay") } },
                )
            }
        }
        lock == LockState.SHIFT_COMPLETE -> ShiftCompleteScreen(displayName, schoolName, onLogout)
        lock == LockState.UNKNOWN -> LockScreen(
            displayName = displayName, schoolName = schoolName, loading = true, error = null,
            onClockIn = {}, onSignOut = onLogout,
        )
        else -> LockScreen(
            displayName = displayName, schoolName = schoolName, loading = false, error = state.attendanceError,
            onClockIn = { controller.openPanel(AttendanceMode.CHECK_IN) }, onSignOut = onLogout,
        )
    }
}

@Composable
fun LockScreen(
    displayName: String,
    schoolName: String,
    loading: Boolean,
    error: String?,
    onClockIn: () -> Unit,
    onSignOut: () -> Unit,
    now: LocalTime = LocalTime.now(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LockGreen)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FormTokens.ScreenHPad, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Text("🛡", fontSize = 36.sp) }
        Spacer(Modifier.height(20.dp))
        Text(
            ClockInLogic.greeting(displayName, now), color = Color.White, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center,
        )
        Text(
            listOf("Security Guard", schoolName).filter { it.isNotBlank() }.joinToString(" · "),
            color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "🔒  " + ClockInLogic.LOCK_CHIP, color = Color.White, fontSize = 14.sp, fontFamily = KioskFont,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            ClockInLogic.LOCK_TEXT, color = Color.White.copy(alpha = 0.95f), fontSize = 14.sp, fontFamily = KioskFont,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
        if (!error.isNullOrBlank()) {
            Text(error, color = Color.White, fontSize = 13.sp, fontFamily = KioskFont, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = if (loading) "…" else ClockInLogic.LOCK_BUTTON,
            color = DarkGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth().heightIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color.White)
                .let { if (loading) it else it.clickableNoRipple(onClockIn) }
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics { contentDescription = ClockInLogic.LOCK_BUTTON },
        )
        Text(
            ClockInLogic.SIGN_OUT, color = Color.White, fontSize = 16.sp, fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickableNoRipple(onSignOut).padding(vertical = 14.dp),
        )
    }
}

@Composable
fun ShiftCompleteScreen(displayName: String, schoolName: String, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(LockGreen).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = FormTokens.ScreenHPad, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✓", color = Color.White, fontSize = 48.sp)
        Text(
            ClockInLogic.SHIFT_COMPLETE, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            fontFamily = KioskFont, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            listOf(ClockInLogic.firstName(displayName), schoolName).filter { it.isNotBlank() }.joinToString(" · "),
            color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            ClockInLogic.SIGN_OUT, color = DarkGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .clickableNoRipple(onSignOut).padding(16.dp),
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun FlowHeader(title: String, schoolName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DarkGreen).statusBarsPadding()
            .padding(horizontal = FormTokens.ScreenHPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "←", color = Color.White, fontSize = 24.sp,
            modifier = Modifier.heightIn(min = 48.dp).clickableNoRipple(onBack).padding(end = 4.dp)
                .semantics { contentDescription = "Back" },
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
            Text(
                listOf("Security Guard", schoolName).filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = KioskFont,
            )
        }
    }
}

@Composable
private fun Card(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KioskColors.card)
            .border(1.dp, KioskColors.border, RoundedCornerShape(14.dp)).padding(FormTokens.ScreenHPad),
        verticalArrangement = Arrangement.spacedBy(FormTokens.LabelToField),
    ) {
        Text(label, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        content()
    }
}

/** Self Check In / Self Check Out info screen (ref-2). */
@Composable
fun ClockInfoScreen(
    mode: AttendanceMode,
    today: com.satcop.smartvisitor.kiosk.data.model.TodayAttendance?,
    schoolName: String,
    gateLabel: String,
    onBack: () -> Unit,
    onProceed: () -> Unit,
) {
    val card = ClockInLogic.statusCard(today)
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        FlowHeader(ClockInLogic.headerTitle(mode), schoolName, onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = FormTokens.ScreenHPad, vertical = FormTokens.FieldToField),
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Card("Current Status") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("● " + card.title, color = KioskColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
                        Text(card.detail, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
                    }
                    val on = card.chip == "On duty"
                    Text(
                        card.chip, color = if (on) Color.White else KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) KioskColors.green else KioskColors.secondaryFill)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Card("Guard Details") {
                Text("Security Guard", color = KioskColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
                Text("📍 " + listOf(gateLabel, schoolName).filter { it.isNotBlank() }.joinToString(" · "), color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            }
            Text(
                "Your front camera will open to take a selfie for " + (if (mode == AttendanceMode.CHECK_IN) "check-in" else "check-out") +
                    " verification. Make sure your face is clearly visible and well-lit.",
                color = KioskColors.text, fontSize = 13.sp, fontFamily = KioskFont,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(KioskColors.greenDim).padding(14.dp),
            )
            Card("How it works") {
                listOf(
                    "📷" to "Camera opens — position your face in the oval",
                    "☁" to "Selfie is securely uploaded to the server",
                    "✓" to "Attendance is recorded with time & location",
                ).forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(KioskColors.greenDim), contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
                        Text(text, color = KioskColors.text, fontSize = 14.sp, fontFamily = KioskFont, modifier = Modifier.weight(1f))
                    }
                }
            }
            card.note?.let { Text(it, color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont) }
        }
        Text(
            ClockInLogic.proceedLabel(mode).uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(FormTokens.ScreenHPad).heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp)).background(if (card.canProceed) DarkGreen else KioskColors.border)
                .let { if (card.canProceed) it.clickableNoRipple(onProceed) else it }
                .padding(16.dp),
        )
    }
}

/** Front-camera selfie (ref-3). One tap = one attempt: photo + location + time are sent together. */
@Composable
fun SelfieScreen(
    mode: AttendanceMode,
    state: GuardHomeState,
    controller: GuardHomeController,
    onSelfie: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fake = QaHooks.fakeCamera
    val tracker = remember { LiveGpsTracker(context) }
    var cameraGranted by remember {
        mutableStateOf(fake || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var locationGranted by remember { mutableStateOf(tracker.hasPermission()) }
    var asked by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        if (!it) controller.reportCameraDenied()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        locationGranted = r.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA)
        if (!locationGranted) locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        asked = true
    }
    controller.hasLocationPermission = { locationGranted }
    DisposableEffect(locationGranted) {
        if (locationGranted) tracker.start()
        onDispose { tracker.stop() }
    }
    val fix by tracker.fix.collectAsState()

    // Retry ALWAYS recaptures: the held selfie is dropped whenever an attempt fails (attemptNo changes).
    var selfie by remember(state.attemptNo) { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    DisposableEffect(Unit) {
        onDispose {
            if (!fake) {
                val f = ProcessCameraProvider.getInstance(context)
                f.addListener({ runCatching { f.get().unbindAll() } }, mainExecutor)
            }
            cameraExecutor.shutdown()
        }
    }

    fun submitWith(bmp: Bitmap) {
        selfie = bmp
        onSelfie(bmp)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        controller.submit(
            photoBase64 = LocalFaceTemplateStore.jpegToBase64(out.toByteArray()),
            photoAtElapsedMs = SystemClock.elapsedRealtime(),
            fix = fix,
        )
    }

    val showLocationNotice = asked && !locationGranted && mode == AttendanceMode.CHECK_IN
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        FlowHeader(ClockInLogic.headerTitle(mode), "", onBack = { if (!state.panelBusy) controller.closePanel() })
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(FormTokens.ScreenHPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Text(ClockInLogic.cameraLabel(mode), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !cameraGranted -> Text(
                        ClockInLogic.CAMERA_OFF, color = Color.White, fontSize = 14.sp, fontFamily = KioskFont,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
                    )
                    selfie != null -> Image(
                        bitmap = selfie!!.asImageBitmap(), contentDescription = "Your selfie",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                    cameraError != null -> Text(cameraError!!, color = Color.White, fontSize = 14.sp, fontFamily = KioskFont, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    fake -> Text("TEST CAMERA (QA build)", color = Color.White, fontSize = 14.sp, fontFamily = KioskFont)
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
                                            .setTargetResolution(Size(640, 480)).build()
                                        provider.unbindAll()
                                        // Front camera only: no back-camera or gallery fallback.
                                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                                        imageCapture = capture
                                        cameraError = null
                                    }.onFailure {
                                        cameraError = "The front camera could not start. Please close other camera apps and try again."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (cameraGranted && selfie == null) FaceGuide()
            }
            Text(ClockInLogic.SELFIE_HINT, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontFamily = KioskFont, textAlign = TextAlign.Center)
            if (showLocationNotice) {
                Text(ClockInLogic.LOCATION_OFF_SOFT, color = Color(0xFFFFD60A), fontSize = 13.sp, fontFamily = KioskFont, textAlign = TextAlign.Center)
            }
            val msg = state.panelMessage
            if (!msg.isNullOrBlank()) {
                Text(
                    msg, color = if (state.panelError) Color(0xFFFF6B6B) else Color.White, fontSize = 14.sp, fontFamily = KioskFont,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = msg },
                )
            }
            if (state.settingsHint != null || !cameraGranted || (mode == AttendanceMode.CLOCK_OUT && !locationGranted && asked)) {
                Text(
                    "Open Settings", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                        .clickableNoRipple { openAppSettings(context) }.padding(12.dp),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(FormTokens.ScreenHPad),
            verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
        ) {
            val canCapture = cameraGranted && !state.panelBusy && (fake || imageCapture != null)
            Text(
                text = when {
                    state.panelBusy -> "Please wait…"
                    state.panelError && state.attemptNo > 0 -> "Retry"
                    else -> "Capture Selfie"
                },
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (canCapture) DarkGreen else Color(0xFF444444))
                    .let {
                        if (!canCapture) it else it.clickableNoRipple {
                            selfie = null
                            if (fake) {
                                submitWith(QaHooks.frame(ClockInLogic.actionLabel(mode))!!)
                            } else {
                                val cap = imageCapture ?: return@clickableNoRipple
                                cap.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = proxyToBitmap(image)
                                        image.close()
                                        mainExecutor.execute { submitWith(bmp) }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        mainExecutor.execute { cameraError = "The photo could not be taken. Please try again." }
                                    }
                                })
                            }
                        }
                    }
                    .padding(16.dp),
            )
            Text(
                "Back", color = Color.White, fontSize = 16.sp, fontFamily = KioskFont, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .clickableNoRipple { if (!state.panelBusy) controller.closePanel() }.padding(14.dp),
            )
        }
    }
}

@Composable
private fun FaceGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.62f
        val h = size.height * 0.62f
        val tl = Offset((size.width - w) / 2, (size.height - h) / 2 - size.height * 0.04f)
        drawOval(Color.White.copy(alpha = 0.9f), tl, GSize(w, h), style = Stroke(width = 4f))
        val m = 40f
        val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * m, y), strokeWidth = 6f)
            drawLine(c, Offset(x, y), Offset(x, y + dy * m), strokeWidth = 6f)
        }
        val pad = 24f
        corner(pad, pad, 1f, 1f); corner(size.width - pad, pad, -1f, 1f)
        corner(pad, size.height - pad, 1f, -1f); corner(size.width - pad, size.height - pad, -1f, -1f)
    }
}

/** "Checked In!" / "Checked Out!" (ref-4) with header "Self Check In" / "Self Check Out". */
@Composable
fun ClockResultScreen(result: ClockResult, displayName: String, schoolName: String, selfie: Bitmap?, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        FlowHeader(ClockInLogic.headerTitle(result.mode), schoolName, onBack = onDone)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = FormTokens.ScreenHPad, vertical = FormTokens.HeaderToForm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Box(Modifier.size(112.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).border(4.dp, KioskColors.green, CircleShape).background(KioskColors.secondaryFill),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selfie != null) {
                        Image(selfie.asImageBitmap(), "Your selfie", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(ClockInLogic.firstName(displayName).take(1).uppercase(), fontSize = 40.sp, color = KioskColors.textMuted)
                    }
                }
                Text(
                    "✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(KioskColors.green).padding(top = 5.dp),
                )
            }
            Text(ClockInLogic.resultTitle(result.mode), color = KioskColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
            Text(ClockInLogic.resultBody(result.mode), color = KioskColors.textMuted, fontSize = 15.sp, fontFamily = KioskFont, textAlign = TextAlign.Center)
            result.flaggedNote?.let {
                Text(it, color = KioskColors.orange, fontSize = 13.sp, fontFamily = KioskFont, textAlign = TextAlign.Center)
            }
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KioskColors.card)
                    .border(1.dp, KioskColors.border, RoundedCornerShape(14.dp)).padding(FormTokens.ScreenHPad),
                verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
            ) {
                ResultRow("Action", result.action)
                ResultRow("Time", result.time)
                ResultRow("Gate", result.gate)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Selfie", color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont)
                    Text(
                        if (result.selfieUploaded) "Uploaded ✓" else "Not uploaded", color = if (result.selfieUploaded) KioskColors.green else KioskColors.orange,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (result.selfieUploaded) KioskColors.greenDim else KioskColors.orangeDim).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                ResultRow("Record ID", result.recordId, valueColor = KioskColors.green)
            }
        }
        Text(
            "DONE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(FormTokens.ScreenHPad).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                .background(DarkGreen).clickableNoRipple(onDone).padding(16.dp),
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color = KioskColors.text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun proxyToBitmap(image: ImageProxy): Bitmap {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bmp
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
