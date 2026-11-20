package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CameraSettings
import com.example.data.local.FilmRollEntity
import com.example.domain.model.DateStampStyle
import com.example.domain.model.LensMount
import com.example.engine.CameraSurfaceHolder
import com.example.ui.components.CameraBodyThumbnail
import com.example.ui.components.FilmCanisterThumbnail
import com.example.ui.components.LensThumbnail
import com.example.ui.components.LiveOpticalPreviewSurface
import com.example.ui.components.MachinedScrewHead
import com.example.ui.components.blackLeatherTexture
import com.example.ui.components.brushedMetalPlate
import com.example.ui.components.recessedInstrumentWell
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MachinedSurface
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.OpticalTeal
import com.example.ui.theme.PositiveGreen
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import com.example.viewmodel.ArgentiqueUiState
import com.example.viewmodel.BoutiqueFocusTab
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ViewfinderScreen(
    uiState: ArgentiqueUiState,
    settings: CameraSettings,
    activeRoll: FilmRollEntity?,
    developedCount: Int,
    inProgressRollsCount: Int,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onSurfaceTextureReady: (CameraSurfaceHolder) -> Unit,
    onSurfaceTextureDestroyed: (CameraSurfaceHolder) -> Unit,
    onTapToFocus: (Float, Float) -> Unit,
    onToggleAeAfLock: () -> Unit,
    onEvChange: (Float) -> Unit,
    onZoomStep: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onCaptureClick: () -> Unit,
    onCycleDateStampStyle: () -> Unit,
    onCycleSelfTimer: () -> Unit,
    onOpenBoutique: (BoutiqueFocusTab) -> Unit,
    onOpenLabo: () -> Unit,
    onOpenSettings: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleSampleNight: () -> Unit,
    onUseDemoScene: () -> Unit,
    onOpenDevelopNow: () -> Unit,
    onDismissEndOfRoll: () -> Unit
) {
    val useLiveCamera = hasCameraPermission && !uiState.useSampleSceneInViewfinder
    val body = uiState.selectedBody
    val lens = uiState.selectedLens
    val film = uiState.selectedFilm
    val expiration = uiState.selectedExpiration
    val isToyCam = body.isFixedFocusToyCam
    val shotsTaken = activeRoll?.shotsTaken ?: 0
    val maxExposures = activeRoll?.maxExposures ?: body.maxExposures
    val isRollFull = shotsTaken >= maxExposures

    // Shutter blackout flash animation
    val shutterFlashAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.shutterFlashTick) {
        if (uiState.shutterFlashTick > 0) {
            shutterFlashAlpha.snapTo(0.92f)
            shutterFlashAlpha.animateTo(0f, tween(170))
        }
    }

    // Simulated optical focus state (strictly disabled on fixed-focus toy cams!)
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTriggerTick by remember { mutableIntStateOf(0) }
    var simulatedDistanceLabel by remember { mutableStateOf("2.4m") }
    var isFocusLockedVisual by remember { mutableStateOf(false) }
    var showToyFixedHint by remember { mutableStateOf(false) }
    val focusProgress = remember { Animatable(1f) }

    // Clear focus overlay immediately if switching to a fixed-focus toy cam
    LaunchedEffect(body.id) {
        if (isToyCam) {
            focusPoint = null
            isFocusLockedVisual = false
        }
    }

    LaunchedEffect(focusTriggerTick) {
        if (focusTriggerTick > 0 && focusPoint != null && !isToyCam) {
            isFocusLockedVisual = false
            focusProgress.snapTo(0f)
            focusProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
            isFocusLockedVisual = true
            if (!uiState.isAeAfLocked) {
                delay(1650L)
                focusPoint = null
                isFocusLockedVisual = false
            }
        }
    }

    LaunchedEffect(showToyFixedHint) {
        if (showToyFixedHint) {
            delay(1400L)
            showToyFixedHint = false
        }
    }

    val activeDateStyle = settings.effectiveDateStampStyle
    val liveDatePreview = remember(activeDateStyle) {
        activeDateStyle.formatDate(Date())
    }

    // Simulated mechanical light-meter shutter speed readout
    val simulatedShutterLabel = remember(film.iso, uiState.evCompensation, settings.sampleSceneNight, useLiveCamera) {
        val baseEv = if (!useLiveCamera && settings.sampleSceneNight) 5.5f else 11.5f
        val effectiveSpeed = (film.iso / 100f) * 2f.pow(baseEv - 8f - uiState.evCompensation) * 15f
        val standardSpeeds = listOf(15, 30, 60, 125, 250, 500, 1000, 2000)
        val nearest = standardSpeeds.minByOrNull { abs(it - effectiveSpeed) } ?: 125
        "1/${nearest}s"
    }

    var currentZoomState by remember(uiState.zoomMultiplier) { mutableFloatStateOf(uiState.zoomMultiplier) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blackLeatherTexture()
    ) {
        // ==================== 1. TOP MACHINED METAL PLATE ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .brushedMetalPlate(
                    shape = RectangleShape,
                    borderColor = Color(0xFF2B2F3B),
                    borderWidth = 0.8.dp,
                    elevated = true
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopStart))
            MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopEnd))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Settings / Mechanical Menu Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .brushedMetalPlate(shape = CircleShape, borderColor = HairlineMetal, elevated = true)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = SilverChrome
                    )
                }

                // Brand Wordmark ("Peloche.") — taps straight into Sac Photo (camera bag)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenBoutique(BoutiqueFocusTab.BODY) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("peloche_wordmark_bag_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Peloche",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                        color = FilmCream
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BrassAccent)
                    )
                    if (inProgressRollsCount > 1) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KodakAmber.copy(alpha = 0.18f))
                                .border(1.dp, KodakAmber.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraRoll,
                                contentDescription = "Pellicules en cours dans le sac",
                                tint = KodakAmber,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "$inProgressRollsCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = KodakAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Recessed Mechanical Frame Counter Drum (e.g. 04 / 36)
                val rollProgress = (shotsTaken.toFloat() / maxExposures.coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .recessedInstrumentWell(
                            shape = RoundedCornerShape(9.dp),
                            borderColor = if (isRollFull) LeicaCrimson.copy(alpha = 0.85f) else HairlineMetal
                        )
                        .clickable { onOpenLabo() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("frame_counter_badge"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isRollFull) LeicaCrimson else if (shotsTaken > 0) KodakAmber else BrassAccent)
                        )
                        Text(
                            text = "%02d".format(shotsTaken),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                            color = if (isRollFull) LeicaCrimson else FilmCream
                        )
                        Text(
                            text = "/$maxExposures",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmEngraving
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AnodizedSlate)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(rollProgress)
                                .background(if (isRollFull) LeicaCrimson else BrassAccent)
                        )
                    }
                }

                // Self-Timer Mechanical Button (OFF / 3s / 10s) — replaces manual advance lever
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .brushedMetalPlate(
                            shape = CircleShape,
                            borderColor = if (settings.selfTimerSeconds > 0) BrassAccent else HairlineMetal,
                            elevated = true
                        )
                        .clickable { onCycleSelfTimer() }
                        .testTag("self_timer_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Retardateur",
                            tint = if (settings.selfTimerSeconds > 0) BrassAccent else SilverChrome,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (settings.selfTimerSeconds > 0) "${settings.selfTimerSeconds}s" else "OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (settings.selfTimerSeconds > 0) BrassAccent else WarmEngraving,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==================== 2. CENTRAL LIVE WYSIWYG VIEWFINDER ====================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCameraPermission && !uiState.useSampleSceneInViewfinder) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brushedMetalPlate(shape = RoundedCornerShape(14.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = BrassAccent,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = stringResource(R.string.permission_camera_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = FilmCream
                    )
                    Text(
                        text = stringResource(R.string.permission_camera_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmEngraving
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRequestCameraPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
                            modifier = Modifier.testTag("grant_camera_button")
                        ) {
                            Text(stringResource(R.string.permission_camera_grant))
                        }
                        OutlinedButton(
                            onClick = onUseDemoScene,
                            modifier = Modifier.testTag("use_sample_scene_button")
                        ) {
                            Text(stringResource(R.string.permission_camera_demo), color = SilverChrome)
                        }
                    }
                }
            } else {
                val frameRatio = body.aspectRatioSpec.aspectRatio.coerceIn(0.62f, 2.75f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(frameRatio, matchHeightConstraintsFirst = frameRatio < 0.9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.5.dp, HairlineMetal, RoundedCornerShape(8.dp))
                        .pointerInput(body.id, isToyCam, uiState.isAeAfLocked) {
                            detectTapGestures { offset ->
                                if (isToyCam) {
                                    // Fixed-focus toy camera: hyperfocal plastic lens, NO autofocus!
                                    showToyFixedHint = true
                                } else {
                                    focusPoint = offset
                                    val nx = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    simulatedDistanceLabel = computeSimulatedFocusDistance(nx, ny, lens.focalLengthMm)
                                    focusTriggerTick++
                                    onTapToFocus(nx, ny)
                                }
                            }
                        }
                        .pointerInput(body.id, isToyCam, lens.id) {
                            if (!isToyCam) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (abs(zoom - 1f) > 0.008f) {
                                        val maxZ = if (lens.zoomMaxMm != null) {
                                            (lens.zoomMaxMm.toFloat() / lens.focalLengthMm.toFloat()).coerceIn(1.2f, 3.0f)
                                        } else {
                                            2.0f
                                        }
                                        val next = (currentZoomState * zoom).coerceIn(1.0f, maxZ)
                                        currentZoomState = next
                                        onZoomChange(next)
                                    }
                                }
                            }
                        }
                ) {
                    LiveOpticalPreviewSurface(
                        params = uiState.liveOpticalParams,
                        useCameraStream = useLiveCamera,
                        sampleSceneNight = settings.sampleSceneNight,
                        onSurfaceTextureReady = onSurfaceTextureReady,
                        onSurfaceTextureDestroyed = onSurfaceTextureDestroyed,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Etched Ground-Glass Brightline & Split-Prism Reticle (hidden on fixed-focus toy cams except simple thirds)
                    if (settings.gridEnabled) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val hairline = FilmCream.copy(alpha = 0.18f)
                            val brightline = BrassAccent.copy(alpha = 0.50f)
                            val thirdW = size.width / 3f
                            val thirdH = size.height / 3f
                            drawLine(hairline, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 0.9f)
                            drawLine(hairline, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 0.9f)
                            drawLine(hairline, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 0.9f)
                            drawLine(hairline, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 0.9f)

                            if (!isToyCam) {
                                val pad = 18f
                                val arm = 22f
                                val sw = 1.5f
                                drawLine(brightline, Offset(pad, pad), Offset(pad + arm, pad), strokeWidth = sw)
                                drawLine(brightline, Offset(pad, pad), Offset(pad, pad + arm), strokeWidth = sw)
                                drawLine(brightline, Offset(size.width - pad, pad), Offset(size.width - pad - arm, pad), strokeWidth = sw)
                                drawLine(brightline, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), strokeWidth = sw)
                                drawLine(brightline, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), strokeWidth = sw)
                                drawLine(brightline, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), strokeWidth = sw)
                                drawLine(brightline, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), strokeWidth = sw)
                                drawLine(brightline, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), strokeWidth = sw)

                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawCircle(FilmCream.copy(alpha = 0.25f), radius = 24f, center = Offset(cx, cy), style = Stroke(1f))
                                drawCircle(BrassAccent.copy(alpha = 0.42f), radius = 12f, center = Offset(cx, cy), style = Stroke(1f))
                                drawLine(BrassAccent.copy(alpha = 0.38f), Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = 0.9f)
                            }
                        }
                    }

                    // Simulated Analog Focus Acquisition Overlay (distinct per optical mount, NEVER shown on toy cams!)
                    if (!isToyCam) {
                        focusPoint?.let { pt ->
                            val p = focusProgress.value
                            val lockedColor = when {
                                uiState.isAeAfLocked -> KodakAmber
                                body.mount == LensMount.COMPACT_SERIES && isFocusLockedVisual -> PositiveGreen
                                isFocusLockedVisual -> BrassAccent
                                else -> FilmCream.copy(alpha = 0.85f)
                            }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                when (body.mount) {
                                    LensMount.RANGEFINDER_M, LensMount.PANORAMIC_XPAN -> {
                                        // Converging dual rangefinder yellow-amber split patch
                                        val offset = (1f - p) * 26f
                                        val rectW = 54f
                                        val rectH = 38f
                                        drawRect(
                                            color = FilmCream.copy(alpha = 0.55f * (1f - p * 0.65f)),
                                            topLeft = Offset(pt.x - rectW / 2f - offset, pt.y - rectH / 2f),
                                            size = Size(rectW, rectH),
                                            style = Stroke(width = 1.6f)
                                        )
                                        drawRect(
                                            color = lockedColor.copy(alpha = 0.20f),
                                            topLeft = Offset(pt.x - rectW / 2f + offset, pt.y - rectH / 2f),
                                            size = Size(rectW, rectH)
                                        )
                                        drawRect(
                                            color = lockedColor,
                                            topLeft = Offset(pt.x - rectW / 2f + offset, pt.y - rectH / 2f),
                                            size = Size(rectW, rectH),
                                            style = Stroke(width = 2.2f)
                                        )
                                        drawCircle(lockedColor, radius = 3f, center = pt)
                                    }

                                    LensMount.SLR_35MM, LensMount.MEDIUM_FORMAT_6X6 -> {
                                        // Split-prism / micro-prism focusing collar aligning into crisp lock
                                        val radius = 38f - p * 8f
                                        val splitShift = (1f - p) * 18f
                                        drawCircle(lockedColor.copy(alpha = 0.45f), radius = radius + 8f, center = pt, style = Stroke(1.3f))
                                        drawCircle(lockedColor, radius = radius, center = pt, style = Stroke(2.2f))
                                        drawLine(lockedColor, Offset(pt.x - radius, pt.y), Offset(pt.x + radius, pt.y), strokeWidth = 1.5f)
                                        // Upper & lower split tick marks converging onto center vertical
                                        drawLine(lockedColor, Offset(pt.x - splitShift, pt.y - radius + 4f), Offset(pt.x - splitShift, pt.y - 2f), strokeWidth = 2.4f)
                                        drawLine(lockedColor, Offset(pt.x + splitShift, pt.y + 2f), Offset(pt.x + splitShift, pt.y + radius - 4f), strokeWidth = 2.4f)
                                    }

                                    else -> {
                                        // 90s Compact AF corner brackets contracting onto subject
                                        val half = 42f - p * 12f
                                        val arm = 14f
                                        val sw = 2.4f
                                        // Top-left
                                        drawLine(lockedColor, Offset(pt.x - half, pt.y - half), Offset(pt.x - half + arm, pt.y - half), sw)
                                        drawLine(lockedColor, Offset(pt.x - half, pt.y - half), Offset(pt.x - half, pt.y - half + arm), sw)
                                        // Top-right
                                        drawLine(lockedColor, Offset(pt.x + half, pt.y - half), Offset(pt.x + half - arm, pt.y - half), sw)
                                        drawLine(lockedColor, Offset(pt.x + half, pt.y - half), Offset(pt.x + half, pt.y - half + arm), sw)
                                        // Bottom-left
                                        drawLine(lockedColor, Offset(pt.x - half, pt.y + half), Offset(pt.x - half + arm, pt.y + half), sw)
                                        drawLine(lockedColor, Offset(pt.x - half, pt.y + half), Offset(pt.x - half, pt.y + half - arm), sw)
                                        // Bottom-right
                                        drawLine(lockedColor, Offset(pt.x + half, pt.y + half), Offset(pt.x + half - arm, pt.y + half), sw)
                                        drawLine(lockedColor, Offset(pt.x + half, pt.y + half), Offset(pt.x + half, pt.y + half - arm), sw)
                                        drawCircle(lockedColor, radius = 3.2f, center = pt)
                                    }
                                }
                            }

                            // Simulated focus confirmation & distance readout pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xCC0A0B0E))
                                    .border(1.dp, lockedColor.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("simulated_focus_badge")
                            ) {
                                Text(
                                    text = if (isFocusLockedVisual) {
                                        "${body.focusModeLabel} • NET $simulatedDistanceLabel"
                                    } else {
                                        "${body.focusModeLabel} • MISE AU POINT…"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = lockedColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Brief Hyperfocal Fixed-Focus feedback when user taps viewfinder on a Toy Cam
                    val toyHintAlpha by animateFloatAsState(
                        targetValue = if (showToyFixedHint && isToyCam) 1f else 0f,
                        animationSpec = tween(220),
                        label = "toy_fixed_hint_alpha"
                    )
                    if (toyHintAlpha > 0.02f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xD90A0B0E).copy(alpha = 0.85f * toyHintAlpha))
                                .border(1.dp, KodakAmber.copy(alpha = 0.7f * toyHintAlpha), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("toy_fixed_focus_hint")
                        ) {
                            Text(
                                text = "FOCALE FIXE • HYPERFOCALE 1.5m–∞ (SANS AF)",
                                style = MaterialTheme.typography.labelSmall,
                                color = KodakAmber.copy(alpha = toyHintAlpha),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Self-Timer Countdown Overlay (3, 2, 1)
                    if (uiState.selfTimerCountdown > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x55000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC0D0E12))
                                    .border(2.5.dp, BrassAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${uiState.selfTimerCountdown}",
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp),
                                    color = BrassAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Top Viewfinder HUD badges: Format + Focal/Zoom + Focus Mode / AE-AF Lock
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HudChip(
                                text = if (body.supportsDiptych && settings.diptychEnabled) {
                                    "${body.aspectRatioSpec.formatBadge} • DIPTYQUE"
                                } else {
                                    body.aspectRatioSpec.formatBadge
                                },
                                accent = BrassAccent
                            )

                            val effectiveMm = (lens.focalLengthMm * uiState.zoomMultiplier).roundToInt()
                            HudChip(
                                text = if (isToyCam) {
                                    "${lens.focalLengthMm}mm ${lens.maxAperture} FIXE"
                                } else {
                                    "${effectiveMm}mm ${lens.maxAperture}"
                                },
                                accent = if (isToyCam) KodakAmber else SilverChrome,
                                onClick = if (isToyCam) null else onZoomStep,
                                testTag = "focal_zoom_chip"
                            )
                        }

                        if (isToyCam) {
                            // Static Fixed-Focus indicator — no autofocus / no AE-AF lock on toy cams
                            HudChip(
                                text = "FIXE ∞",
                                accent = KodakAmber,
                                testTag = "toy_fixed_focus_badge"
                            )
                        } else {
                            // AUTO / AE-AF Lock button for non-toy bodies
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (uiState.isAeAfLocked) KodakAmber.copy(alpha = 0.88f)
                                        else Color(0xAA100F0E)
                                    )
                                    .border(
                                        1.dp,
                                        if (uiState.isAeAfLocked) KodakAmber else HairlineMetal,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onToggleAeAfLock() }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("ae_af_lock_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isAeAfLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = "Verrouillage AE/AF",
                                    tint = if (uiState.isAeAfLocked) MatteLeather else SilverChrome,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (uiState.isAeAfLocked) "AE/AF" else body.focusModeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isAeAfLocked) MatteLeather else SilverChrome,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Bottom-right Interactive Date Stamp Preview (tap to cycle among all 6 styles!)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .then(
                                when (activeDateStyle) {
                                    DateStampStyle.OFF -> Modifier
                                        .background(Color(0x880B0B0D))
                                        .border(0.8.dp, HairlineMetal.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                                    DateStampStyle.CLASSIC_WHITE -> Modifier
                                        .background(Color(0x660B0B0D))
                                    else -> Modifier
                                }
                            )
                            .clickable { onCycleDateStampStyle() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("viewfinder_date_stamp_button")
                    ) {
                        if (activeDateStyle == DateStampStyle.OFF) {
                            Text(
                                text = "DATE OFF",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
                                color = WarmEngraving
                            )
                        } else {
                            val coreColor = Color(activeDateStyle.coreColorHex)
                            val glowColor = Color(activeDateStyle.glowColorHex)
                            Text(
                                text = liveDatePreview,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = if (activeDateStyle == DateStampStyle.CLASSIC_WHITE) FontFamily.SansSerif else FontFamily.Monospace,
                                    fontSize = if (activeDateStyle == DateStampStyle.TIMECODE_CINE) 11.sp else 13.sp,
                                    letterSpacing = if (activeDateStyle == DateStampStyle.TIMECODE_CINE) 1.sp else 2.sp,
                                    fontWeight = if (activeDateStyle == DateStampStyle.CLASSIC_WHITE) FontWeight.Medium else FontWeight.Bold,
                                    shadow = Shadow(
                                        color = glowColor,
                                        offset = Offset(0f, 0f),
                                        blurRadius = if (activeDateStyle == DateStampStyle.CLASSIC_WHITE) 4f else 12f
                                    )
                                ),
                                color = coreColor
                            )
                        }
                    }

                    // Bottom-left discreet vintage batch stamp inside viewfinder
                    if (expiration.isExpired) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xAA0B0B0D))
                                .border(1.dp, KodakAmber.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = expiration.shortBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = KodakAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mechanical focal-plane shutter blackout curtain overlay
                    if (shutterFlashAlpha.value > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = shutterFlashAlpha.value))
                        )
                    }
                }
            }
        }

        // ==================== 3. TACTILE EV EXPOSURE SCALE + SIMULATED LIGHT METER ====================
        EvExposureDial(
            ev = uiState.evCompensation,
            meterReadout = "$simulatedShutterLabel • ${lens.maxAperture} • ISO ${film.iso}",
            onEvChange = onEvChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )

        // ==================== 4. MODULAR HARDWARE RACK (3 SLOTS) ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HardwareRackSlot(
                slotLabel = if (inProgressRollsCount > 1) "SAC ($inProgressRollsCount)" else "BOÎTIER",
                title = body.name,
                subtitle = "${body.mount.shortCode} • ${body.aspectRatioSpec.formatBadge}",
                accentColor = if (inProgressRollsCount > 1) KodakAmber else BrassAccent,
                testTag = "rack_body_slot",
                onClick = { onOpenBoutique(BoutiqueFocusTab.BODY) },
                modifier = Modifier.weight(1f)
            ) {
                CameraBodyThumbnail(body = body, modifier = Modifier.size(width = 58.dp, height = 40.dp))
            }

            HardwareRackSlot(
                slotLabel = "OBJECTIF",
                title = lens.shortCode,
                subtitle = if (isToyCam) "Focale fixe ∞" else lens.characterTag,
                accentColor = OpticalTeal,
                testTag = "rack_lens_slot",
                onClick = { onOpenBoutique(BoutiqueFocusTab.LENS) },
                modifier = Modifier.weight(1f)
            ) {
                LensThumbnail(lens = lens, modifier = Modifier.size(40.dp))
            }

            HardwareRackSlot(
                slotLabel = "PELLICULE",
                title = film.name,
                subtitle = "ISO ${film.iso} • ${expiration.shortBadge}",
                accentColor = if (expiration.isExpired) KodakAmber else BrassAccent,
                testTag = "rack_film_slot",
                onClick = { onOpenBoutique(BoutiqueFocusTab.FILM) },
                modifier = Modifier.weight(1f)
            ) {
                FilmCanisterThumbnail(
                    film = film,
                    expirationDate = expiration,
                    modifier = Modifier.size(width = 36.dp, height = 42.dp)
                )
            }
        }

        // ==================== 5. SHUTTER CONTROL DECK ====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .brushedMetalPlate(
                    shape = RectangleShape,
                    borderColor = Color(0xFF2B2F3B),
                    borderWidth = 0.8.dp,
                    elevated = true
                )
                .padding(horizontal = 24.dp, vertical = 11.dp)
                .navigationBarsPadding()
        ) {
            MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopStart))
            MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopEnd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Labo & Developed Rolls Gallery Button with live pending indicator
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("open_labo_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .brushedMetalPlate(
                                shape = RoundedCornerShape(14.dp),
                                borderColor = if (isRollFull) LeicaCrimson.copy(alpha = 0.85f) else HairlineMetal,
                                elevated = true
                            )
                            .clickable { onOpenLabo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (developedCount > 0) Icons.Filled.PhotoLibrary else Icons.Filled.Science,
                                contentDescription = stringResource(R.string.cd_open_labo),
                                tint = if (isRollFull) LeicaCrimson else BrassAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (developedCount > 0) "LABO ($developedCount)" else "LABO",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = SilverChrome
                            )
                        }
                    }
                    if (inProgressRollsCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isRollFull) LeicaCrimson else KodakAmber)
                                .border(1.dp, MatteLeather, CircleShape)
                        )
                    }
                }

                // Center: Large Concentric Machined Metallic Shutter Button
                val shutterScale by animateFloatAsState(
                    targetValue = if (uiState.isCapturing) 0.90f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "shutter_scale"
                )
                Box(
                    modifier = Modifier
                        .size((76 * shutterScale).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF454038), Color(0xFF1D1B18))
                            )
                        )
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                if (isRollFull) listOf(LeicaCrimson, KodakAmber)
                                else listOf(BrassAccent, SilverChrome, BrassAccent)
                            ),
                            shape = CircleShape
                        )
                        .clickable(enabled = !uiState.isCapturing) {
                            if (isRollFull) onOpenDevelopNow() else onCaptureClick()
                        }
                        .testTag("shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    if (isRollFull) listOf(KodakAmber, LeicaCrimson)
                                    else listOf(Color(0xFFEBE4D7), Color(0xFFA8A092))
                                )
                            )
                            .border(1.5.dp, MatteLeather, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRollFull) {
                            Text(
                                text = "LABO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatteLeather,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (uiState.selfTimerCountdown > 0) {
                            Text(
                                text = "${uiState.selfTimerCountdown}s",
                                style = MaterialTheme.typography.titleMedium,
                                color = MatteLeather,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(LeicaCrimson.copy(alpha = 0.85f))
                            )
                        }
                    }
                }

                // Right: Front/Rear Camera Flip OR Day/Night sample scene switch
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .brushedMetalPlate(
                            shape = RoundedCornerShape(14.dp),
                            borderColor = HairlineMetal,
                            elevated = true
                        )
                        .clickable {
                            if (useLiveCamera) onFlipCamera() else onToggleSampleNight()
                        }
                        .testTag("flip_camera_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (useLiveCamera) {
                                Icons.Filled.Cameraswitch
                            } else if (settings.sampleSceneNight) {
                                Icons.Filled.DarkMode
                            } else {
                                Icons.Filled.WbSunny
                            },
                            contentDescription = stringResource(R.string.cd_flip_camera),
                            tint = SilverChrome,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (useLiveCamera) "CAM" else if (settings.sampleSceneNight) "NUIT" else "JOUR",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = WarmEngraving
                        )
                    }
                }
            }
        }
    }

    // End-of-Roll Rewind & Development Modal
    if (uiState.showEndOfRollPrompt) {
        AlertDialog(
            onDismissRequest = onDismissEndOfRoll,
            containerColor = BrushedGunmetal,
            title = {
                Text(
                    text = "Fin de pellicule ($maxExposures/$maxExposures)",
                    style = MaterialTheme.typography.titleLarge,
                    color = FilmCream
                )
            },
            text = {
                Text(
                    text = "Votre rouleau ${film.name} (${expiration.stampCode}) est complet ! Rembobinez-le et choisissez votre bain chimique au laboratoire pour révéler la planche-contact.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverChrome
                )
            },
            confirmButton = {
                Button(
                    onClick = onOpenDevelopNow,
                    colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
                    modifier = Modifier.testTag("develop_now_dialog_button")
                ) {
                    Text("Développer au Labo", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissEndOfRoll) {
                    Text("Plus tard", color = SilverChrome)
                }
            }
        )
    }
}

