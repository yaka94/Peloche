package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.CameraSettings
import com.example.data.local.FilmRollEntity
import com.example.domain.model.CameraFlashMode
import com.example.domain.model.DateStampStyle
import com.example.domain.model.LensMount
import com.example.domain.model.VideoFilterStyle
import com.example.engine.CameraSurfaceHolder
import com.example.ui.components.AnalogFrameContour
import com.example.ui.components.CameraBodyThumbnail
import com.example.ui.components.CineConsoleDeck
import com.example.ui.components.CineFormatsBottomSheet
import com.example.ui.components.FilmCanisterThumbnail
import com.example.ui.components.LensThumbnail
import com.example.ui.components.LiveOpticalPreviewSurface
import com.example.ui.components.MachinedScrewHead
import com.example.ui.components.PhotoCineModeSwitch
import com.example.ui.components.Super8RecorderHandle
import com.example.ui.components.blackLeatherTexture
import java.io.File
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
    onToggleRecordVideo: () -> Unit,
    onToggleCineVideoMode: () -> Unit,
    onSelectVideoFilter: (VideoFilterStyle) -> Unit,
    onTiltShiftPositionChange: (Float) -> Unit,
    onToggleTiltShiftOrientation: () -> Unit,
    onToggleInvertedGroundGlass: () -> Unit,
    onTogglePureViewfinderMode: () -> Unit,
    onToggleDoubleExposure: () -> Unit,
    onCancelPendingDoubleExposure: () -> Unit,
    onRerollOpticalEffects: () -> Unit,
    recorderHandle: Super8RecorderHandle,
    onCycleDateStampStyle: () -> Unit,
    onCycleSelfTimer: () -> Unit,
    onCycleFlashMode: () -> Unit,
    onImportGalleryPhoto: () -> Unit,
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
    var showCineFormatsSheet by remember { mutableStateOf(false) }
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

    val rollProgress = (shotsTaken.toFloat() / maxExposures.coerceAtLeast(1)).coerceIn(0f, 1f)
    val recProgress by animateFloatAsState(
        targetValue = if (uiState.isRecordingVideo) {
            (uiState.recordingElapsedSec / 15f).coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 450),
        label = "cine_rec_progress"
    )
    val shutterScale by animateFloatAsState(
        targetValue = if (uiState.isCapturing) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "shutter_scale"
    )

    // Shared Live Framed Viewfinder Stage (exact per-body aspect ratio + physical contour)
    val viewfinderStage: @Composable (Modifier) -> Unit = { stageModifier ->
        Box(
            modifier = stageModifier,
            contentAlignment = Alignment.Center
        ) {
            if (!hasCameraPermission && !uiState.useSampleSceneInViewfinder) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brushedMetalPlate(shape = RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = BrassAccent,
                        modifier = Modifier.size(36.dp)
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
                AnalogFrameContour(
                    body = body,
                    film = film,
                    frameNumber = (shotsTaken + 1).coerceAtMost(maxExposures),
                    borderEnabled = settings.frameBorderEnabled,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                            animatedEffectsEnabled = settings.animatedEffectsEnabled,
                            recorderHandle = recorderHandle,
                            onSurfaceTextureReady = onSurfaceTextureReady,
                            onSurfaceTextureDestroyed = onSurfaceTextureDestroyed,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Double Exposure translucent ghost of 1st shot for optical alignment
                        uiState.doubleExposureFirstShotPath?.let { firstPath ->
                            val ghostFile = remember(firstPath) { File(firstPath) }
                            if (ghostFile.exists()) {
                                AsyncImage(
                                    model = ghostFile,
                                    contentDescription = "Fantôme 1re exposition",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(0.42f)
                                        .testTag("double_exposure_ghost_overlay")
                                )
                            }
                        }

                        // Clean Cinema Recording crimson frame border & sleek bottom-edge 15s progress bar
                        // (Timecode lives cleanly in the top bar drum + circular ring around shutter — zero HUD overlap!)
                        if (uiState.isRecordingVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, LeicaCrimson.copy(alpha = 0.85f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color(0x66000000))
                                    .testTag("super8_rec_badge")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(recProgress.coerceAtLeast(0.03f))
                                        .background(LeicaCrimson)
                                )
                            }
                        }

                        // Etched Ground-Glass Brightline & Split-Prism Reticle (razor-thin & understated)
                        if (settings.gridEnabled) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val hairline = FilmCream.copy(alpha = 0.13f)
                                val brightline = BrassAccent.copy(alpha = 0.34f)
                                val thirdW = size.width / 3f
                                val thirdH = size.height / 3f
                                drawLine(hairline, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 0.8f)
                                drawLine(hairline, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 0.8f)
                                drawLine(hairline, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 0.8f)
                                drawLine(hairline, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 0.8f)

                                if (!isToyCam) {
                                    val pad = 16f
                                    val arm = 18f
                                    val sw = 1.2f
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
                                    drawCircle(FilmCream.copy(alpha = 0.18f), radius = 20f, center = Offset(cx, cy), style = Stroke(0.8f))
                                    drawCircle(BrassAccent.copy(alpha = 0.30f), radius = 10f, center = Offset(cx, cy), style = Stroke(0.8f))
                                    drawLine(BrassAccent.copy(alpha = 0.28f), Offset(cx - 10f, cy), Offset(cx + 10f, cy), strokeWidth = 0.8f)
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

                    // Pure Viewfinder (Zen) Mode: hide HUD clutter, leave only a discreet restore icon
                    if (uiState.isPureViewfinderMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color(0x660B0C10))
                                .border(0.8.dp, HairlineMetal.copy(alpha = 0.45f), CircleShape)
                                .clickable { onTogglePureViewfinderMode() }
                                .padding(7.dp)
                                .testTag("pure_viewfinder_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = "Afficher les indicateurs du viseur",
                                tint = SilverChrome.copy(alpha = 0.78f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                    // Reference-Style Decluttered Top Viewfinder Overlay:
                    // Discreet DATE pill (top-left) + Translucent Engraved EV Scale (top-center) + AE/AF & Zen toggle (top-right)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x8807080B), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discreet DATE pill (tap to cycle all 6 authentic date back styles)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (activeDateStyle != DateStampStyle.OFF) KodakAmber.copy(alpha = 0.22f)
                                    else Color(0x880B0C10)
                                )
                                .border(
                                    0.8.dp,
                                    if (activeDateStyle != DateStampStyle.OFF) KodakAmber.copy(alpha = 0.72f)
                                    else HairlineMetal.copy(alpha = 0.55f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onCycleDateStampStyle() }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("viewfinder_date_stamp_button")
                        ) {
                            Text(
                                text = if (activeDateStyle == DateStampStyle.OFF) "DATE" else "DATE •",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                                color = if (activeDateStyle != DateStampStyle.OFF) KodakAmber else SilverChrome.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Translucent Engraved EV Exposure Scale right inside the top of the viewfinder!
                        InViewfinderEvScale(
                            ev = uiState.evCompensation,
                            onEvChange = onEvChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isToyCam) {
                                HudChip(
                                    text = "FIXE ∞",
                                    accent = KodakAmber,
                                    testTag = "toy_fixed_focus_badge"
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (uiState.isAeAfLocked) KodakAmber.copy(alpha = 0.88f)
                                            else Color(0x880B0C10)
                                        )
                                        .border(
                                            0.8.dp,
                                            if (uiState.isAeAfLocked) KodakAmber else HairlineMetal.copy(alpha = 0.55f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onToggleAeAfLock() }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                        .testTag("ae_af_lock_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isAeAfLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                        contentDescription = "Verrouillage AE/AF",
                                        tint = if (uiState.isAeAfLocked) MatteLeather else SilverChrome,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (uiState.isAeAfLocked) "AE/AF" else body.focusModeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (uiState.isAeAfLocked) MatteLeather else SilverChrome,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Pure Viewfinder (Zen Mode) eye button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x880B0C10))
                                    .border(0.8.dp, HairlineMetal.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { onTogglePureViewfinderMode() }
                                    .padding(horizontal = 5.dp, vertical = 4.dp)
                                    .testTag("pure_viewfinder_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = "Mode viseur pur sans HUD",
                                    tint = SilverChrome.copy(alpha = 0.85f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Bottom-left compact Focal + Meter + Vintage Batch + Ground Glass readout
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val effectiveMm = (lens.focalLengthMm * uiState.zoomMultiplier).roundToInt()
                        HudChip(
                            text = if (isToyCam) "${lens.focalLengthMm}mm" else "${effectiveMm}mm • $simulatedShutterLabel",
                            accent = SilverChrome,
                            onClick = if (isToyCam) null else onZoomStep,
                            testTag = "focal_zoom_chip"
                        )
                        // 1-tap Re-seed procedural optical effects (light leaks, flares, plastic scratches)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x990B0C10))
                                .border(0.8.dp, BrassAccent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .clickable { onRerollOpticalEffects() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("reroll_effects_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Casino,
                                contentDescription = "Nouveau tirage d'effet optique",
                                tint = BrassAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        // Authentic 19th-century Inverted Ground Glass 180° toggle for large-format wooden cameras
                        if (body.isHistoricalLargeFormat && !uiState.isRecordingVideo) {
                            HudChip(
                                text = if (uiState.invertedGroundGlass) "DÉPOLI 180°" else "DÉPOLI ↕",
                                accent = if (uiState.invertedGroundGlass) KodakAmber else BrassAccent,
                                onClick = onToggleInvertedGroundGlass,
                                testTag = "ground_glass_invert_toggle"
                            )
                        }
                        // Double Exposure (2×) toggle pill (hidden during active Cine video mode)
                        if (!uiState.isCineVideoMode) {
                            val waitingSecond = uiState.doubleExposureFirstShotPath != null
                            HudChip(
                                text = when {
                                    waitingSecond -> "2× 2/2"
                                    uiState.isDoubleExposureMode -> "2× ON"
                                    else -> "2×"
                                },
                                accent = when {
                                    waitingSecond -> LeicaCrimson
                                    uiState.isDoubleExposureMode -> KodakAmber
                                    else -> SilverChrome
                                },
                                onClick = onToggleDoubleExposure,
                                testTag = "double_exposure_toggle"
                            )
                            if (waitingSecond) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LeicaCrimson.copy(alpha = 0.85f))
                                        .clickable { onCancelPendingDoubleExposure() }
                                        .padding(horizontal = 5.dp, vertical = 4.dp)
                                        .testTag("cancel_double_exposure_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Annuler la 1re exposition",
                                        tint = FilmCream,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        if (expiration.isExpired && !uiState.isDoubleExposureMode && !uiState.isCineVideoMode && !body.isHistoricalLargeFormat) {
                            HudChip(
                                text = expiration.shortBadge,
                                accent = KodakAmber
                            )
                        }
                    }
                    }

                    // Bottom-right Authentic Glowing Date Back Imprint (when active)
                    if (activeDateStyle != DateStampStyle.OFF) {
                        val coreColor = Color(activeDateStyle.coreColorHex)
                        val glowColor = Color(activeDateStyle.glowColorHex)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .then(
                                    if (activeDateStyle == DateStampStyle.CLASSIC_WHITE) {
                                        Modifier.background(Color(0x660B0B0D))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onCycleDateStampStyle() }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
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

                    // Developing Imported Photo subtle overlay
                    if (uiState.isImportingPhoto) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x990A0B0E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = BrassAccent, strokeWidth = 2.5.dp)
                                Text(
                                    text = "Développement argentique…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FilmCream
                                )
                            }
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
        }
    }

    // Shared Machined Shutter / Super 8 REC Button
    val shutterButton: @Composable (Int) -> Unit = { diameterDp ->
        val isCine = uiState.isCineVideoMode
        val isRec = uiState.isRecordingVideo
        val waitingDoubleExp = uiState.isDoubleExposureMode && uiState.doubleExposureFirstShotPath != null
        Box(
            modifier = Modifier
                .size((diameterDp * shutterScale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF454038), Color(0xFF1D1B18))
                    )
                )
                .border(
                    width = if (isCine && isRec) 2.dp else 3.dp,
                    brush = Brush.linearGradient(
                        when {
                            isCine && isRec -> listOf(Color(0xFF452024), Color(0xFF281214))
                            isCine -> listOf(LeicaCrimson, KodakAmber, LeicaCrimson)
                            isRollFull -> listOf(LeicaCrimson, KodakAmber)
                            waitingDoubleExp -> listOf(KodakAmber, LeicaCrimson)
                            else -> listOf(BrassAccent, SilverChrome, BrassAccent)
                        }
                    ),
                    shape = CircleShape
                )
                .clickable(enabled = !uiState.isCapturing) {
                    when {
                        isCine -> onToggleRecordVideo()
                        isRollFull -> onOpenDevelopNow()
                        else -> onCaptureClick()
                    }
                }
                .testTag("shutter_button"),
            contentAlignment = Alignment.Center
        ) {
            // Circular 15-second Cinema REC progress arc drawn directly on the machined rim
            if (isCine && isRec) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    val strokePx = 3.5.dp.toPx()
                    drawArc(
                        color = Color(0x38FFFFFF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = LeicaCrimson,
                        startAngle = -90f,
                        sweepAngle = (recProgress * 360f).coerceAtLeast(10f),
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size((diameterDp - 20).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            when {
                                isCine -> listOf(Color(0xFF2B1316), Color(0xFF15080A))
                                isRollFull -> listOf(KodakAmber, LeicaCrimson)
                                else -> listOf(Color(0xFFEBE4D7), Color(0xFFA8A092))
                            }
                        )
                    )
                    .border(1.5.dp, MatteLeather, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCine && isRec -> {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(LeicaCrimson)
                        )
                    }
                    isCine -> {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(LeicaCrimson)
                        )
                    }
                    isRollFull -> {
                        Text(
                            text = "LABO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatteLeather,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    uiState.selfTimerCountdown > 0 -> {
                        Text(
                            text = "${uiState.selfTimerCountdown}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = MatteLeather,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    waitingDoubleExp -> {
                        Text(
                            text = "2/2",
                            style = MaterialTheme.typography.labelMedium,
                            color = LeicaCrimson,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(LeicaCrimson.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }

    // Shared Labo Button
    val laboButton: @Composable (Int) -> Unit = { sizeDp ->
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (developedCount > 0) "LABO ($developedCount)" else "LABO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = SilverChrome,
                        maxLines = 1
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
    }

    // Shared Camera Flip / Day-Night Switch Button
    val flipCamButton: @Composable (Int) -> Unit = { sizeDp ->
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
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
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (useLiveCamera) "CAM" else if (settings.sampleSceneNight) "NUIT" else "JOUR",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = WarmEngraving
                )
            }
        }
    }

    // ==================== ADAPTIVE PORTRAIT / LANDSCAPE CAMERA BODY ====================
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .blackLeatherTexture()
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // ==================== LANDSCAPE ERGONOMIC CAMERA-BACK LAYOUT ====================
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT MACHINED VERTICAL RAIL (Settings, Counter Drum, Self-Timer, Brand/Sac)
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .brushedMetalPlate(
                            shape = RectangleShape,
                            borderColor = Color(0xFF2B2F3B),
                            borderWidth = 0.8.dp,
                            elevated = true
                        )
                        .displayCutoutPadding()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                ) {
                    MachinedScrewHead(size = 6.dp, modifier = Modifier.align(Alignment.TopStart))
                    MachinedScrewHead(size = 6.dp, modifier = Modifier.align(Alignment.BottomStart))

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Settings button
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

                        // Compact Context-Aware Counter Drum (Photo 00/36 vs Ciné 00:00 / 00:15)
                        Column(
                            modifier = Modifier
                                .recessedInstrumentWell(
                                    shape = RoundedCornerShape(9.dp),
                                    borderColor = when {
                                        uiState.isRecordingVideo -> LeicaCrimson
                                        !uiState.isCineVideoMode && isRollFull -> LeicaCrimson.copy(alpha = 0.85f)
                                        else -> HairlineMetal
                                    }
                                )
                                .clickable { onOpenLabo() }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                .testTag("frame_counter_badge"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (uiState.isCineVideoMode) {
                                Text(
                                    text = "00:%02d".format(uiState.recordingElapsedSec),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (uiState.isRecordingVideo) LeicaCrimson else FilmCream
                                )
                                Text(
                                    text = "/00:15",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = WarmEngraving
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(AnodizedSlate)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(recProgress)
                                            .background(LeicaCrimson)
                                    )
                                }
                            } else {
                                Text(
                                    text = "%02d".format(shotsTaken),
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                                    color = if (isRollFull) LeicaCrimson else FilmCream
                                )
                                Text(
                                    text = "/$maxExposures",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = WarmEngraving
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
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
                        }

                        // Self-timer button (Photo mode only)
                        if (!uiState.isCineVideoMode) {
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
                                        modifier = Modifier.size(16.dp)
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

                        // Hardware Flash LED button (OFF / AUTO / ON)
                        FlashHardwareButton(
                            flashMode = uiState.flashMode,
                            onClick = onCycleFlashMode
                        )

                        // Context-aware bottom left-rail action: Photo Import vs Ciné Formats Catalog
                        if (uiState.isCineVideoMode) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .brushedMetalPlate(
                                        shape = CircleShape,
                                        borderColor = BrassAccent.copy(alpha = 0.6f),
                                        elevated = true
                                    )
                                    .clickable(enabled = !uiState.isRecordingVideo) { showCineFormatsSheet = true }
                                    .testTag("cine_all_formats_rail_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GridView,
                                    contentDescription = "Tous les formats vidéo",
                                    tint = BrassAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .brushedMetalPlate(shape = CircleShape, borderColor = HairlineMetal, elevated = true)
                                    .clickable { onImportGalleryPhoto() }
                                    .testTag("import_gallery_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Importer une photo de la galerie",
                                    tint = SilverChrome,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // CENTER OPTICAL VIEWFINDER + UNIFIED CONTEXTUAL CONSOLE (No Stacking!)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    viewfinderStage(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    AnimatedContent(
                        targetState = uiState.isCineVideoMode,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "landscape_bottom_console"
                    ) { isCine ->
                        if (isCine) {
                            CineConsoleDeck(
                                selected = uiState.selectedVideoFilter,
                                lensShortCode = lens.shortCode,
                                aspectBadge = body.aspectRatioSpec.formatBadge,
                                enabled = !uiState.isRecordingVideo,
                                compact = true,
                                onSelect = onSelectVideoFilter,
                                onOpenOpticalGear = { onOpenBoutique(BoutiqueFocusTab.LENS) },
                                onOpenAllFormatsSheet = { showCineFormatsSheet = true }
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (lens.tiltShiftAmount > 0.05f) {
                                    TiltShiftPlaneMiniBar(
                                        position = uiState.tiltShiftPosition,
                                        isVertical = uiState.tiltShiftVertical,
                                        onPositionChange = onTiltShiftPositionChange,
                                        onToggleOrientation = onToggleTiltShiftOrientation
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LandscapeGearPill(
                                        label = if (inProgressRollsCount > 1) "SAC ($inProgressRollsCount)" else "BOÎTIER",
                                        value = body.shortCode,
                                        accent = if (inProgressRollsCount > 1) KodakAmber else SilverChrome,
                                        testTag = "rack_body_slot",
                                        onClick = { onOpenBoutique(BoutiqueFocusTab.BODY) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LandscapeGearPill(
                                        label = "OPTIQUE",
                                        value = lens.shortCode,
                                        accent = SilverChrome,
                                        testTag = "rack_lens_slot",
                                        onClick = { onOpenBoutique(BoutiqueFocusTab.LENS) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LandscapeGearPill(
                                        label = "FILM",
                                        value = film.name,
                                        accent = if (expiration.isExpired) KodakAmber else BrassAccent,
                                        testTag = "rack_film_slot",
                                        onClick = { onOpenBoutique(BoutiqueFocusTab.FILM) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT MACHINED VERTICAL SHUTTER GRIP (Flip Cam, Shutter, Labo)
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .brushedMetalPlate(
                            shape = RectangleShape,
                            borderColor = Color(0xFF2B2F3B),
                            borderWidth = 0.8.dp,
                            elevated = true
                        )
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    MachinedScrewHead(size = 6.dp, modifier = Modifier.align(Alignment.TopEnd))
                    MachinedScrewHead(size = 6.dp, modifier = Modifier.align(Alignment.BottomEnd))

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        flipCamButton(48)
                        // Super 8 Cinema toggle on landscape shutter rail
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .brushedMetalPlate(
                                    shape = RoundedCornerShape(13.dp),
                                    borderColor = if (uiState.isCineVideoMode) LeicaCrimson else HairlineMetal,
                                    elevated = true
                                )
                                .clickable(enabled = !uiState.isRecordingVideo) { onToggleCineVideoMode() }
                                .testTag("cine_mode_toggle_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = "Mode Ciné & Vidéo Rétro",
                                    tint = if (uiState.isCineVideoMode) LeicaCrimson else SilverChrome,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = if (uiState.isCineVideoMode) "PHOTO" else "CINÉ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                    color = if (uiState.isCineVideoMode) LeicaCrimson else WarmEngraving,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        shutterButton(68)
                        laboButton(48)
                    }
                }
            }
        } else {
            // ==================== PORTRAIT AUTHENTIC CAMERA-BACK LAYOUT ====================
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. AUTHENTIC CAMERA-BACK TOP PLATE (Retro Frame Counter Drum • Optical Eyepiece • Knurled Dial + Flash LED)
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
                        // LEFT: Context-Aware Counter Drum (Photo 00/36 Mechanical Drum vs Ciné Timecode 00:00 / 00:15)
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onOpenLabo() }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .testTag("frame_counter_badge"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (uiState.isCineVideoMode) {
                                val isRec = uiState.isRecordingVideo
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1A1B1F))
                                        .border(
                                            1.2.dp,
                                            if (isRec) LeicaCrimson else Color(0xFF3A3D46),
                                            RoundedCornerShape(7.dp)
                                        )
                                        .padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isRec) LeicaCrimson else Color(0xFF23242C))
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isRec) Color.White else LeicaCrimson.copy(alpha = 0.75f))
                                        )
                                        Text(
                                            text = "00:%02d".format(uiState.recordingElapsedSec),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = if (isRec) Color.White else FilmCream
                                        )
                                    }
                                    Text(
                                        text = "/00:15",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = WarmEngraving,
                                        modifier = Modifier.padding(end = 3.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(86.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(AnodizedSlate)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(recProgress)
                                            .background(LeicaCrimson)
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF1A1B1F))
                                        .border(1.2.dp, if (isRollFull) LeicaCrimson else Color(0xFF3A3D46), RoundedCornerShape(7.dp))
                                        .padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Warm ivory mechanical counter window
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isRollFull) LeicaCrimson else Color(0xFFEBE4D4))
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = shotsTaken,
                                            transitionSpec = {
                                                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                                            },
                                            label = "counter_drum_flip"
                                        ) { count ->
                                            Text(
                                                text = "%02d".format(count),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                color = if (isRollFull) Color.White else Color(0xFF141311)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "/$maxExposures",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = WarmEngraving,
                                        modifier = Modifier.padding(end = 3.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(68.dp)
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
                        }

                        // CENTER: Tactile Optical Viewfinder Eyepiece (taps → Settings) + Engraved Peloche. Wordmark
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            OpticalEyepieceButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("settings_button")
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenBoutique(BoutiqueFocusTab.BODY) }
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .testTag("peloche_wordmark_bag_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Peloche",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        letterSpacing = 1.4.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = SilverChrome.copy(alpha = 0.85f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(BrassAccent)
                                )
                                if (inProgressRollsCount > 1) {
                                    Text(
                                        text = "×$inProgressRollsCount",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = KodakAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // RIGHT: Knurled Self-Timer Dial (Photo only) + Hardware Flash LED Button
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!uiState.isCineVideoMode) {
                                // Machined Knurled Self-Timer Dial (OFF / 3s / 10s)
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
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val c = Offset(size.width / 2f, size.height / 2f)
                                        val r = size.minDimension / 2f - 2f
                                        for (i in 0 until 20) {
                                            val a = (i / 20.0) * Math.PI * 2.0
                                            drawLine(
                                                color = SilverChrome.copy(alpha = if (i % 2 == 0) 0.30f else 0.12f),
                                                start = Offset(c.x + kotlin.math.cos(a).toFloat() * (r - 3.5f), c.y + kotlin.math.sin(a).toFloat() * (r - 3.5f)),
                                                end = Offset(c.x + kotlin.math.cos(a).toFloat() * r, c.y + kotlin.math.sin(a).toFloat() * r),
                                                strokeWidth = 1.1f
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.Timer,
                                            contentDescription = "Retardateur",
                                            tint = if (settings.selfTimerSeconds > 0) BrassAccent else SilverChrome,
                                            modifier = Modifier.size(15.dp)
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

                            // Hardware Flash LED Button (OFF / AUTO / ON)
                            FlashHardwareButton(
                                flashMode = uiState.flashMode,
                                onClick = onCycleFlashMode
                            )
                        }
                    }
                }

                // 2. DECLUTTERED, TALLER CENTRAL LIVE FRAMED VIEWFINDER
                viewfinderStage(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )

                // 3. UNIFIED CONTEXTUAL HARDWARE CONSOLE:
                //    - CINÉ mode: Seamless CineConsoleDeck (9 tactile cartridges/cassettes/reels) — NO vertical stacking!
                //    - PHOTO mode: Optional Tilt-Shift bar + 3-Slot Hardware Rack (BOÎTIER • OBJECTIF • PELLICULE)
                AnimatedContent(
                    targetState = uiState.isCineVideoMode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "portrait_hardware_console"
                ) { isCine ->
                    if (isCine) {
                        CineConsoleDeck(
                            selected = uiState.selectedVideoFilter,
                            lensShortCode = lens.shortCode,
                            aspectBadge = body.aspectRatioSpec.formatBadge,
                            enabled = !uiState.isRecordingVideo,
                            compact = false,
                            onSelect = onSelectVideoFilter,
                            onOpenOpticalGear = { onOpenBoutique(BoutiqueFocusTab.LENS) },
                            onOpenAllFormatsSheet = { showCineFormatsSheet = true },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (lens.tiltShiftAmount > 0.05f) {
                                TiltShiftPlaneMiniBar(
                                    position = uiState.tiltShiftPosition,
                                    isVertical = uiState.tiltShiftVertical,
                                    onPositionChange = onTiltShiftPositionChange,
                                    onToggleOrientation = onToggleTiltShiftOrientation,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HardwareRackSlot(
                                    slotLabel = if (inProgressRollsCount > 1) "SAC ($inProgressRollsCount)" else "BOÎTIER",
                                    title = body.name,
                                    subtitle = body.aspectRatioSpec.formatBadge,
                                    titleColor = FilmCream,
                                    testTag = "rack_body_slot",
                                    onClick = { onOpenBoutique(BoutiqueFocusTab.BODY) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CameraBodyThumbnail(body = body, modifier = Modifier.size(width = 62.dp, height = 42.dp))
                                }

                                HardwareRackSlot(
                                    slotLabel = "OBJECTIF",
                                    title = lens.shortCode,
                                    subtitle = if (isToyCam) "Focale fixe ∞" else lens.characterTag,
                                    titleColor = FilmCream,
                                    testTag = "rack_lens_slot",
                                    onClick = { onOpenBoutique(BoutiqueFocusTab.LENS) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    LensThumbnail(lens = lens, modifier = Modifier.size(42.dp))
                                }

                                HardwareRackSlot(
                                    slotLabel = "PELLICULE",
                                    title = film.name,
                                    subtitle = "ISO ${film.iso} • ${expiration.shortBadge}",
                                    titleColor = if (expiration.isExpired) KodakAmber else BrassAccent,
                                    testTag = "rack_film_slot",
                                    onClick = { onOpenBoutique(BoutiqueFocusTab.FILM) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    FilmCanisterThumbnail(
                                        film = film,
                                        expirationDate = expiration,
                                        modifier = Modifier.size(width = 38.dp, height = 42.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. BALANCED SHUTTER CONTROL DECK (Segmented PHOTO | CINÉ Switch + Dead-Center Shutter)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brushedMetalPlate(
                            shape = RectangleShape,
                            borderColor = Color(0xFF2B2F3B),
                            borderWidth = 0.8.dp,
                            elevated = true
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopStart))
                    MachinedScrewHead(size = 7.dp, modifier = Modifier.align(Alignment.TopEnd))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top-level machined PHOTO | CINÉ segmented mode selector
                        PhotoCineModeSwitch(
                            isCineMode = uiState.isCineVideoMode,
                            enabled = !uiState.isRecordingVideo,
                            onSelectMode = { wantCine ->
                                if (wantCine != uiState.isCineVideoMode) {
                                    onToggleCineVideoMode()
                                }
                            }
                        )

                        // Mathematically dead-centered shutter row
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                laboButton(54)
                                if (!uiState.isCineVideoMode) {
                                    // Zero-permission Gallery Import button (Photo mode only)
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .brushedMetalPlate(
                                                shape = RoundedCornerShape(13.dp),
                                                borderColor = HairlineMetal,
                                                elevated = true
                                            )
                                            .clickable { onImportGalleryPhoto() }
                                            .testTag("import_gallery_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Filled.AddPhotoAlternate,
                                                contentDescription = "Importer une photo de la galerie",
                                                tint = SilverChrome,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "IMPORT",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                color = WarmEngraving
                                            )
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.align(Alignment.Center)) {
                                shutterButton(74)
                            }

                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                flipCamButton(54)
                            }
                        }
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

    // 2-Column Cinema & Vintage Video Formats Catalog Sheet ("TOUS (9)")
    if (showCineFormatsSheet) {
        CineFormatsBottomSheet(
            selected = uiState.selectedVideoFilter,
            activeBodyName = body.name,
            activeFilmName = film.name,
            onSelect = onSelectVideoFilter,
            onDismiss = { showCineFormatsSheet = false }
        )
    }
}

@Composable
private fun TiltShiftPlaneMiniBar(
    position: Float,
    isVertical: Boolean,
    onPositionChange: (Float) -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .recessedInstrumentWell(shape = RoundedCornerShape(10.dp), borderColor = BrassAccent.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .testTag("tilt_shift_control_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "TILT",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            color = BrassAccent,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = position,
            onValueChange = onPositionChange,
            valueRange = 0.15f..0.85f,
            colors = SliderDefaults.colors(
                thumbColor = BrassAccent,
                activeTrackColor = BrassAccent,
                inactiveTrackColor = HairlineMetal
            ),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .testTag("tilt_shift_position_slider")
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(AnodizedSlate)
                .border(0.9.dp, BrassAccent.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                .clickable { onToggleOrientation() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("tilt_shift_orientation_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = "Basculer axe Tilt-Shift",
                tint = BrassAccent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isVertical) "VERT" else "HORIZ",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = FilmCream,
                fontWeight = FontWeight.Bold
            )
        }
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
 * Sleek translucent engraved EV Exposure Scale (-2..+2 IL) floating directly inside the top of
 * the live viewfinder (inspired by rangefinder LED/etched scales), freeing vertical height.
 */
@Composable
private fun InViewfinderEvScale(
    ev: Float,
    onEvChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragEv by remember(ev) { mutableFloatStateOf(ev) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x55090A0E))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    dragEv = (dragEv + dragAmount / 85f).coerceIn(-2.0f, 2.0f)
                    onEvChange(dragEv)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onEvChange(0f) },
                    onTap = { offset ->
                        val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val tappedEv = (frac * 4f - 2f).coerceIn(-2.0f, 2.0f)
                        dragEv = tappedEv
                        onEvChange(tappedEv)
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("ev_exposure_dial"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("-2", "-1", "0", "+1", "+2").forEach { mark ->
                Text(
                    text = mark,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (mark == "0") FilmCream.copy(alpha = 0.85f) else FilmCream.copy(alpha = 0.50f)
                )
            }
            if (ev != 0f) {
                Text(
                    text = "%+.1f".format(ev),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = KodakAmber,
                    modifier = Modifier
                        .clickable { onEvChange(0f) }
                        .padding(start = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val steps = 12
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * size.width
                val isMajor = i % 3 == 0
                drawLine(
                    color = if (i == 6) FilmCream.copy(alpha = 0.85f) else FilmCream.copy(alpha = 0.35f),
                    start = Offset(x, if (isMajor) 1f else 3f),
                    end = Offset(x, size.height - 1f),
                    strokeWidth = if (isMajor) 1.5f else 0.9f
                )
            }
            val needleX = ((ev + 2f) / 4f).coerceIn(0f, 1f) * size.width
            drawLine(
                color = if (ev == 0f) BrassAccent else KodakAmber,
                start = Offset(needleX, 0f),
                end = Offset(needleX, size.height),
                strokeWidth = 2.8f
            )
        }
    }
}

/**
 * Tactile recessed optical viewfinder eyepiece glass window on the camera back top plate.
 */
@Composable
private fun OpticalEyepieceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0B0E))
            .border(1.5.dp, Color(0xFF3A3E4A), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1F384A), Color(0xFF0C151D), Color(0xFF050709)),
                    center = Offset(size.width * 0.38f, size.height * 0.35f),
                    radius = size.maxDimension * 0.85f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            // Subtle anti-reflective sapphire optical coating glint
            drawLine(
                color = Color(0x558BD8FF),
                start = Offset(size.width * 0.16f, size.height * 0.25f),
                end = Offset(size.width * 0.44f, size.height * 0.25f),
                strokeWidth = 2f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = 2.2f,
                center = Offset(size.width * 0.72f, size.height * 0.65f)
            )
        }
    }
}

/**
 * Tactile Hardware Flash LED toggle button (OFF / AUTO / ON) with physical status LED dot.
 */
@Composable
private fun FlashHardwareButton(
    flashMode: CameraFlashMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = flashMode != CameraFlashMode.OFF
    val ledColor = when (flashMode) {
        CameraFlashMode.OFF -> Color(0xFF343844)
        CameraFlashMode.AUTO -> BrassAccent
        CameraFlashMode.ON -> KodakAmber
    }
    Box(
        modifier = modifier
            .size(46.dp)
            .brushedMetalPlate(
                shape = CircleShape,
                borderColor = if (isActive) ledColor else HairlineMetal,
                elevated = true
            )
            .clickable { onClick() }
            .testTag("flash_mode_button"),
        contentAlignment = Alignment.Center
    ) {
        // Tiny glowing hardware ready LED at top rim
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(ledColor)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                imageVector = when (flashMode) {
                    CameraFlashMode.OFF -> Icons.Filled.FlashOff
                    CameraFlashMode.AUTO -> Icons.Filled.FlashAuto
                    CameraFlashMode.ON -> Icons.Filled.FlashOn
                },
                contentDescription = "Flash : ${flashMode.labelFr}",
                tint = if (isActive) ledColor else SilverChrome,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = flashMode.shortBadge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                color = if (isActive) ledColor else WarmEngraving,
                fontWeight = FontWeight.Bold
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
    titleColor: Color = FilmCream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .brushedMetalPlate(
                shape = RoundedCornerShape(12.dp),
                borderColor = Color(0xFF282C36),
                elevated = true
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = slotLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, letterSpacing = 1.0.sp),
            color = WarmEngraving,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        thumbnail()
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
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

@Composable
private fun LandscapeGearPill(
    label: String,
    value: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .brushedMetalPlate(
                shape = RoundedCornerShape(10.dp),
                borderColor = accent.copy(alpha = 0.55f),
                elevated = true
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 7.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = FilmCream,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
