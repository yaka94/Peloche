package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.CameraSurfaceHolder
import com.example.engine.CameraXController
import com.example.ui.components.InstantGalleryPrintDialog
import com.example.ui.components.OpticalDiagnosticBottomSheet
import com.example.ui.components.Super8ClipPreviewDialog
import com.example.ui.components.Super8RecorderHandle
import com.example.ui.screens.BoutiqueScreen
import com.example.ui.screens.ContactSheetScreen
import com.example.ui.screens.DarkroomProgressDialog
import com.example.ui.screens.DevelopmentMethodSheet
import com.example.ui.screens.LaboScreen
import com.example.ui.screens.SettingsBottomSheet
import com.example.ui.screens.ViewfinderScreen
import com.example.ui.theme.ArgentiqueTheme
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.FilmCream
import com.example.ui.theme.MatteLeather
import com.example.viewmodel.AppScreen
import com.example.viewmodel.ArgentiqueViewModel

class MainActivity : ComponentActivity() {
    var hardwareShutterAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArgentiqueTheme {
                ArgentiqueAppRoot(
                    onRegisterHardwareShutter = { action -> hardwareShutterAction = action }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event?.repeatCount == 0
        ) {
            val action = hardwareShutterAction
            if (action != null) {
                action.invoke()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun ArgentiqueAppRoot(
    onRegisterHardwareShutter: ((() -> Unit)?) -> Unit,
    viewModel: ArgentiqueViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeRoll by viewModel.activeRoll.collectAsStateWithLifecycle()
    val inProgressRolls by viewModel.inProgressRolls.collectAsStateWithLifecycle()
    val inProgressRollsByBody by viewModel.inProgressRollsByBody.collectAsStateWithLifecycle()
    val rollForDevelopment by viewModel.rollForDevelopment.collectAsStateWithLifecycle()
    val developedRolls by viewModel.developedRolls.collectAsStateWithLifecycle()
    val developedRollPreviews by viewModel.developedRollPreviews.collectAsStateWithLifecycle()
    val contactRoll by viewModel.selectedContactRoll.collectAsStateWithLifecycle()
    val contactFrames by viewModel.contactSheetFrames.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showOpticalDiagnostic by remember { mutableStateOf(false) }

    // Camera runtime permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            viewModel.setUseSampleSceneInViewfinder(true)
        }
    }

    // Zero-permission Android Photo Picker for developing existing gallery photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoFromUri(uri)
        }
    }
    val launchPhotoImport: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX controller & Super 8 GL video recorder lifecycle
    val cameraController = remember { CameraXController(context) }
    val super8RecorderHandle = remember { Super8RecorderHandle() }
    var activeSurfaceHolder by remember { mutableStateOf<CameraSurfaceHolder?>(null) }
    var boutiqueUseLiveCamera by remember { mutableStateOf(false) }

    // Reset boutique live camera toggle when leaving Boutique
    LaunchedEffect(uiState.currentScreen) {
        if (uiState.currentScreen != AppScreen.BOUTIQUE) {
            boutiqueUseLiveCamera = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.unbind()
        }
    }

