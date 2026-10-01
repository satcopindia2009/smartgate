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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import com.satcop.smartvisitor.kiosk.ui.components.SgPillStyle
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSmallPill
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusChip
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusKind
import com.satcop.smartvisitor.kiosk.ui.theme.SgSize
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.LocalTime
import java.util.concurrent.Executors


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
    val lockGateLabel = state.attendance?.gateName?.takeIf { it.isNotBlank() } ?: ""
    val result = state.result
    val panel = state.panel
    BackHandler(enabled = panel != null || result != null) {
        if (result != null) controller.clearResult() else controller.back()
    }

    when {
        result != null -> ClockResultScreen(
            result = result, displayName = displayName, schoolName = schoolName, selfie = lastSelfie,
            checkInTime = ClockInLogic.time12h(state.attendance?.attendance?.let { it.checkInAt ?: it.timestamp })
                .takeIf { result.mode == AttendanceMode.CLOCK_OUT && it != "—" },
            onDone = { controller.clearResult(); lastSelfie = null; controller.refresh() },
        )
        panel != null && state.step == ClockStep.INFO -> ClockInfoScreen(
            mode = panel, today = state.attendance, schoolName = schoolName,
            gateLabel = state.attendance?.gateName?.takeIf { it.isNotBlank() } ?: state.attendance?.attendance?.let { ClockInLogic.gateLabel(it) } ?: "Campus",
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
        lock == LockState.SHIFT_COMPLETE -> ShiftCompleteScreen(
            displayName, schoolName, onLogout,
            detail = ClockInLogic.statusCard(state.attendance).detail,
        )
        lock == LockState.UNKNOWN -> LockScreen(
            displayName = displayName, schoolName = schoolName, loading = true, error = null,
            onClockIn = {}, onSignOut = onLogout, gateLabel = lockGateLabel,
            shiftText = GuardTodayLogic.shiftRow(state.attendance),
        )
        else -> LockScreen(
            displayName = displayName, schoolName = schoolName, loading = false, error = state.attendanceError,
            onClockIn = { controller.openPanel(AttendanceMode.CHECK_IN) }, onSignOut = onLogout, gateLabel = lockGateLabel,
            shiftText = GuardTodayLogic.shiftRow(state.attendance),
        )
    }
}

private fun hasPerm(context: Context, perm: String) =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

/** Clock-in lock screen (picture 40): greeting, status card (location, camera, lock), Check in, Sign out. */
@Composable
fun LockScreen(
    displayName: String,
    schoolName: String,
    loading: Boolean,
    error: String?,
    onClockIn: () -> Unit,
    onSignOut: () -> Unit,
    now: LocalTime = LocalTime.now(),
    gateLabel: String = "",
    /** Server shift text (shiftStartDisplay); null or blank hides the row, never hardcoded. */
    shiftText: String? = null,
) {
    val context = LocalContext.current
    val locationOk = hasPerm(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPerm(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val cameraOk = hasPerm(context, Manifest.permission.CAMERA)
    val date = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))
    val place = listOf(gateLabel, schoolName).firstOrNull { it.isNotBlank() }
    Column(
        modifier = Modifier.fillMaxSize().background(KioskColors.bg).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Text(ClockInLogic.greeting(displayName, now), color = KioskColors.text, style = SgType.ScreenTitle.copy(fontSize = 26.sp, lineHeight = 32.sp))
            Text(
                listOf(date, place).filter { !it.isNullOrBlank() }.joinToString(" · "),
                color = KioskColors.textMuted, style = SgType.Body, modifier = Modifier.padding(top = 4.dp),
            )
            GuardGap(SgSpacing.SectionGap)
            GuardCard {
                GuardInfoRow(Icons.Outlined.LocationOn, "Location", if (locationOk) "Allowed" else "Needed to check in", trailingCheck = locationOk)
                GuardInfoRow(Icons.Outlined.CameraAlt, "Camera", if (cameraOk) "Allowed" else "Needed to check in", trailingCheck = cameraOk, divider = !shiftText.isNullOrBlank())
                // Board 40 third row: server shiftStartDisplay only; hidden when the guard has no shift.
                if (!shiftText.isNullOrBlank()) GuardInfoRow(Icons.Outlined.Schedule, "Shift", shiftText, divider = false)
            }
            Text(ClockInLogic.LOCK_TEXT, color = KioskColors.textMuted, style = SgType.Label, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
            if (!error.isNullOrBlank()) {
                GuardGap()
                GuardBanner(error, GuardBannerKind.WARNING)
            }
        }
        Spacer(Modifier.height(16.dp))
        SgPrimaryButton(
            text = if (loading) "Please wait…" else GuardCopy.CHECK_IN,
            onClick = onClockIn, enabled = !loading,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = ClockInLogic.LOCK_BUTTON },
        )
        GuardTextButton(GuardCopy.SIGN_OUT, onSignOut, Modifier.fillMaxWidth())
    }
}

