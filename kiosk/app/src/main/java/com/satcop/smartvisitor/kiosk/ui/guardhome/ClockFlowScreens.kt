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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
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

/** 1072 C: the phone's Location switch (the permission can be granted while GPS is off). */
private fun openLocationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { openAppSettings(context) }
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
    /** 1074: the check-in response of the face step; shown once as the "Checked In!" card. */
    initialCheckInRow: com.satcop.smartvisitor.kiosk.data.model.AttendanceRow? = null,
    onInitialRowConsumed: () -> Unit = {},
    /** 1076: a role whose clock-in the server refused: face-verify only. The card says Face verified and the lock is skipped. */
    initialVerifiedCard: ClockResult? = null,
    verifyOnlySession: Boolean = false,
    /** 1077: bumped when the server answers NOT_CLOCKED_IN: re-read attendance, the lock screen follows. */
    attendanceRecheck: Int = 0,
    /** 1077: gate chooser on the lock screen (id to name), shown for two or more gate duties. */
    gateChoices: List<Pair<String, String>> = emptyList(),
    chosenGateId: String? = null,
    onChooseGate: (String) -> Unit = {},
    /** 1079: patrol routes (today-summary) only with PATROL duty. */
    patrolCalls: Boolean = true,
    /** 1080: gate routes (visitor list) only with GATE duty (or duty not read yet). */
    gateCalls: Boolean = true,
    content: @Composable (requestLogout: () -> Unit) -> Unit,
) {
    val state by controller.state.collectAsState()
    SideEffect { controller.patrolCalls = patrolCalls; controller.gateCalls = gateCalls }
    LaunchedEffect(initialCheckInRow) {
        if (initialCheckInRow != null) controller.showCheckInResult(initialCheckInRow)
    }
    LaunchedEffect(attendanceRecheck) { if (attendanceRecheck > 0) controller.refreshAttendance() }
    LaunchedEffect(initialVerifiedCard) {
        if (initialVerifiedCard != null) controller.showVerifiedResult(initialVerifiedCard)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var lastSelfie by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) controller.refreshAttendance() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 1072 E: a row from an earlier IST day never unlocks the app.
    // 1074: the business day (IST minus the school cutoff). A row or a "Shift complete" from an earlier business day never counts.
    val businessDate = GuardGeoLogic.businessDate(java.time.Instant.now(), state.sessionCutoff)
    val lock = if (initialVerifiedCard != null || verifyOnlySession) LockState.UNLOCKED
    else ClockInLogic.lockState(state.attendance, state.attendanceLoaded, businessDate)
    val lockGateLabel = state.attendance?.gateName?.takeIf { it.isNotBlank() } ?: ""
    val result = state.result
    // 1075: release the hand-over only once the card is up (no lock-screen frame in between).
    LaunchedEffect(result) { if (result != null && (initialCheckInRow != null || initialVerifiedCard != null)) onInitialRowConsumed() }
    val panel = state.panel
    BackHandler(enabled = panel != null || result != null) {
        if (result != null) controller.clearResult() else controller.back()
    }
    // 1074: Back on the lock / Shift complete screen does nothing (no exit, no logout); only Sign Out leaves.
    BackHandler(enabled = panel == null && result == null && lock != LockState.UNLOCKED) { }

    when {
        // 1075: the face step handed over a check-in row: hold a plain background until the Checked In card shows (no lock flash).
        (initialCheckInRow != null || initialVerifiedCard != null) && result == null -> Box(Modifier.fillMaxSize().background(KioskColors.bg))
        result != null -> ClockResultScreen(
            result = result, displayName = displayName, schoolName = schoolName, selfie = lastSelfie,
            checkInTime = ClockInLogic.time12h(state.attendance?.attendance?.let { it.checkInAt ?: it.timestamp })
                .takeIf { result.mode == AttendanceMode.CLOCK_OUT && it != "—" },
            onDone = { controller.clearResult(); lastSelfie = null; controller.refresh() },
        )
        panel != null && state.step == ClockStep.INFO -> ClockInfoScreen(
            mode = panel, today = state.attendance?.takeUnless { ClockInLogic.isStaleDay(it, businessDate) }, schoolName = schoolName,
            gateLabel = state.attendance?.gateName?.takeIf { it.isNotBlank() } ?: state.attendance?.attendance?.let { ClockInLogic.gateLabel(it) } ?: "Campus",
            onBack = controller::closePanel, onProceed = controller::proceedToSelfie,
        )
        panel != null -> SelfieScreen(
            mode = panel, state = state, controller = controller, onSelfie = { lastSelfie = it },
        )
        lock == LockState.UNLOCKED -> {
            // 1072 G: shift-end reminder, once per day, never an automatic clock-out.
            LaunchedEffect(state.attendance, state.panel, state.result) {
                while (true) {
                    controller.evaluateShiftEnd()
                    kotlinx.coroutines.delay(30_000L)
                }
            }
            if (state.shiftEndPrompt) {
                AlertDialog(
                    onDismissRequest = controller::dismissShiftEndPrompt,
                    title = { Text(GuardGeoLogic.SHIFT_END_PROMPT, fontFamily = KioskFont) },
                    confirmButton = { TextButton(onClick = controller::acceptShiftEndPrompt) { Text(GuardGeoLogic.SHIFT_END_CLOCK_OUT) } },
                    dismissButton = { TextButton(onClick = controller::dismissShiftEndPrompt) { Text(GuardGeoLogic.SHIFT_END_LATER) } },
                )
            }
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
            gateChoices = gateChoices, chosenGateId = chosenGateId, onChooseGate = onChooseGate,
        )
    }
}