private fun computeSimulatedFocusDistance(nx: Float, ny: Float, focalMm: Int): String {
    // Upper portion of frame feels further toward infinity; lower/foreground feels closer
    val radial = ((1f - ny) * 0.72f + abs(nx - 0.5f) * 0.28f).coerceIn(0f, 1f)
    return when {
        radial > 0.82f -> "∞"
        radial > 0.65f -> "5.0m"
        radial > 0.48f -> if (focalMm >= 75) "3.2m" else "2.4m"
        radial > 0.30f -> "1.5m"
        else -> "0.8m"
    }
}

@Composable
private fun HudChip(
    text: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xAA100F0E))
            .border(1.dp, HairlineMetal, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Tactile horizontal slideable & tappable Exposure Compensation Scale (-2.0 to +2.0 IL)
 * paired with a live simulated mechanical light meter readout.
 */
@Composable
private fun EvExposureDial(
    ev: Float,
    meterReadout: String,
    onEvChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragEv by remember(ev) { mutableFloatStateOf(ev) }

    Column(
        modifier = modifier
            .recessedInstrumentWell(shape = RoundedCornerShape(10.dp), borderColor = HairlineMetal)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    dragEv = (dragEv + dragAmount / 110f).coerceIn(-2.0f, 2.0f)
                    onEvChange(dragEv)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val tappedEv = (frac * 4f - 2f).coerceIn(-2.0f, 2.0f)
                    dragEv = tappedEv
                    onEvChange(tappedEv)
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("ev_exposure_dial")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = meterReadout,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                color = WarmEngraving,
                maxLines = 1
            )
            Text(
                text = if (ev == 0f) "EV 0.0 IL" else "EV %+.1f IL ↺".format(ev),
                style = MaterialTheme.typography.labelMedium,
                color = if (ev == 0f) SilverChrome else KodakAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = ev != 0f) { onEvChange(0f) }
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val steps = 12 // -2..+2 in 1/3 stops
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * size.width
                val isMajor = i % 3 == 0
                val top = if (isMajor) 2f else 5f
                drawLine(
                    color = if (i == 6) BrassAccent else WarmEngraving.copy(alpha = 0.55f),
                    start = Offset(x, top),
                    end = Offset(x, size.height - 2f),
                    strokeWidth = if (isMajor) 2f else 1.2f
                )
            }
            val needleX = ((ev + 2f) / 4f).coerceIn(0f, 1f) * size.width
            drawLine(
                color = KodakAmber,
                start = Offset(needleX, 0f),
                end = Offset(needleX, size.height),
                strokeWidth = 3.5f
            )
        }
    }
}

@Composable
private fun HardwareRackSlot(
    slotLabel: String,
    title: String,
    subtitle: String,
    testTag: String,
    accentColor: Color = BrassAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .brushedMetalPlate(
                shape = RoundedCornerShape(12.dp),
                borderColor = if (accentColor != BrassAccent) accentColor.copy(alpha = 0.48f) else HairlineMetal,
                elevated = true
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = slotLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = accentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        thumbnail()
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = FilmCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = WarmEngraving,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