    val configuration = LocalConfiguration.current
    fun readDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            }
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }
    }

    var displayRotation by remember { mutableIntStateOf(readDisplayRotation()) }
    LaunchedEffect(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        displayRotation = readDisplayRotation()
    }
    DisposableEffect(context) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                displayRotation = readDisplayRotation()
            }
        }
        dm?.registerDisplayListener(listener, null)
        onDispose { dm?.unregisterDisplayListener(listener) }
    }

    val shouldBindLiveCamera = hasCameraPermission && when (uiState.currentScreen) {
        AppScreen.VIEWFINDER -> !uiState.useSampleSceneInViewfinder
        AppScreen.BOUTIQUE -> boutiqueUseLiveCamera
        AppScreen.LABO, AppScreen.CONTACT_SHEET -> false
    }

    // Rebind CameraX ONLY when live camera is actively requested AND surfaceHolder is valid
    LaunchedEffect(shouldBindLiveCamera, uiState.isFrontCamera, activeSurfaceHolder) {
        val holder = activeSurfaceHolder
        if (shouldBindLiveCamera && holder != null && holder.isValid()) {
            cameraController.bindToSurfaceHolder(
                lifecycleOwner = lifecycleOwner,
                surfaceHolder = holder,
                isFrontCamera = uiState.isFrontCamera,
                targetRotation = displayRotation
            ) { bound ->
                if (!bound && uiState.currentScreen == AppScreen.VIEWFINDER && holder.isValid()) {
                    viewModel.setUseSampleSceneInViewfinder(true)
                }
            }
        } else {
            cameraController.unbind()
        }
    }

    // Keep CameraX Preview & ImageCapture target rotation synced live on orientation changes
    LaunchedEffect(displayRotation) {
        cameraController.updateTargetRotation(displayRotation)
    }

    // Sync hardware EV, zoom, flash & focus mode to CameraX (toy cams never use AF/AE lock)
    LaunchedEffect(uiState.evCompensation) {
        cameraController.setExposureCompensationEv(uiState.evCompensation)
    }
    LaunchedEffect(uiState.selectedLens, uiState.zoomMultiplier) {
        cameraController.setZoomRatio(uiState.selectedLens.cropZoomFactor * uiState.zoomMultiplier)
    }
    LaunchedEffect(uiState.flashMode) {
        cameraController.setFlashMode(uiState.flashMode)
    }
    LaunchedEffect(uiState.selectedBody) {
        if (uiState.selectedBody.isFixedFocusToyCam) {
            cameraController.unlockAeAf()
        }
    }

    // Toast/Snackbar messages
    LaunchedEffect(uiState.activeToastMessage) {
        val msg = uiState.activeToastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
    }

    // System Back handling for secondary screens
    BackHandler(enabled = uiState.currentScreen != AppScreen.VIEWFINDER) {
        viewModel.navigateBack()
    }

    val triggerPhotoCapture: () -> Unit = {
        viewModel.captureExposure {
            if (hasCameraPermission && !uiState.useSampleSceneInViewfinder) {
                cameraController.captureBitmap(uiState.isFrontCamera)
            } else {
                null
            }
        }
    }

    val toggleSuper8Recording: () -> Unit = {
        if (uiState.isRecordingVideo) {
            super8RecorderHandle.stopRecording { file ->
                viewModel.onVideoRecordingStopped(file)
            }
        } else {
            val outFile = viewModel.createSuper8OutputFile()
            val ratio = uiState.selectedBody.aspectRatioSpec.aspectRatio
            val targetFps = uiState.selectedVideoFilter.targetFps
            super8RecorderHandle.startRecording(outFile, ratio, targetFps) { started ->
                if (started) {
                    viewModel.onVideoRecordingStarted {
                        super8RecorderHandle.stopRecording { finished ->
                            viewModel.onVideoRecordingStopped(finished)
                        }
                    }
                } else {
                    viewModel.onVideoRecordingStopped(null)
                }
            }
        }
    }

    // Stop Super 8 recording safely if app enters background
    DisposableEffect(lifecycleOwner, uiState.isRecordingVideo) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && uiState.isRecordingVideo) {
                super8RecorderHandle.stopRecording { finished ->
                    viewModel.onVideoRecordingStopped(finished)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hardware Volume + / - shutter release while actively framing in Viewfinder
    val canUseVolumeShutter = uiState.currentScreen == AppScreen.VIEWFINDER &&
        uiState.instantPrintPreview == null &&
        uiState.recordedClipPreview == null &&
        !uiState.showSettingsSheet &&
        !uiState.showDevelopDialog &&
        !uiState.devProgress.isRunning &&
        !uiState.showEndOfRollPrompt
    DisposableEffect(canUseVolumeShutter, uiState.isCineVideoMode, uiState.isRecordingVideo, hasCameraPermission, uiState.useSampleSceneInViewfinder, uiState.isFrontCamera, uiState.selectedBody.id) {
        onRegisterHardwareShutter(
            if (canUseVolumeShutter) {
                {
                    if (uiState.isCineVideoMode) toggleSuper8Recording()
                    else triggerPhotoCapture()
                }
            } else {
                null
            }
        )
        onDispose { onRegisterHardwareShutter(null) }
    }

    Scaffold(
        containerColor = MatteLeather,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = BrushedGunmetal,
                    contentColor = FilmCream,
                    actionColor = BrassAccent
                )
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.currentScreen) {
                AppScreen.VIEWFINDER -> {
                    ViewfinderScreen(
                        uiState = uiState,
                        settings = settings,
                        activeRoll = activeRoll,
                        developedCount = developedRolls.size,
                        inProgressRollsCount = inProgressRolls.size,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onSurfaceTextureReady = { holder -> activeSurfaceHolder = holder },
                        onSurfaceTextureDestroyed = { holder ->
                            if (activeSurfaceHolder === holder) {
                                activeSurfaceHolder = null
                                cameraController.unbind()
                            }
                        },
                        onTapToFocus = { nx, ny ->
                            if (!uiState.selectedBody.isFixedFocusToyCam) {
                                cameraController.tapToFocus(nx, ny, uiState.isAeAfLocked)
                            }
                        },
                        onToggleAeAfLock = {
                            if (!uiState.selectedBody.isFixedFocusToyCam) {
                                viewModel.toggleAeAfLock()
                                if (uiState.isAeAfLocked) cameraController.unlockAeAf()
                            }
                        },
                        onEvChange = viewModel::setEvCompensation,
                        onZoomStep = {
                            if (!uiState.selectedBody.isFixedFocusToyCam) {
                                val lens = uiState.selectedLens
                                val nextZoom = if (lens.zoomMaxMm != null) {
                                    val maxZ = lens.zoomMaxMm.toFloat() / lens.focalLengthMm.toFloat()
                                    val step = (maxZ - 1f) / 3f
                                    val candidate = uiState.zoomMultiplier + step
                                    if (candidate > maxZ + 0.05f) 1.0f else candidate
                                } else {
                                    if (uiState.zoomMultiplier < 1.35f) 1.5f else 1.0f
                                }
                                viewModel.setZoomMultiplier(nextZoom)
                            }
                        },
                        onZoomChange = { zoom ->
                            if (!uiState.selectedBody.isFixedFocusToyCam) {
                                viewModel.setZoomMultiplier(zoom)
                            }
                        },
                        onCaptureClick = triggerPhotoCapture,
                        onToggleRecordVideo = toggleSuper8Recording,
                        onToggleCineVideoMode = viewModel::toggleCineVideoMode,
                        onSelectVideoFilter = viewModel::selectVideoFilter,
                        onTiltShiftPositionChange = viewModel::setTiltShiftPosition,
                        onToggleTiltShiftOrientation = viewModel::toggleTiltShiftOrientation,
                        onToggleInvertedGroundGlass = viewModel::toggleInvertedGroundGlass,
                        onTogglePureViewfinderMode = viewModel::togglePureViewfinderMode,
                        onToggleDoubleExposure = viewModel::toggleDoubleExposureMode,
                        onCancelPendingDoubleExposure = viewModel::cancelPendingDoubleExposure,
                        onRerollOpticalEffects = viewModel::rerollOpticalEffects,
                        recorderHandle = super8RecorderHandle,
                        onCycleDateStampStyle = viewModel::cycleDateStampStyle,
                        onCycleSelfTimer = viewModel::cycleSelfTimer,
                        onCycleFlashMode = viewModel::cycleFlashMode,
                        onImportGalleryPhoto = launchPhotoImport,
                        onOpenBoutique = { tab -> viewModel.navigateTo(AppScreen.BOUTIQUE, tab) },
                        onOpenLabo = { viewModel.navigateTo(AppScreen.LABO) },
                        onOpenSettings = { viewModel.toggleSettingsSheet(true) },
                        onFlipCamera = viewModel::toggleFrontCamera,
                        onToggleSampleNight = { viewModel.updateSampleSceneNight(!settings.sampleSceneNight) },
                        onUseDemoScene = { viewModel.setUseSampleSceneInViewfinder(true) },
                        onOpenDevelopNow = {
                            viewModel.navigateTo(AppScreen.LABO)
                            viewModel.openDevelopDialog()
                        },
                        onDismissEndOfRoll = viewModel::dismissEndOfRollPrompt
                    )
                }

                AppScreen.BOUTIQUE -> {
                    BoutiqueScreen(
                        uiState = uiState,
                        settings = settings,
                        activeRoll = activeRoll,
                        inProgressRollsByBody = inProgressRollsByBody,
                        hasCameraPermission = hasCameraPermission,
                        previewUseCamera = boutiqueUseLiveCamera,
                        onSetPreviewUseCamera = { boutiqueUseLiveCamera = it },
                        onSelectTab = viewModel::setBoutiqueFocusTab,
                        onBack = { viewModel.navigateBack() },
                        onSelectBody = viewModel::selectBody,
                        onSelectLens = viewModel::selectLens,
                        onSelectFilm = { film -> viewModel.selectFilm(film) },
                        onRandomizeBody = viewModel::randomizeBody,
                        onRandomizeLens = viewModel::randomizeLens,
                        onRandomizeFilm = viewModel::randomizeFilm,
                        onRandomizeFullCombo = viewModel::randomizeFullCombo,
                        onSelectFilmType = viewModel::setFilmTypeFilter,
                        onSelectMood = viewModel::setMoodFilter,
                        onSelectFilmSort = viewModel::setFilmSort,
                        onSearchQueryChange = viewModel::setFilmSearchQuery,
                        onToggleFavoritesOnly = viewModel::toggleShowFavoritesOnly,
                        onToggleFavoriteFilm = viewModel::toggleFavoriteFilm,
                        onResetFilmFilters = viewModel::resetFilmFilters,
                        onSelectExpiration = viewModel::selectExpirationDate,
                        onRandomizeExpiration = viewModel::randomizeExpirationBatch,
                        onToggleSampleNight = viewModel::updateSampleSceneNight,
                        onSurfaceTextureReady = { holder -> activeSurfaceHolder = holder },
                        onSurfaceTextureDestroyed = { holder ->
                            if (activeSurfaceHolder === holder) {
                                activeSurfaceHolder = null
                                cameraController.unbind()
                            }
                        },
                        onOpenLaboToRewind = { viewModel.navigateTo(AppScreen.LABO) },
                        onSaveCurrentCombo = viewModel::saveCurrentComboPreset,
                        onApplyComboPreset = viewModel::applyComboPreset,
                        onDeleteComboPreset = viewModel::deleteComboPreset,
                        onApplyCuratedEraCombo = viewModel::applyCuratedEraCombo
                    )
                }

                AppScreen.LABO -> {
                    LaboScreen(
                        activeRoll = activeRoll,
                        inProgressRolls = inProgressRolls,
                        developedRolls = developedRolls,
                        developedRollPreviews = developedRollPreviews,
                        onBack = { viewModel.navigateBack() },
                        onOpenDevelopSheet = { roll -> viewModel.openDevelopDialog(roll) },
                        onResumeRollInViewfinder = viewModel::pickUpCameraRoll,
                        onOpenContactSheet = viewModel::openContactSheet,
                        onRenameRoll = { roll, title -> viewModel.renameRoll(roll.id, title) },
                        onDeleteRoll = viewModel::deleteDevelopedRoll
                    )
                }

                AppScreen.CONTACT_SHEET -> {
                    val roll = contactRoll
                    if (roll != null) {
                        ContactSheetScreen(
                            roll = roll,
                            frames = contactFrames,
                            greasePencilMarks = settings.greasePencilFrameIds,
                            onToggleGreasePencil = viewModel::toggleGreasePencilFrame,
                            onBack = { viewModel.navigateBack() },
                            onRenameRoll = { title -> viewModel.renameRoll(roll.id, title) },
                            onUpdateFrameCaption = { frame, note -> viewModel.updateFrameCaption(frame.id, note) },
                            onExportContactSheet = { viewModel.exportContactSheetForRoll(roll) },
                            onExportAllFrames = { viewModel.exportAllFramesInRoll(roll) },
                            onShareContactSheet = { viewModel.shareContactSheet(roll) },
                            onExportSingleFrame = { frame -> viewModel.exportSingleFrame(frame, roll) },
                            onShareSingleFrame = { frame -> viewModel.shareSingleFrame(frame, roll) },
                            onExportGalleryCard = { frame, story916 -> viewModel.exportGalleryCardForFrame(frame, roll, story916) },
                            onShareGalleryCard = { frame, story916 -> viewModel.shareGalleryCardForFrame(frame, roll, story916) }
                        )
                    } else {
                        viewModel.navigateBack()
                    }
                }
            }
        }
    }

    // Museum Paper Instant Print / Imported Photo Preview Overlay
    uiState.instantPrintPreview?.let { preview ->
        InstantGalleryPrintDialog(
            preview = preview,
            onDismiss = viewModel::dismissInstantPreview,
            onUpdateNote = viewModel::updateInstantPreviewNote,
            onSaveRaw = { viewModel.exportInstantPreview(asGalleryCard = false) },
            onSaveGalleryPost = { viewModel.exportInstantPreview(asGalleryCard = true, story916 = false) },
            onSaveGalleryStory = { viewModel.exportInstantPreview(asGalleryCard = true, story916 = true) },
            onShareGalleryCard = { viewModel.shareInstantPreview(asGalleryCard = true, story916 = false) }
        )
    }

    // Super 8 / 35mm Recorded Clip Preview Overlay
    uiState.recordedClipPreview?.let { clip ->
        Super8ClipPreviewDialog(
            clip = clip,
            onDismiss = viewModel::dismissRecordedClip,
            onSave = viewModel::saveRecordedClipToGallery,
            onShare = viewModel::shareRecordedClip
        )
    }

    // Advanced Lab Chemical Development Selector Sheet
    val rollForDev = rollForDevelopment ?: activeRoll
    if (uiState.showDevelopDialog && rollForDev != null) {
        DevelopmentMethodSheet(
            activeRoll = rollForDev,
            selectedMethod = uiState.selectedDevMethod,
            sampleSceneNight = settings.sampleSceneNight,
            animatedEffectsEnabled = settings.animatedEffectsEnabled,
            onSelectMethod = viewModel::selectDevelopmentMethod,
            onDismiss = viewModel::dismissDevelopDialog,
            onStartDevelopment = viewModel::startLabDevelopment
        )
    }

    // Mechanical Settings Bottom Sheet
    if (uiState.showSettingsSheet) {
        SettingsBottomSheet(
            settings = settings,
            supportsDiptych = uiState.selectedBody.supportsDiptych,
            useSampleSceneInViewfinder = uiState.useSampleSceneInViewfinder,
            onDismiss = { viewModel.toggleSettingsSheet(false) },
            onSelectDateStampStyle = viewModel::updateDateStampStyle,
            onToggleFrameBorder = viewModel::updateFrameBorder,
            onToggleGrid = viewModel::updateGrid,
            onToggleDiptych = viewModel::updateDiptych,
            onToggleSound = viewModel::updateSound,
            onToggleHaptics = viewModel::updateHaptics,
            onToggleLosslessPng = viewModel::updateLosslessPng,
            onToggleSampleSceneMode = viewModel::setUseSampleSceneInViewfinder,
            onToggleInstantPreview = viewModel::updateInstantPreview,
            onToggleAnimatedEffects = viewModel::updateAnimatedEffects,
            onSelectLightLeakMode = viewModel::updateLightLeakMode,
            onImportGalleryPhoto = launchPhotoImport,
            onOpenOpticalDiagnostic = {
                viewModel.toggleSettingsSheet(false)
                showOpticalDiagnostic = true
            }
        )
    }

    if (showOpticalDiagnostic) {
        OpticalDiagnosticBottomSheet(
            onDismiss = { showOpticalDiagnostic = false }
        )
    }

    // Darkroom Chemical Bath Processing Progress Overlay
    DarkroomProgressDialog(progressState = uiState.devProgress)
}