private fun hasPerm(context: Context, perm: String) =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

/** Clock-in lock screen (Viren's reference 1, our teal): full teal, shield, greeting, lock chip, CLOCK IN TO CONTINUE, Sign Out. */
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
    /** 1077: two or more gate duties => the guard picks the gate (id to name); empty = no chooser. */
    gateChoices: List<Pair<String, String>> = emptyList(),
    chosenGateId: String? = null,
    onChooseGate: (String) -> Unit = {},
) {
    val subtitle = listOf(GuardCopy.ROLE, schoolName).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxSize().background(KioskColors.primary).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(
                ClockInLogic.greeting(displayName, now), color = Color.White, textAlign = TextAlign.Center,
                style = SgType.ScreenTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
            )
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = SgType.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.clip(PillShape).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(ClockInLogic.LOCK_CHIP, color = Color.White, style = SgType.Label)
            }
            Text(
                ClockInLogic.LOCK_TEXT, color = Color.White.copy(alpha = 0.9f), style = SgType.Body, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 12.dp, end = 12.dp),
            )
            if (gateChoices.size > 1) com.satcop.smartvisitor.kiosk.ui.duty.GateChooser(gateChoices, chosenGateId, onChooseGate)
            if (!error.isNullOrBlank()) {
                GuardGap()
                GuardBanner(error, GuardBannerKind.WARNING)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = SgSize.ButtonHeight).clip(PillShape).background(Color.White)
                .clickable(enabled = !loading, role = Role.Button, onClick = onClockIn)
                .padding(horizontal = 24.dp)
                .semantics { contentDescription = ClockInLogic.LOCK_BUTTON },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!loading) {
                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, tint = KioskColors.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (loading) "Please wait…" else ClockInLogic.LOCK_BUTTON, color = KioskColors.primary, style = SgType.Button.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button, onClick = onSignOut),
            contentAlignment = Alignment.Center,
        ) { Text(ClockInLogic.SIGN_OUT, color = Color.White, style = SgType.Button, maxLines = 1) }
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