/** Shift complete (picture 48). */
@Composable
fun ShiftCompleteScreen(displayName: String, schoolName: String, onSignOut: () -> Unit, detail: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().background(KioskColors.bg).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            GuardCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GuardHeroIcon(Icons.Filled.Check, KioskColors.brandSoft, KioskColors.success)
                    Spacer(Modifier.height(16.dp))
                    Text(ClockInLogic.SHIFT_COMPLETE, color = KioskColors.text, style = SgType.ScreenTitle, textAlign = TextAlign.Center)
                    val sub = detail?.takeIf { it.isNotBlank() }
                        ?: listOf(ClockInLogic.firstName(displayName), schoolName).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, color = KioskColors.textMuted, style = SgType.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SgPrimaryButton(GuardCopy.SIGN_OUT, onSignOut, Modifier.fillMaxWidth())
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** Self check in / out info screen (picture 41). Status card (ruling R1) is kept, restyled. */
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
    val word = if (mode == AttendanceMode.CHECK_IN) "check-in" else "check-out"
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        GuardTopBar(GuardCopy.flowTitle(mode), onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            GuardCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(card.title, color = KioskColors.text, style = SgType.SectionTitle)
                        Text(card.detail, color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                        val where = listOf(gateLabel, schoolName).filter { it.isNotBlank() }.joinToString(" · ")
                        if (where.isNotBlank()) Text(where, color = KioskColors.textMuted, style = SgType.Caption, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    SgStatusChip(card.chip, if (card.chip == "On duty") SgStatusKind.IN_PROGRESS else if (card.canProceed) SgStatusKind.NEUTRAL else SgStatusKind.COMPLETED)
                }
            }
            Text("What we record", color = KioskColors.text, style = SgType.SectionTitle, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (mode == AttendanceMode.CHECK_IN) "To start your shift we take:" else "To end your shift we take:",
                color = KioskColors.textMuted, style = SgType.Body,
            )
            GuardCard {
                GuardInfoRow(Icons.Outlined.CameraAlt, "A selfie", "Taken now with the front camera. Make sure your face is clearly visible and well lit.")
                GuardInfoRow(Icons.Outlined.LocationOn, "Your location", "Used only to confirm you are inside the school campus.")
                GuardInfoRow(Icons.Outlined.Schedule, "The time", "Saved in school time as your $word.", divider = false)
            }
            card.note?.let { Text(it, color = KioskColors.textMuted, style = SgType.Body) }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(SgSpacing.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
        ) {
            SgPrimaryButton(GuardCopy.CONTINUE, onProceed, Modifier.fillMaxWidth(), enabled = card.canProceed)
            GuardOutlineButton(GuardCopy.BACK, onBack, Modifier.fillMaxWidth())
        }
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
    val msg = state.panelMessage
    val showErrorCard = state.panelError && !msg.isNullOrBlank() && !state.panelBusy
    val needsSettings = state.settingsHint != null || !cameraGranted
    Box(Modifier.fillMaxSize().background(Color.Black)) {
    Column(Modifier.fillMaxSize()) {
        // Top bar: close, title (picture 42)
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = SgSize.TopBarHeight).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape)
                    .clickableNoRipple { if (!state.panelBusy) controller.closePanel() }
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White) }
            Text(GuardCopy.selfieTitle(mode), color = Color.White, style = SgType.SectionTitle)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SgSpacing.ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !cameraGranted -> Text(
                        ClockInLogic.CAMERA_OFF, color = Color.White, style = SgType.Body,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
                    )
                    selfie != null -> Image(
                        bitmap = selfie!!.asImageBitmap(), contentDescription = "Your selfie",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                    cameraError != null -> Text(cameraError!!, color = Color.White, style = SgType.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    fake -> Text(QaHooks.bannerText, color = Color.White, style = SgType.Body)
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
            Text(GuardCopy.LOOK_AT_CAMERA, color = Color.White, style = SgType.SectionTitle, textAlign = TextAlign.Center)
            Text(ClockInLogic.SELFIE_HINT, color = Color.White.copy(alpha = 0.8f), style = SgType.Label, textAlign = TextAlign.Center)
            if (showLocationNotice) {
                GuardBanner(ClockInLogic.LOCATION_OFF_SOFT, GuardBannerKind.WARNING, Icons.Outlined.LocationOn)
            }
            if (!showErrorCard && !msg.isNullOrBlank()) {
                Text(msg, color = Color.White, style = SgType.Label, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = msg })
            }
            if (mode == AttendanceMode.CLOCK_OUT && !locationGranted && asked) {
                SgSmallPill(GuardCopy.OPEN_SETTINGS, { openAppSettings(context) }, style = SgPillStyle.SOFT)
            }
        }
        val canCapture = cameraGranted && !state.panelBusy && (fake || imageCapture != null)
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.panelBusy) Text("Please wait…", color = Color.White, style = SgType.Label, modifier = Modifier.padding(bottom = 8.dp))
            // Shutter: one tap = one attempt (selfie + location + time together).
            Box(
                Modifier.size(72.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape)
                    .background(if (canCapture) Color.White else Color(0xFF666666))
                    .semantics { contentDescription = "Capture selfie"; role = Role.Button }
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
                    },
            )
        }
    }
    if (showErrorCard) {
        // Errors (pictures 45-47): warning card + Retry / Open Settings + Back. Copy is exactly what the controller produced.
        Column(
            Modifier.fillMaxSize().background(KioskColors.bg).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                GuardCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val icon = when {
                            state.settingsHint == SettingsHint.CAMERA || !cameraGranted -> Icons.Outlined.CameraAlt
                            state.settingsHint == SettingsHint.LOCATION || msg.orEmpty().contains("campus", true) || msg.orEmpty().contains("location", true) -> Icons.Outlined.LocationOn
                            else -> Icons.Outlined.Warning
                        }
                        GuardHeroIcon(icon, KioskColors.warningSoft, KioskColors.warning)
                        Spacer(Modifier.height(16.dp))
                        Text(msg.orEmpty(), color = KioskColors.text, style = SgType.SectionTitle, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = msg })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (needsSettings) {
                SgPrimaryButton(GuardCopy.OPEN_SETTINGS, { openAppSettings(context) }, Modifier.fillMaxWidth())
            } else {
                // Retry always recaptures: clear the message and go back to the camera (attemptNo already dropped the held selfie).
                SgPrimaryButton(GuardCopy.RETRY, { controller.onPhotoRetaken() }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(FormTokens.ButtonGap))
            GuardOutlineButton(GuardCopy.BACK, { if (!state.panelBusy) controller.closePanel() }, Modifier.fillMaxWidth())
        }
    }
    }
}