/** Self Check In / Out info screen (Viren's reference 2, our teal). Current Status (ruling R1) + Guard Details + How it works. */
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
    val activeChip = when (today?.state) {
        com.satcop.smartvisitor.kiosk.data.model.AttendanceState.PRESENT -> "Active" to SgStatusKind.IN_PROGRESS
        com.satcop.smartvisitor.kiosk.data.model.AttendanceState.CLOCKED_OUT -> "Completed" to SgStatusKind.COMPLETED
        else -> "Inactive" to SgStatusKind.NEUTRAL
    }
    val where = listOf(gateLabel, if (schoolName.isNotBlank()) "$schoolName Campus" else "").filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        GuardTealHeader(
            GuardCopy.flowTitle(mode),
            listOf(GuardCopy.ROLE, schoolName).filter { it.isNotBlank() }.joinToString(" · "),
            onBack,
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            GuardCard {
                Text(GuardCopy.CURRENT_STATUS, color = KioskColors.textMuted, style = SgType.Label)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(card.title, color = KioskColors.text, style = SgType.SectionTitle)
                        Text(card.detail, color = KioskColors.textMuted, style = SgType.Body)
                    }
                    Spacer(Modifier.width(8.dp))
                    SgStatusChip(activeChip.first, activeChip.second)
                }
            }
            GuardCard {
                Text(GuardCopy.GUARD_DETAILS, color = KioskColors.textMuted, style = SgType.Label)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GuardIconBadge(Icons.Filled.Shield)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(GuardCopy.ROLE, color = KioskColors.text, style = SgType.BodyStrong)
                        if (where.isNotBlank()) Text(where, color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = FontWeight.Normal))
                    }
                }
            }
            GuardBanner(GuardCopy.infoBanner(mode), GuardBannerKind.INFO, Icons.Outlined.Info)
            GuardCard {
                Text(GuardCopy.HOW_IT_WORKS, color = KioskColors.text, style = SgType.SectionTitle)
                val stepIcons = listOf(Icons.Outlined.CameraAlt, Icons.Outlined.CloudUpload, Icons.Outlined.CheckCircle)
                GuardCopy.HOW_STEPS.forEachIndexed { i, step ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GuardIconBadge(stepIcons[i.coerceAtMost(stepIcons.lastIndex)], size = 32.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(step, color = KioskColors.text, style = SgType.Body, modifier = Modifier.weight(1f))
                    }
                }
            }
            card.note?.let { Text(it, color = KioskColors.textMuted, style = SgType.Body) }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(SgSpacing.ScreenMargin)) {
            SgPrimaryButton(ClockInLogic.proceedLabel(mode), onProceed, Modifier.fillMaxWidth(), enabled = card.canProceed)
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
    // 1072 C: the permission can be granted while the phone's Location switch is off. Ask the OS, never assume.
    controller.locationServiceOn = { tracker.isLocationServiceOn() }
    DisposableEffect(locationGranted) {
        if (locationGranted) tracker.start()
        onDispose { tracker.stop() }
    }
    DisposableEffect(lifecycleOwner, locationGranted) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && locationGranted) tracker.restart() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val serviceOn by tracker.serviceOn.collectAsState()
    LaunchedEffect(locationGranted, serviceOn) {
        // No listener is registered while every provider is off, so poll the switch lightly.
        while (locationGranted) {
            kotlinx.coroutines.delay(2_000L)
            if (tracker.isLocationServiceOn() != serviceOn) tracker.restart()
        }
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

    val geoMode = state.geoMode
    val showLocationNotice = asked && (!locationGranted || !serviceOn) && mode == AttendanceMode.CHECK_IN && geoMode != GeoMode.RESTRICT
    val preGate = if (asked) GuardGeoLogic.locationGate(mode, geoMode, locationGranted, serviceOn) else null
    // 1074: the up-front distance hint only uses a reading taken after the last Retry (never a stale one).
    var hintAfterMs by remember { mutableStateOf(0L) }
    val freshFix = fix?.takeIf { it.elapsedMs > hintAfterMs && it.ageMs(SystemClock.elapsedRealtime()) <= com.satcop.smartvisitor.kiosk.data.geo.GpsPolicy.MAX_FIX_AGE_MS }
    val restrictHint = GuardGeoLogic.restrictHint(geoMode, freshFix, state.fence)
    val msg = state.panelMessage
    val showErrorCard = (state.panelError && !msg.isNullOrBlank() && !state.panelBusy) || preGate != null
    val shownMsg = if (state.panelError && !msg.isNullOrBlank()) msg else preGate?.message
    val locationServiceProblem = state.settingsHint == SettingsHint.LOCATION_SERVICE || preGate?.action == LocationAction.LOCATION_SETTINGS
    val needsSettings = state.settingsHint != null || !cameraGranted || preGate != null
    val headerSubtitle = listOf(GuardCopy.ROLE, state.attendance?.schoolName.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
    Box(Modifier.fillMaxSize().background(KioskColors.bg)) {
    Column(Modifier.fillMaxSize()) {
        GuardTealHeader(
            GuardCopy.flowTitle(mode), headerSubtitle,
            onBack = { if (!state.panelBusy) controller.closePanel() },
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Row(
                Modifier.clip(PillShape).background(KioskColors.brandSoft).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = KioskColors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(GuardCopy.photoPill(mode), color = KioskColors.primary, style = SgType.BodyStrong)
            }
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111)),
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
                if (cameraGranted && selfie == null) {
                    FaceGuide()
                    CornerMarkers()
                    CameraPill(ClockInLogic.cameraLabel(mode), Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                    CameraPill(ClockInLogic.SELFIE_HINT, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                }
            }
            if (showLocationNotice) {
                GuardBanner(ClockInLogic.LOCATION_OFF_SOFT, GuardBannerKind.WARNING, Icons.Outlined.LocationOn)
                if (locationGranted && !serviceOn) {
                    SgSmallPill(GuardCopy.OPEN_LOCATION_SETTINGS, { openLocationSettings(context) }, style = SgPillStyle.SOFT)
                }
            }
            if (restrictHint != null && !showErrorCard) {
                GuardBanner(restrictHint, GuardBannerKind.WARNING, Icons.Outlined.LocationOn)
            }
            if (fix?.isMock == true) {
                GuardBanner(GuardGeoLogic.MOCK_LOCATION_NOTE, GuardBannerKind.WARNING)
            }
            if (!showErrorCard && !msg.isNullOrBlank()) {
                Text(msg, color = KioskColors.text, style = SgType.Label, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = msg })
            }
            if (mode == AttendanceMode.CLOCK_OUT && !locationGranted && asked && !showErrorCard) {
                SgSmallPill(GuardCopy.OPEN_SETTINGS, { openAppSettings(context) }, style = SgPillStyle.SOFT)
            }
        }
        val canCapture = cameraGranted && !state.panelBusy && (fake || imageCapture != null)
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
        ) {
            if (state.panelBusy) Text("Please wait…", color = KioskColors.textMuted, style = SgType.Label)
            // One tap = one attempt (selfie + location + time together).
            GuardIconPrimaryButton(
                GuardCopy.CAPTURE, Icons.Outlined.CameraAlt, enabled = canCapture,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Capture selfie" },
                onClick = {
                    selfie = null
                    if (fake) {
                        submitWith(QaHooks.frame(ClockInLogic.actionLabel(mode))!!)
                    } else {
                        val cap = imageCapture ?: return@GuardIconPrimaryButton
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
                },
            )
            GuardOutlineButton(GuardCopy.BACK, { if (!state.panelBusy) controller.closePanel() }, Modifier.fillMaxWidth())
        }
    }
    if (showErrorCard) {
        // Errors: warning card + Retry / Open Settings / Open location settings + Back. Copy is exactly what the controller produced.
        Column(
            Modifier.fillMaxSize().background(KioskColors.bg).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 24.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                GuardCard {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val icon = when {
                            state.settingsHint == SettingsHint.CAMERA || !cameraGranted -> Icons.Outlined.CameraAlt
                            state.settingsHint == SettingsHint.LOCATION || locationServiceProblem || shownMsg.orEmpty().contains("campus", true) || shownMsg.orEmpty().contains("location", true) -> Icons.Outlined.LocationOn
                            else -> Icons.Outlined.Warning
                        }
                        GuardHeroIcon(icon, KioskColors.warningSoft, KioskColors.warning)
                        Spacer(Modifier.height(16.dp))
                        Text(shownMsg.orEmpty(), color = KioskColors.text, style = SgType.SectionTitle, textAlign = TextAlign.Center, modifier = Modifier.semantics { contentDescription = shownMsg.orEmpty() })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (locationServiceProblem) {
                SgPrimaryButton(GuardCopy.OPEN_LOCATION_SETTINGS, { openLocationSettings(context) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(FormTokens.ButtonGap))
                GuardOutlineButton(GuardCopy.RETRY, { hintAfterMs = SystemClock.elapsedRealtime(); controller.onPhotoRetaken() }, Modifier.fillMaxWidth())
            } else if (needsSettings) {
                SgPrimaryButton(GuardCopy.OPEN_SETTINGS, { openAppSettings(context) }, Modifier.fillMaxWidth())
            } else {
                // Retry always recaptures: clear the message and go back to the camera (attemptNo already dropped the held selfie).
                SgPrimaryButton(GuardCopy.RETRY, { hintAfterMs = SystemClock.elapsedRealtime(); controller.onPhotoRetaken() }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(FormTokens.ButtonGap))
            GuardOutlineButton(GuardCopy.BACK, { if (!state.panelBusy) controller.closePanel() }, Modifier.fillMaxWidth())
        }
    }
    }
}

/** Dark translucent pill over the camera ("Front Camera · Check In", "Position your face inside the oval"). */
@Composable
private fun CameraPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, style = SgType.Label, maxLines = 1, textAlign = TextAlign.Center,
        modifier = modifier.clip(PillShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Four teal corner markers of the camera frame. */
@Composable
private fun CornerMarkers() {
    val c = KioskColors.brand
    Canvas(Modifier.fillMaxSize()) {
        val len = 28.dp.toPx(); val inset = 14.dp.toPx(); val w = 4.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), strokeWidth = w)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), strokeWidth = w)
        }
        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)
    }
}