/** Dashed round face guide (picture 42). */
@Composable
private fun FaceGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val d = minOf(size.width, size.height) * 0.72f
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2 - size.height * 0.04f)
        drawOval(
            Color.White.copy(alpha = 0.95f), tl, GSize(d, d),
            style = Stroke(width = 4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(22f, 16f))),
        )
    }
}

/** "Checked In!" / "Checked Out!" (pictures 43-44). */
@Composable
fun ClockResultScreen(
    result: ClockResult,
    displayName: String,
    schoolName: String,
    selfie: Bitmap?,
    onDone: () -> Unit,
    checkInTime: String? = null,
) {
    // guardPhotoUrl: the signed URL exactly as the server returned it (MediaUrl keeps ?t=, bearer only for the API host).
    var serverSelfie by remember(result.guardPhotoUrl) { mutableStateOf<Bitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(result.guardPhotoUrl) {
        val url = result.guardPhotoUrl
        if (selfie == null && url != null) {
            serverSelfie = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi().getMediaByUrl(url).let { b ->
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)
                    }
                }.getOrNull()
            }
        }
    }
    val shownSelfie = selfie ?: serverSelfie
    val out = result.mode == AttendanceMode.CLOCK_OUT
    Column(Modifier.fillMaxSize().background(KioskColors.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            GuardCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GuardHeroIcon(Icons.Filled.Check, KioskColors.brandSoft, KioskColors.success)
                    Spacer(Modifier.height(12.dp))
                    Text(ClockInLogic.resultTitle(result.mode), color = KioskColors.text, style = SgType.BigNumber)
                    Text(GuardCopy.resultBody(result.mode), color = KioskColors.textMuted, style = SgType.Body, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.size(SgSize.AvatarDetail).clip(CircleShape).background(KioskColors.brandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shownSelfie != null) {
                            Image(shownSelfie.asImageBitmap(), "Your selfie", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Text(ClockInLogic.firstName(displayName).take(1).uppercase(), color = KioskColors.primary, style = SgType.BigNumber)
                        }
                    }
                    Text(displayName.ifBlank { ClockInLogic.firstName(displayName) }, color = KioskColors.text, style = SgType.SectionTitle, modifier = Modifier.padding(top = 8.dp))
                    SgStatusChip(GuardCopy.resultChip(result.mode), if (out) SgStatusKind.COMPLETED else SgStatusKind.IN_PROGRESS, Modifier.padding(top = 6.dp))
                    result.flaggedNote?.let {
                        Spacer(Modifier.height(10.dp))
                        SgStatusChip("Flagged for review", SgStatusKind.IN_PROGRESS)
                        Text(it, color = KioskColors.warning, style = SgType.Label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 1.dp, color = KioskColors.border)
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        if (out && !checkInTime.isNullOrBlank()) {
                            GuardKeyValueRow("In", checkInTime)
                            GuardKeyValueRow("Out", result.time)
                        } else {
                            GuardKeyValueRow("Time", result.time)
                        }
                        GuardKeyValueRow("Gate", result.gate)
                        if (!result.selfieUploaded) GuardKeyValueRow("Selfie", "Not uploaded", KioskColors.warning)
                        GuardKeyValueRow("Record ID", result.recordId, KioskColors.primary)
                    }
                }
            }
        }
        SgPrimaryButton(GuardCopy.DONE, onDone, Modifier.fillMaxWidth().padding(SgSpacing.ScreenMargin))
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