/** White oval face guide (reference 3). */
@Composable
private fun FaceGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.62f
        val h = w * 1.3f
        val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
        drawOval(Color.White.copy(alpha = 0.95f), tl, GSize(w, h), style = Stroke(width = 5f))
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
    // 1074: after a check-in the card shows for ~4 s, then the guard goes to Today (Done goes earlier).
    if (!out) LaunchedEffect(result) { kotlinx.coroutines.delay(4_000L); onDone() }
    val headerSubtitle = listOf(GuardCopy.ROLE, schoolName).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxSize().background(KioskColors.bg).navigationBarsPadding()) {
        // Header says "Self Check In" / "Self Check Out" to match the action (the reference screenshot shows the wrong one).
        GuardTealHeader(ClockInLogic.resultHeader(result), headerSubtitle, onBack = onDone)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(SgSize.AvatarDetail + 8.dp)) {
                Box(
                    Modifier.size(SgSize.AvatarDetail).align(Alignment.Center).clip(CircleShape).background(KioskColors.brandSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    if (shownSelfie != null) {
                        Image(shownSelfie.asImageBitmap(), "Your selfie", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(ClockInLogic.firstName(displayName).take(1).uppercase(), color = KioskColors.primary, style = SgType.BigNumber)
                    }
                }
                Box(
                    Modifier.size(28.dp).align(Alignment.BottomEnd).clip(CircleShape).background(KioskColors.success),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(ClockInLogic.resultTitleOf(result), color = KioskColors.text, style = SgType.BigNumber)
            Text(if (result.verifyOnly) "Your face is verified. Welcome!" else GuardCopy.resultBody(result.mode), color = KioskColors.textMuted, style = SgType.Body, textAlign = TextAlign.Center)
            result.flaggedNote?.let {
                Spacer(Modifier.height(12.dp))
                GuardBanner(it, GuardBannerKind.WARNING)
                result.flaggedDetail?.let { d ->
                    Text(d, color = KioskColors.warning, style = SgType.Label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
            result.mockNote?.let {
                Spacer(Modifier.height(12.dp))
                GuardBanner(it, GuardBannerKind.WARNING)
            }
            Spacer(Modifier.height(16.dp))
            GuardCard {
                GuardKeyValueRow("Action", result.action)
                if (out && !checkInTime.isNullOrBlank()) {
                    GuardKeyValueRow("In", checkInTime)
                    GuardKeyValueRow("Out", result.time)
                } else {
                    GuardKeyValueRow("Time", result.time)
                }
                GuardKeyValueRow("Gate", result.gate)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Selfie", color = KioskColors.textMuted, style = SgType.Body)
                    Spacer(Modifier.weight(1f))
                    if (result.selfieUploaded) SgStatusChip(GuardCopy.SELFIE_UPLOADED, SgStatusKind.COMPLETED)
                    else Text("Not uploaded", color = KioskColors.warning, style = SgType.BodyStrong)
                }
                GuardKeyValueRow("Record ID", result.recordId, KioskColors.primary)
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
