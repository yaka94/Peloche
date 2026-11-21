package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.CameraSettings
import com.example.data.local.FilmFrameEntity
import com.example.data.local.FilmRepository
import com.example.data.local.FilmRollEntity
import com.example.data.local.SettingsDataStore
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.CameraFlashMode
import com.example.domain.model.CuratedEraCombo
import com.example.domain.model.DateStampStyle
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmMood
import com.example.domain.model.FilmSortOption
import com.example.domain.model.FilmStockModel
import com.example.domain.model.FilmTypeFilter
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.LensModel
import com.example.domain.model.OpticalChainParams
import com.example.domain.model.SavedComboPreset
import com.example.domain.model.VideoFilterStyle
import com.example.engine.ExifAndExportManager
import com.example.engine.MechanicalAudioEngine
import com.example.engine.OffscreenGlProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

enum class AppScreen {
    VIEWFINDER,
    BOUTIQUE,
    LABO,
    CONTACT_SHEET
}

enum class BoutiqueFocusTab {
    BODY,
    LENS,
    FILM
}

data class ExpiredFilmAlert(
    val film: FilmStockModel,
    val expirationDate: FilmExpirationDate,
    val warningMessage: String
)

data class DevelopmentProgressState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val rollBeingDeveloped: FilmRollEntity? = null
)

data class InstantPrintPreview(
    val developedPath: String,
    val originalPath: String,
    val filmName: String,
    val filmCode: String,
    val bodyName: String,
    val lensLabel: String,
    val caption: String,
    val isImportedFromGallery: Boolean,
    val frameNumber: Int,
    val handwrittenNote: String = "",
    val frameId: Long? = null,
    val revealType: String = "STANDARD"
)

data class RecordedClipPreview(
    val filePath: String,
    val durationSec: Int,
    val bodyName: String,
    val filmName: String,
    val filmCode: String,
    val aspectLabel: String,
    val videoFilterLabel: String = "Super 8 Kodachrome",
    val videoFilterBadge: String = "SUPER 8",
    val fpsLabel: String = "18 i/s"
)

data class ArgentiqueUiState(
    val currentScreen: AppScreen = AppScreen.VIEWFINDER,
    val boutiqueFocusTab: BoutiqueFocusTab = BoutiqueFocusTab.FILM,
    val selectedBody: CameraBodyModel = HardwareCatalog.bodies[0],
    val selectedLens: LensModel = HardwareCatalog.lenses[1],
    val selectedFilm: FilmStockModel = HardwareCatalog.films[0],
    val selectedExpiration: FilmExpirationDate = FilmExpirationDate.freshDefault(),
    val selectedFilmTypeFilter: FilmTypeFilter = FilmTypeFilter.ALL,
    val selectedMoodFilter: FilmMood = FilmMood.ALL,
    val selectedFilmSort: FilmSortOption = FilmSortOption.CATALOG,
    val filmSearchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val evCompensation: Float = 0f,
    val zoomMultiplier: Float = 1.0f,
    val isFrontCamera: Boolean = false,
    val flashMode: CameraFlashMode = CameraFlashMode.OFF,
    val isAeAfLocked: Boolean = false,
    val useSampleSceneInViewfinder: Boolean = false,
    val previewLightLeakSeed: Float = 0.37f,
    val lightLeakMode: com.example.domain.model.LightLeakMode = com.example.domain.model.LightLeakMode.AUTO,
    val tiltShiftPosition: Float = 0.5f,
    val tiltShiftVertical: Boolean = false,
    val invertedGroundGlass: Boolean = false,
    val isPureViewfinderMode: Boolean = false,
    val selectedVideoFilter: VideoFilterStyle = VideoFilterStyle.SUPER_8_KODACHROME,
    val isDoubleExposureMode: Boolean = false,
    val doubleExposureFirstShotPath: String? = null,
    val isCineVideoMode: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val recordingElapsedSec: Int = 0,
    val recordedClipPreview: RecordedClipPreview? = null,
    val isCapturing: Boolean = false,
    val isImportingPhoto: Boolean = false,
    val selfTimerCountdown: Int = 0,
    val shutterFlashTick: Int = 0,
    val instantPrintPreview: InstantPrintPreview? = null,
    val showSettingsSheet: Boolean = false,
    val showDevelopDialog: Boolean = false,
    val rollPendingDevelopmentId: Long? = null,
    val showEndOfRollPrompt: Boolean = false,
    val selectedDevMethod: DevelopmentMethod = DevelopmentMethod.STANDARD_C41,
    val devProgress: DevelopmentProgressState = DevelopmentProgressState(),
    val expiredFilmAlert: ExpiredFilmAlert? = null,
    val selectedContactRollId: Long? = null,
    val activeToastMessage: String? = null
) {
    val liveOpticalParams: OpticalChainParams
        get() {
            val isDedicatedVideoFilter = isCineVideoMode && selectedVideoFilter != VideoFilterStyle.OPTICAL_CHAIN
            val effectiveFilm = if (isDedicatedVideoFilter) {
                selectedFilm.copy(
                    isMonochrome = false,
                    redGain = 1.0f,
                    greenGain = 1.0f,
                    blueGain = 1.0f,
                    contrast = 1.04f,
                    saturation = 1.0f,
                    toeLift = 0.015f,
                    shoulderCompress = 0.10f,
                    shadowTintR = 0f,
                    shadowTintG = 0f,
                    shadowTintB = 0f,
                    highlightTintR = 0f,
                    highlightTintG = 0f,
                    highlightTintB = 0f,
                    hueShift = 0f,
                    sepiaAmount = 0f,
                    cyanotypeAmount = 0f,
                    infraredAmount = 0f,
                    orthoUvAmount = 0f,
                    collodionPlateAmount = 0f,
                    daguerreTarnishAmount = 0f,
                    autochromeMosaicAmount = 0f,
                    paperFiberAmount = 0f,
                    brushBorderAmount = 0f
                )
            } else {
                selectedFilm
            }
            val effectiveBody = if (isDedicatedVideoFilter) {
                selectedBody.copy(
                    collodionPlateAmount = 0f,
                    daguerreTarnishAmount = 0f,
                    autochromeMosaicAmount = 0f
                )
            } else {
                selectedBody
            }
            return OpticalChainParams(
                lens = selectedLens,
                body = effectiveBody,
                film = effectiveFilm,
                expirationDate = if (isDedicatedVideoFilter) FilmExpirationDate.freshDefault() else selectedExpiration,
                development = DevelopmentMethod.STANDARD_C41,
                evCompensation = evCompensation,
                zoomMultiplier = zoomMultiplier,
                lightLeakSeed = previewLightLeakSeed,
                isFrontCamera = isFrontCamera,
                lightLeakMode = lightLeakMode,
                tiltShiftPosition = tiltShiftPosition,
                tiltShiftVertical = tiltShiftVertical,
                invertedGroundGlass = invertedGroundGlass && selectedBody.isHistoricalLargeFormat,
                videoFilterMode = if (isCineVideoMode) selectedVideoFilter.shaderModeId else 0
            )
        }
}

class ArgentiqueViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FilmRepository(application, db.filmDao())
    val settingsStore = SettingsDataStore(application)
    val audioEngine = MechanicalAudioEngine(application)

    private val _uiState = MutableStateFlow(ArgentiqueUiState())
    val uiState: StateFlow<ArgentiqueUiState> = _uiState.asStateFlow()
    private var videoTimerJob: Job? = null

    val settings: StateFlow<CameraSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CameraSettings()
    )

    val activeRoll: StateFlow<FilmRollEntity?> = repository.activeRoll.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * All currently loaded & started film rolls across the photographer's camera bag
     * (one independent in-progress roll per camera body!).
     */
    val inProgressRolls: StateFlow<List<FilmRollEntity>> = repository.inProgressRolls.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val inProgressRollsByBody: StateFlow<Map<String, FilmRollEntity>> = inProgressRolls
        .map { list -> list.associateBy { it.bodyId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val developedRolls: StateFlow<List<FilmRollEntity>> = repository.developedRolls.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Maps each developed roll ID to up to 4 developed image file paths for miniature 35mm
     * filmstrip previews right inside the Lab view.
     */
    val developedRollPreviews: StateFlow<Map<Long, List<String>>> = repository.allDevelopedFrames
        .map { frames ->
            frames.groupBy { it.rollId }
                .mapValues { (_, rollFrames) ->
                    rollFrames.mapNotNull { it.developedPath }.take(4)
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactSheetFrames: StateFlow<List<FilmFrameEntity>> = _uiState
        .map { it.selectedContactRollId }
        .distinctUntilChanged()
        .flatMapLatest { rollId ->
            if (rollId == null) flowOf(emptyList()) else repository.observeFrames(rollId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedContactRoll: StateFlow<FilmRollEntity?> = combine(
        developedRolls,
        _uiState
    ) { rolls, state ->
        rolls.firstOrNull { it.id == state.selectedContactRollId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rollForDevelopment: StateFlow<FilmRollEntity?> = combine(
        activeRoll,
        inProgressRolls,
        _uiState
    ) { active, inProgress, state ->
        val targetId = state.rollPendingDevelopmentId
        if (targetId != null) {
            inProgress.firstOrNull { it.id == targetId }
                ?: active?.takeIf { it.id == targetId }
                ?: active
        } else {
            active
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            val roll = repository.ensureActiveRoll()
            syncUiToRoll(roll, notifyIfExpired = true)
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.lightLeakMode }
                .distinctUntilChanged()
                .collect { mode ->
                    _uiState.update { it.copy(lightLeakMode = mode) }
                }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.videoFilterStyle }
                .distinctUntilChanged()
                .collect { style ->
                    _uiState.update { it.copy(selectedVideoFilter = style) }
                }
        }
    }

    private fun syncUiToRoll(roll: FilmRollEntity, notifyIfExpired: Boolean) {
        val body = HardwareCatalog.bodyById(roll.bodyId)
        val rawLens = HardwareCatalog.lensById(roll.lensId)
        val lens = if (HardwareCatalog.isLensCompatible(rawLens, body)) {
            rawLens
        } else {
            HardwareCatalog.defaultLensFor(body)
        }
        val film = HardwareCatalog.filmById(roll.filmId)
        val exp = roll.expirationDate
        _uiState.update {
            it.copy(
                selectedBody = body,
                selectedLens = lens,
                selectedFilm = film,
                selectedExpiration = exp,
                zoomMultiplier = 1.0f,
                expiredFilmAlert = null
            )
        }
    }

    fun setBoutiqueFocusTab(tab: BoutiqueFocusTab) {
        playDetent()
        _uiState.update { it.copy(boutiqueFocusTab = tab) }
    }

    fun navigateTo(screen: AppScreen, boutiqueTab: BoutiqueFocusTab? = null) {
        playDetent()
        _uiState.update {
            it.copy(
                currentScreen = screen,
                boutiqueFocusTab = boutiqueTab ?: it.boutiqueFocusTab
            )
        }
    }

    fun navigateBack() {
        val current = _uiState.value.currentScreen
        when (current) {
            AppScreen.CONTACT_SHEET -> _uiState.update { it.copy(currentScreen = AppScreen.LABO) }
            AppScreen.BOUTIQUE, AppScreen.LABO -> _uiState.update { it.copy(currentScreen = AppScreen.VIEWFINDER) }
            AppScreen.VIEWFINDER -> Unit
        }
    }

    fun openContactSheet(roll: FilmRollEntity) {
        if (!roll.isDeveloped) {
            showToast("Pellicule non développée — passez au labo pour révéler les négatifs !")
            return
        }
        playDetent()
        _uiState.update {
            it.copy(
                selectedContactRollId = roll.id,
                currentScreen = AppScreen.CONTACT_SHEET
            )
        }
    }

    /**
     * Photographer's Bag: Switching camera bodies is ALWAYS allowed!
     * - Any started roll in the previous body stays safely loaded inside that body.
     * - If the picked body already has an in-progress roll, it is immediately resumed.
     * - Lens automatically adapts to the picked body's physical mount.
     */
    fun selectBody(body: CameraBodyModel) {
        playDetent()
        val currentState = _uiState.value
        val existingForBody = inProgressRollsByBody.value[body.id]
        val nextLens = if (existingForBody != null) {
            val rollLens = HardwareCatalog.lensById(existingForBody.lensId)
            if (HardwareCatalog.isLensCompatible(rollLens, body)) rollLens else HardwareCatalog.defaultLensFor(body)
        } else if (HardwareCatalog.isLensCompatible(currentState.selectedLens, body)) {
            currentState.selectedLens
        } else {
            HardwareCatalog.defaultLensFor(body)
        }

        // Optimistic immediate UI preview update
        _uiState.update {
            it.copy(
                selectedBody = body,
                selectedLens = nextLens,
                selectedFilm = if (existingForBody != null) HardwareCatalog.filmById(existingForBody.filmId) else it.selectedFilm,
                selectedExpiration = existingForBody?.expirationDate ?: it.selectedExpiration,
                zoomMultiplier = 1.0f,
                isAeAfLocked = if (body.isFixedFocusToyCam) false else it.isAeAfLocked,
                previewLightLeakSeed = Random.nextFloat()
            )
        }

        val diptych = settings.value.diptychEnabled
        viewModelScope.launch {
            val switchedRoll = repository.switchToBody(
                body = body,
                preferredLens = nextLens,
                preferredFilm = currentState.selectedFilm,
                preferredExpiration = currentState.selectedExpiration,
                isDiptychMode = diptych
            )
            syncUiToRoll(switchedRoll, notifyIfExpired = false)
            if (switchedRoll.shotsTaken > 0) {
                val loadedFilm = HardwareCatalog.filmById(switchedRoll.filmId)
                showToast("${body.name} : reprise de ${loadedFilm.name} (${switchedRoll.shotsTaken}/${switchedRoll.maxExposures}).")
            }
        }
    }

    /**
     * Picks up a specific loaded camera body from the Labo / photographer's bag and jumps to the viewfinder.
     */
    fun pickUpCameraRoll(roll: FilmRollEntity) {
        playDetent()
        viewModelScope.launch {
            val activated = repository.switchToRoll(roll.id)
            if (activated != null) {
                syncUiToRoll(activated, notifyIfExpired = false)
                _uiState.update { it.copy(currentScreen = AppScreen.VIEWFINDER) }
            }
        }
    }

    /**
     * Swapping lenses works AT ANY TIME (even mid-roll!), just like a real interchangeable-lens camera,
     * strictly limited to lenses physically compatible with the current body's mount.
     */
    fun selectLens(lens: LensModel) {
        val currentBody = _uiState.value.selectedBody
        if (!HardwareCatalog.isLensCompatible(lens, currentBody)) {
            showToast("Objectif incompatible avec ${currentBody.mount.labelFr}.")
            return
        }
        playDetent()
        _uiState.update {
            it.copy(
                selectedLens = lens,
                zoomMultiplier = 1.0f
            )
        }
        persistRollHardwareUpdate()
    }

    /**
     * Selecting a new film stock draws a realistic randomized expiration date from the shelf.
     * Locked only if THIS specific camera body already has exposed shots on its current roll.
     */
    fun selectFilm(film: FilmStockModel, customExpiration: FilmExpirationDate? = null) {
        playDetent()
        val roll = activeRoll.value
        val body = _uiState.value.selectedBody
        if (roll != null && roll.shotsTaken > 0 && roll.filmId != film.id) {
            showToast("Pellicule en cours dans ${body.name} (${roll.shotsTaken}/${roll.maxExposures}) — changez de boîtier ou développez au Labo.")
            return
        }
        val drawnExpiration = customExpiration ?: FilmExpirationDate.randomForNewRoll(
            seed = System.nanoTime() xor film.id.hashCode().toLong()
        )
        _uiState.update {
            it.copy(
                selectedFilm = film,
                selectedExpiration = drawnExpiration,
                expiredFilmAlert = null
            )
        }
        persistRollHardwareUpdate()
    }

    /**
     * Randomizes the Camera Body (always allowed — any started roll in the previous body stays in the bag!).
     */
    fun randomizeBody() {
        val currentId = _uiState.value.selectedBody.id
        val candidates = HardwareCatalog.bodies.filter { it.id != currentId }
        val picked = candidates.randomOrNull() ?: HardwareCatalog.bodies.first()
        selectBody(picked)
    }

    /**
     * Randomizes only the Lens among lenses physically compatible with the current body
     * (always allowed, even mid-roll!).
     */
    fun randomizeLens() {
        val compatible = HardwareCatalog.compatibleLensesFor(_uiState.value.selectedBody)
        val currentId = _uiState.value.selectedLens.id
        val candidates = compatible.filter { it.id != currentId }.ifEmpty { compatible }
        val picked = candidates.randomOrNull() ?: HardwareCatalog.defaultLensFor(_uiState.value.selectedBody)
        selectLens(picked)
    }

    /**
     * Randomizes only the Film Stock (respecting current mood filter if any, on unexposed rolls).
     */
    fun randomizeFilm() {
        val roll = activeRoll.value
        val body = _uiState.value.selectedBody
        if (roll != null && roll.shotsTaken > 0) {
            showToast("Pellicule en cours dans ${body.name} (${roll.shotsTaken}/${roll.maxExposures}) — changez de boîtier ou développez au Labo.")
            return
        }
        val state = _uiState.value
        val favs = settings.value.favoriteFilmIds
        val query = state.filmSearchQuery.trim()
        val pool = HardwareCatalog.films.filter { film ->
            state.selectedFilmTypeFilter.matches(film) &&
                (state.selectedMoodFilter == FilmMood.ALL || film.mood == state.selectedMoodFilter) &&
                (!state.showFavoritesOnly || film.id in favs) &&
                (query.isEmpty() || film.name.contains(query, ignoreCase = true) || film.code.contains(query, ignoreCase = true) || film.tagline.contains(query, ignoreCase = true))
        }.ifEmpty { HardwareCatalog.films }
        val currentId = state.selectedFilm.id
        val candidates = pool.filter { it.id != currentId }.ifEmpty { pool }
        val picked = candidates.randomOrNull() ?: HardwareCatalog.films.first()
        selectFilm(picked)
    }

    /**
     * Draws a full surprise analog trio (Boîtier + Objectif compatible + Pellicule + lot de péremption).
     * Mid-roll on the current camera, preserves the current roll and only swaps to a random compatible lens!
     */
    fun randomizeFullCombo() {
        playDetent()
        val roll = activeRoll.value
        if (roll != null && roll.shotsTaken > 0) {
            val currentBody = _uiState.value.selectedBody
            val compatible = HardwareCatalog.compatibleLensesFor(currentBody)
            val currentLensId = _uiState.value.selectedLens.id
            val pickedLens = compatible.filter { it.id != currentLensId }.randomOrNull()
                ?: HardwareCatalog.defaultLensFor(currentBody)
            selectLens(pickedLens)
            showToast("Pellicule en cours dans ${currentBody.name} : objectif monté (${pickedLens.shortCode}).")
            return
        }
        // Prefer an unloaded body from the bag so the new surprise combo starts cleanly at 0 poses
        val busyBodyIds = inProgressRollsByBody.value.keys
        val freeBodies = HardwareCatalog.bodies.filter { it.id !in busyBodyIds }.ifEmpty { HardwareCatalog.bodies }
        val pickedBody = freeBodies.random()
        val pickedLens = HardwareCatalog.compatibleLensesFor(pickedBody).randomOrNull()
            ?: HardwareCatalog.defaultLensFor(pickedBody)
        val pickedFilm = HardwareCatalog.films.random()
        val pickedExp = FilmExpirationDate.randomForNewRoll(System.nanoTime())
        _uiState.update {
            it.copy(
                selectedBody = pickedBody,
                selectedLens = pickedLens,
                selectedFilm = pickedFilm,
                selectedExpiration = pickedExp,
                zoomMultiplier = 1.0f,
                previewLightLeakSeed = Random.nextFloat(),
                expiredFilmAlert = null
            )
        }
        val diptych = settings.value.diptychEnabled
        viewModelScope.launch {
            val switched = repository.switchToBody(
                body = pickedBody,
                preferredLens = pickedLens,
                preferredFilm = pickedFilm,
                preferredExpiration = pickedExp,
                isDiptychMode = diptych
            )
            syncUiToRoll(switched, notifyIfExpired = false)
        }
        showToast("Combo surprise : ${pickedBody.name} + ${pickedLens.shortCode} + ${pickedFilm.name}")
    }

    /**
     * Allows the user to manually draw a different shelf batch / expiration date for an unexposed roll.
     */
    fun selectExpirationDate(expirationDate: FilmExpirationDate) {
        playDetent()
        val roll = activeRoll.value
        if (roll != null && roll.shotsTaken > 0) {
            showToast("Impossible de changer la péremption d'une pellicule déjà exposée.")
            return
        }
        _uiState.update {
            it.copy(
                selectedExpiration = expirationDate,
                expiredFilmAlert = null
            )
        }
        persistRollHardwareUpdate()
    }

    fun randomizeExpirationBatch() {
        selectExpirationDate(FilmExpirationDate.randomForNewRoll())
    }

    fun dismissExpiredAlert() {
        _uiState.update { it.copy(expiredFilmAlert = null) }
    }

    fun setFilmTypeFilter(filter: FilmTypeFilter) {
        playDetent()
        _uiState.update { state ->
            // Clear incompatible mood if switching directly to N&B or another family
            val adjustedMood = when {
                filter == FilmTypeFilter.BW && state.selectedMoodFilter in setOf(
                    FilmMood.COLOR, FilmMood.PASTEL, FilmMood.GOLDEN, FilmMood.NIGHT_TUNGSTEN
                ) -> FilmMood.ALL
                filter != FilmTypeFilter.ALL && filter != FilmTypeFilter.BW && state.selectedMoodFilter == FilmMood.BW -> FilmMood.ALL
                else -> state.selectedMoodFilter
            }
            state.copy(
                selectedFilmTypeFilter = filter,
                selectedMoodFilter = adjustedMood
            )
        }
    }

    fun setMoodFilter(mood: FilmMood) {
        playDetent()
        _uiState.update { state ->
            val adjustedType = if (mood == FilmMood.BW) FilmTypeFilter.BW else state.selectedFilmTypeFilter
            state.copy(
                selectedFilmTypeFilter = adjustedType,
                selectedMoodFilter = mood
            )
        }
    }

    fun setFilmSort(sort: FilmSortOption) {
        playDetent()
        _uiState.update { it.copy(selectedFilmSort = sort) }
    }

    fun setFilmSearchQuery(query: String) {
        _uiState.update { it.copy(filmSearchQuery = query) }
    }

    fun toggleShowFavoritesOnly() {
        playDetent()
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun resetFilmFilters() {
        playDetent()
        _uiState.update {
            it.copy(
                selectedFilmTypeFilter = FilmTypeFilter.ALL,
                selectedMoodFilter = FilmMood.ALL,
                selectedFilmSort = FilmSortOption.CATALOG,
                filmSearchQuery = "",
                showFavoritesOnly = false
            )
        }
    }

    fun setEvCompensation(ev: Float) {
        val snapped = ((ev * 3f).toInt() / 3f).coerceIn(-2.0f, 2.0f)
        if (snapped != _uiState.value.evCompensation) {
            _uiState.update { it.copy(evCompensation = snapped) }
        }
    }

    fun setZoomMultiplier(zoom: Float) {
        val state = _uiState.value
        if (state.selectedBody.isFixedFocusToyCam) {
            if (state.zoomMultiplier != 1.0f) _uiState.update { it.copy(zoomMultiplier = 1.0f) }
            return
        }
        val lens = state.selectedLens
        val maxRatio = if (lens.zoomMaxMm != null) {
            lens.zoomMaxMm.toFloat() / lens.focalLengthMm.toFloat()
        } else {
            1.6f
        }
        _uiState.update { it.copy(zoomMultiplier = zoom.coerceIn(1.0f, maxRatio)) }
    }

    fun toggleFrontCamera() {
        playDetent()
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun cycleFlashMode() {
        playDetent()
        val next = _uiState.value.flashMode.next()
        _uiState.update { it.copy(flashMode = next) }
        showToast("Flash : ${next.labelFr}")
    }

    fun toggleAeAfLock() {
        if (_uiState.value.selectedBody.isFixedFocusToyCam) {
            showToast("${_uiState.value.selectedBody.name} : focale fixe hyperfocale (sans autofocus).")
            return
        }
        playDetent()
        _uiState.update { it.copy(isAeAfLocked = !it.isAeAfLocked) }
    }

    fun toggleDoubleExposureMode() {
        if (_uiState.value.isRecordingVideo) return
        playDetent()
        val wasEnabled = _uiState.value.isDoubleExposureMode
        val pendingPath = _uiState.value.doubleExposureFirstShotPath
        if (wasEnabled && pendingPath != null) {
            runCatching { File(pendingPath).delete() }
        }
        _uiState.update {
            it.copy(
                isDoubleExposureMode = !wasEnabled,
                doubleExposureFirstShotPath = if (wasEnabled) null else it.doubleExposureFirstShotPath,
                isCineVideoMode = if (!wasEnabled) false else it.isCineVideoMode
            )
        }
        showToast(
            if (!wasEnabled) "Double exposition (2×) activée — exposez la 1re vue."
            else "Double exposition désactivée."
        )
    }

    fun cancelPendingDoubleExposure() {
        playDetent()
        val pendingPath = _uiState.value.doubleExposureFirstShotPath
        if (pendingPath != null) {
            runCatching { File(pendingPath).delete() }
        }
        _uiState.update { it.copy(doubleExposureFirstShotPath = null) }
        showToast("1re exposition annulée — prêt pour une nouvelle surimpression.")
    }

    fun rerollOpticalEffects() {
        playDetent()
        _uiState.update { it.copy(previewLightLeakSeed = Random.nextFloat()) }
        showToast("Nouveau tirage optique (fuites, flares & rayures)")
    }

    fun setTiltShiftPosition(pos: Float) {
        val clamped = pos.coerceIn(0.15f, 0.85f)
        if (clamped != _uiState.value.tiltShiftPosition) {
            _uiState.update { it.copy(tiltShiftPosition = clamped) }
        }
    }

    fun toggleTiltShiftOrientation() {
        playDetent()
        val nextVertical = !_uiState.value.tiltShiftVertical
        _uiState.update { it.copy(tiltShiftVertical = nextVertical) }
        showToast(if (nextVertical) "Bascule Tilt-Shift : bande verticale" else "Bascule Tilt-Shift : bande horizontale")
    }

    fun toggleInvertedGroundGlass() {
        playDetent()
        val next = !_uiState.value.invertedGroundGlass
        _uiState.update { it.copy(invertedGroundGlass = next) }
        showToast(
            if (next) "Verre dépoli 180° activé (comme sur vraie chambre XIXe — cliché enregistré à l'endroit)."
            else "Visée redressée activée."
        )
    }

    fun togglePureViewfinderMode() {
        playDetent()
        val next = !_uiState.value.isPureViewfinderMode
        _uiState.update { it.copy(isPureViewfinderMode = next) }
    }

    fun selectVideoFilter(style: VideoFilterStyle) {
        playDetent()
        _uiState.update { it.copy(selectedVideoFilter = style) }
        viewModelScope.launch { settingsStore.setVideoFilterStyle(style) }
        showToast("Ciné : ${style.labelFr} (${style.fpsLabel})")
    }

    fun toggleCineVideoMode() {
        setCineVideoMode(!_uiState.value.isCineVideoMode)
    }

    fun setCineVideoMode(enteringCine: Boolean) {
        if (_uiState.value.isRecordingVideo) return
        if (_uiState.value.isCineVideoMode == enteringCine) return
        playDetent()
        val pendingPath = _uiState.value.doubleExposureFirstShotPath
        if (enteringCine && pendingPath != null) {
            runCatching { File(pendingPath).delete() }
        }
        _uiState.update {
            it.copy(
                isCineVideoMode = enteringCine,
                isDoubleExposureMode = if (enteringCine) false else it.isDoubleExposureMode,
                doubleExposureFirstShotPath = if (enteringCine) null else it.doubleExposureFirstShotPath
            )
        }
        val activeStyle = _uiState.value.selectedVideoFilter
        showToast(
            if (enteringCine) "Mode Ciné : ${activeStyle.labelFr} (${activeStyle.fpsLabel})"
            else "Mode Photo argentique"
        )
    }

    fun createSuper8OutputFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "super8").apply { mkdirs() }
        val state = _uiState.value
        val tag = if (state.selectedVideoFilter == VideoFilterStyle.OPTICAL_CHAIN) {
            state.selectedFilm.code
        } else {
            state.selectedVideoFilter.name.take(8)
        }
        return File(dir, "PELOCHE_CINE_${tag}_${System.currentTimeMillis() % 100000}.mp4")
    }

    fun onVideoRecordingStarted(onAutoStopRequested: () -> Unit) {
        playDetent()
        videoTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isRecordingVideo = true,
                recordingElapsedSec = 0
            )
        }
        videoTimerJob = viewModelScope.launch {
            for (sec in 1..15) {
                delay(1000L)
                _uiState.update { it.copy(recordingElapsedSec = sec) }
                if (sec >= 15) {
                    onAutoStopRequested()
                }
            }
        }
    }

    fun onVideoRecordingStopped(file: File?) {
        videoTimerJob?.cancel()
        videoTimerJob = null
        playDetent()
        val state = _uiState.value
        val duration = state.recordingElapsedSec.coerceAtLeast(1)
        val filter = state.selectedVideoFilter
        if (file != null && file.exists() && file.length() > 1024L) {
            _uiState.update {
                it.copy(
                    isRecordingVideo = false,
                    recordingElapsedSec = 0,
                    recordedClipPreview = RecordedClipPreview(
                        filePath = file.absolutePath,
                        durationSec = duration,
                        bodyName = state.selectedBody.name,
                        filmName = if (filter == VideoFilterStyle.OPTICAL_CHAIN) state.selectedFilm.name else filter.labelFr,
                        filmCode = if (filter == VideoFilterStyle.OPTICAL_CHAIN) state.selectedFilm.code else filter.shortBadge,
                        aspectLabel = state.selectedBody.aspectRatioSpec.displayLabel,
                        videoFilterLabel = filter.labelFr,
                        videoFilterBadge = filter.shortBadge,
                        fpsLabel = filter.fpsLabel
                    )
                )
            }
        } else {
            _uiState.update { it.copy(isRecordingVideo = false, recordingElapsedSec = 0) }
            showToast("Clip vidéo trop court ou non enregistré.")
        }
    }

    fun dismissRecordedClip() {
        _uiState.update { it.copy(recordedClipPreview = null) }
    }

    fun saveRecordedClipToGallery() {
        val clip = _uiState.value.recordedClipPreview ?: return
        val file = File(clip.filePath)
        if (!file.exists()) return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ExifAndExportManager.exportVideoToMediaStore(
                    context = getApplication(),
                    sourceFile = file,
                    displayName = file.name
                )
            }
            if (uri != null) {
                showToast("Clip Super 8 enregistré dans Movies/Peloche !")
            } else {
                showToast("Impossible d'enregistrer le clip vidéo.")
            }
        }
    }

    fun shareRecordedClip() {
        val clip = _uiState.value.recordedClipPreview ?: return
        val file = File(clip.filePath)
        if (!ExifAndExportManager.shareVideoFile(
                context = getApplication(),
                file = file,
                chooserTitle = "Partager le clip Super 8 (${clip.filmName})"
            )
        ) {
            showToast("Partage vidéo indisponible.")
        }
    }

    fun setUseSampleSceneInViewfinder(useSample: Boolean) {
        _uiState.update { it.copy(useSampleSceneInViewfinder = useSample) }
    }

    fun toggleSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun openDevelopDialog(rollToDevelop: FilmRollEntity? = null, defaultMethod: DevelopmentMethod? = null) {
        val roll = rollToDevelop ?: activeRoll.value
        if (roll == null || roll.shotsTaken <= 0) {
            showToast("Exposez au moins une vue avant de passer au laboratoire.")
            return
        }
        playDetent()
        _uiState.update {
            it.copy(
                showDevelopDialog = true,
                rollPendingDevelopmentId = roll.id,
                showEndOfRollPrompt = false,
                selectedDevMethod = defaultMethod ?: it.selectedDevMethod
            )
        }
    }

    fun dismissDevelopDialog() {
        _uiState.update { it.copy(showDevelopDialog = false, rollPendingDevelopmentId = null) }
    }

    fun selectDevelopmentMethod(method: DevelopmentMethod) {
        playDetent()
        _uiState.update { it.copy(selectedDevMethod = method) }
    }

    fun dismissEndOfRollPrompt() {
        _uiState.update { it.copy(showEndOfRollPrompt = false) }
    }

    /**
     * Shutter Release: Optionally counts down self-timer (3s/10s), captures hardware frame
     * (or Scène Témoin fallback), saves privately as a latent negative, advances frame counter,
     * and prompts Lab development upon finishing the roll.
     */
    fun captureExposure(hardwareBitmapProvider: suspend () -> Bitmap?) {
        val currentRoll = activeRoll.value ?: return
        if (_uiState.value.isCapturing || _uiState.value.selfTimerCountdown > 0) return
        if (currentRoll.isFull) {
            _uiState.update { it.copy(showEndOfRollPrompt = true) }
            showToast("Fin de pellicule (${currentRoll.maxExposures}/${currentRoll.maxExposures}) — rembobinez au Labo !")
            return
        }

        val currentSettings = settings.value
        val timerSec = currentSettings.selfTimerSeconds

        viewModelScope.launch {
            if (timerSec > 0) {
                for (remaining in timerSec downTo 1) {
                    _uiState.update { it.copy(selfTimerCountdown = remaining) }
                    audioEngine.playDialDetent(currentSettings.soundEnabled, currentSettings.hapticsEnabled)
                    delay(1000L)
                }
                _uiState.update { it.copy(selfTimerCountdown = 0) }
            }

            val latestRoll = activeRoll.value ?: return@launch
            val state = _uiState.value
            val s = settings.value

            _uiState.update {
                it.copy(
                    isCapturing = true,
                    shutterFlashTick = it.shutterFlashTick + 1
                )
            }
            audioEngine.playShutterClick(
                soundEnabled = s.soundEnabled,
                hapticsEnabled = s.hapticsEnabled,
                mount = state.selectedBody.mount,
                borderStyle = state.selectedBody.frameBorderStyle
            )

            try {
                val captured: Bitmap = hardwareBitmapProvider() ?: loadSampleBitmap(s.sampleSceneNight)

                // Double Exposure Step 1: store first exposure as translucent viewfinder ghost without advancing film!
                if (state.isDoubleExposureMode && state.doubleExposureFirstShotPath == null) {
                    val tempFile = withContext(Dispatchers.IO) {
                        val dir = File(getApplication<Application>().cacheDir, "double_exp").apply { mkdirs() }
                        val out = File(dir, "first_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(out).use { fos ->
                            captured.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                        out
                    }
                    captured.recycle()
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            doubleExposureFirstShotPath = tempFile.absolutePath
                        )
                    }
                    showToast("Vue 1/2 exposée — cadrez votre surimpression !")
                    return@launch
                }

                // Double Exposure Step 2: optically blend Shot 1 + Shot 2 on the same frame!
                val pendingFirstPath = state.doubleExposureFirstShotPath
                val finalSourceBitmap: Bitmap = if (state.isDoubleExposureMode && pendingFirstPath != null) {
                    val blended = withContext(Dispatchers.IO) {
                        val firstFile = File(pendingFirstPath)
                        val firstBmp = if (firstFile.exists()) BitmapFactory.decodeFile(firstFile.absolutePath) else null
                        firstFile.delete()
                        if (firstBmp != null) {
                            val merged = OffscreenGlProcessor.blendDoubleExposure(firstBmp, captured)
                            firstBmp.recycle()
                            captured.recycle()
                            merged
                        } else {
                            captured
                        }
                    }
                    blended
                } else {
                    captured
                }

                val capturedSeed = state.previewLightLeakSeed
                val (updatedRoll, savedFrame) = repository.recordLatentExposure(
                    roll = latestRoll,
                    sourceBitmap = finalSourceBitmap,
                    lens = state.selectedLens,
                    evCompensation = state.evCompensation,
                    zoomMultiplier = state.zoomMultiplier,
                    dateStampStyle = s.effectiveDateStampStyle,
                    isFrontCamera = state.isFrontCamera,
                    lightLeakSeed = capturedSeed
                )

                val instantPreview = if (s.instantPreviewEnabled) {
                    val result = repository.renderInstantPreview(
                        sourceBitmap = finalSourceBitmap,
                        body = state.selectedBody,
                        lens = state.selectedLens,
                        film = state.selectedFilm,
                        expirationDate = state.selectedExpiration,
                        evCompensation = state.evCompensation,
                        zoomMultiplier = state.zoomMultiplier,
                        dateStampStyle = s.effectiveDateStampStyle,
                        includeFrameBorder = s.frameBorderEnabled,
                        frameNumber = savedFrame.frameNumber,
                        isFrontCamera = state.isFrontCamera,
                        lightLeakSeed = capturedSeed,
                        lightLeakMode = s.lightLeakMode,
                        tiltShiftPosition = state.tiltShiftPosition,
                        tiltShiftVertical = state.tiltShiftVertical
                    )
                    val revealKind = when {
                        state.selectedBody.isInstantCamera -> "POLAROID"
                        state.selectedBody.isHistoricalLargeFormat ||
                            state.selectedFilm.collodionPlateAmount > 0.3f ||
                            state.selectedFilm.daguerreTarnishAmount > 0.3f -> "COLLODION"
                        else -> "STANDARD"
                    }
                    InstantPrintPreview(
                        developedPath = result.developedPath,
                        originalPath = result.originalPath,
                        filmName = state.selectedFilm.name,
                        filmCode = state.selectedFilm.code,
                        bodyName = state.selectedBody.name,
                        lensLabel = "${state.selectedLens.focalLabel} ${state.selectedLens.maxAperture}",
                        caption = "${state.selectedFilm.name} · ${state.selectedBody.name}",
                        isImportedFromGallery = false,
                        frameNumber = savedFrame.frameNumber,
                        frameId = savedFrame.id,
                        revealType = revealKind
                    )
                } else {
                    null
                }
                finalSourceBitmap.recycle()

                delay(180L)
                audioEngine.playAdvanceLever(s.soundEnabled, s.hapticsEnabled)

                val completedDoubleExp = state.isDoubleExposureMode && pendingFirstPath != null
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        previewLightLeakSeed = Random.nextFloat(),
                        doubleExposureFirstShotPath = null,
                        isDoubleExposureMode = if (completedDoubleExp) false else it.isDoubleExposureMode,
                        instantPrintPreview = instantPreview ?: it.instantPrintPreview,
                        showEndOfRollPrompt = updatedRoll.isFull && instantPreview == null
                    )
                }
                if (completedDoubleExp && instantPreview == null) {
                    showToast("Double exposition enregistrée sur la vue #${savedFrame.frameNumber} !")
                }

                if (updatedRoll.isFull) {
                    audioEngine.playRewindSequence(s.soundEnabled, s.hapticsEnabled)
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isCapturing = false, selfTimerCountdown = 0) }
                showToast("Erreur lors de l'exposition.")
            }
        }
    }

    /**
     * Zero-permission Android Photo Picker import: develops an existing gallery photo through
     * the currently mounted Boîtier + Objectif + Pellicule + Péremption + Cadre + Dateur
     * and opens the museum-paper Tirage Galerie overlay with Avant/Après compare!
     */
    fun importPhotoFromUri(uri: Uri) {
        if (_uiState.value.isImportingPhoto) return
        val state = _uiState.value
        val s = settings.value
        _uiState.update { it.copy(isImportingPhoto = true, showSettingsSheet = false) }
        viewModelScope.launch {
            try {
                val decoded = decodeOrientedBitmapFromUri(uri, maxEdge = 2048)
                if (decoded == null) {
                    _uiState.update { it.copy(isImportingPhoto = false) }
                    showToast("Impossible de lire cette photo.")
                    return@launch
                }
                audioEngine.playShutterClick(
                    soundEnabled = s.soundEnabled,
                    hapticsEnabled = s.hapticsEnabled,
                    mount = state.selectedBody.mount,
                    borderStyle = state.selectedBody.frameBorderStyle
                )
                val result = repository.renderInstantPreview(
                    sourceBitmap = decoded,
                    body = state.selectedBody,
                    lens = state.selectedLens,
                    film = state.selectedFilm,
                    expirationDate = state.selectedExpiration,
                    evCompensation = state.evCompensation,
                    zoomMultiplier = 1.0f,
                    dateStampStyle = s.effectiveDateStampStyle,
                    includeFrameBorder = s.frameBorderEnabled,
                    frameNumber = (activeRoll.value?.shotsTaken ?: 0) + 1,
                    isFrontCamera = false,
                    lightLeakSeed = state.previewLightLeakSeed,
                    lightLeakMode = s.lightLeakMode,
                    tiltShiftPosition = state.tiltShiftPosition,
                    tiltShiftVertical = state.tiltShiftVertical
                )
                decoded.recycle()
                val revealKind = when {
                    state.selectedBody.isInstantCamera -> "POLAROID"
                    state.selectedBody.isHistoricalLargeFormat ||
                        state.selectedFilm.collodionPlateAmount > 0.3f ||
                        state.selectedFilm.daguerreTarnishAmount > 0.3f -> "COLLODION"
                    else -> "STANDARD"
                }
                _uiState.update {
                    it.copy(
                        isImportingPhoto = false,
                        instantPrintPreview = InstantPrintPreview(
                            developedPath = result.developedPath,
                            originalPath = result.originalPath,
                            filmName = state.selectedFilm.name,
                            filmCode = state.selectedFilm.code,
                            bodyName = state.selectedBody.name,
                            lensLabel = "${state.selectedLens.focalLabel} ${state.selectedLens.maxAperture}",
                            caption = "${state.selectedFilm.name} · ${state.selectedBody.name}",
                            isImportedFromGallery = true,
                            frameNumber = (activeRoll.value?.shotsTaken ?: 0) + 1,
                            revealType = revealKind
                        )
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isImportingPhoto = false) }
                showToast("Erreur lors du développement de l'import.")
            }
        }
    }

    fun dismissInstantPreview() {
        _uiState.update { it.copy(instantPrintPreview = null) }
    }

    fun updateInstantPreviewNote(note: String) {
        val clean = note.take(72)
        val frameId = _uiState.value.instantPrintPreview?.frameId
        _uiState.update { state ->
            state.copy(instantPrintPreview = state.instantPrintPreview?.copy(handwrittenNote = clean))
        }
        if (frameId != null) {
            viewModelScope.launch { repository.updateFrameCaption(frameId, clean) }
        }
    }

    fun renameRoll(rollId: Long, newTitle: String) {
        playDetent()
        viewModelScope.launch {
            repository.renameRoll(rollId, newTitle)
            val label = newTitle.trim().ifEmpty { "numéro d'archive" }
            showToast("Rouleau renommé : $label")
        }
    }

    fun updateFrameCaption(frameId: Long, caption: String) {
        viewModelScope.launch {
            repository.updateFrameCaption(frameId, caption)
        }
    }

    fun exportInstantPreview(asGalleryCard: Boolean, story916: Boolean = false) {
        val preview = _uiState.value.instantPrintPreview ?: return
        val sourceFile = File(preview.developedPath)
        if (!sourceFile.exists()) return
        val lossless = settings.value.exportLosslessPng
        val prefix = "PELOCHE_${preview.filmCode}_${System.currentTimeMillis() % 100000}"
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (asGalleryCard) {
                    val (_, uri) = ExifAndExportManager.exportGalleryPrintCard(
                        context = getApplication(),
                        sourceFile = sourceFile,
                        caption = preview.caption,
                        filePrefix = prefix,
                        storyFormat916 = story916,
                        losslessPng = lossless,
                        handwrittenNote = preview.handwrittenNote
                    )
                    uri != null
                } else {
                    ExifAndExportManager.exportFrameWithOptionalNote(
                        context = getApplication(),
                        sourceFile = sourceFile,
                        handwrittenNote = preview.handwrittenNote,
                        displayName = "$prefix.${if (lossless) "png" else "jpg"}",
                        losslessPng = lossless
                    ) != null
                }
            }
            if (ok) {
                val label = when {
                    asGalleryCard && story916 -> "Story Galerie 9:16"
                    asGalleryCard -> "Carte Galerie 4:5"
                    else -> "Tirage argentique"
                }
                showToast("$label enregistré dans DCIM/Peloche !")
            } else {
                showToast("Exportation impossible sur cet appareil.")
            }
        }
    }

    fun shareInstantPreview(asGalleryCard: Boolean, story916: Boolean = false) {
        val preview = _uiState.value.instantPrintPreview ?: return
        val sourceFile = File(preview.developedPath)
        if (!sourceFile.exists()) return
        val lossless = settings.value.exportLosslessPng
        val prefix = "PELOCHE_${preview.filmCode}_${System.currentTimeMillis() % 100000}"
        viewModelScope.launch {
            val fileToShare = if (asGalleryCard) {
                withContext(Dispatchers.IO) {
                    ExifAndExportManager.exportGalleryPrintCard(
                        context = getApplication(),
                        sourceFile = sourceFile,
                        caption = preview.caption,
                        filePrefix = prefix,
                        storyFormat916 = story916,
                        losslessPng = lossless,
                        handwrittenNote = preview.handwrittenNote
                    ).first
                }
            } else {
                sourceFile
            }
            if (fileToShare == null || !ExifAndExportManager.shareImageFile(
                    getApplication(),
                    fileToShare,
                    "Partager ${preview.caption}"
                )
            ) {
                showToast("Partage indisponible.")
            }
        }
    }

    /**
     * Rewinds the active roll, runs the full-res chemical development pipeline with the selected
     * DevelopmentMethod, loads a fresh roll with a new random expiration date, and opens the
     * newly developed Contact Sheet!
     */
    fun startLabDevelopment(method: DevelopmentMethod) {
        val roll = rollForDevelopment.value ?: activeRoll.value ?: return
        if (roll.shotsTaken <= 0 || _uiState.value.devProgress.isRunning) return
        val wasCurrentlyHeldRoll = activeRoll.value?.id == roll.id

        val currentSettings = settings.value
        audioEngine.playRewindSequence(currentSettings.soundEnabled, currentSettings.hapticsEnabled)

        _uiState.update {
            it.copy(
                showDevelopDialog = false,
                rollPendingDevelopmentId = null,
                showEndOfRollPrompt = false,
                devProgress = DevelopmentProgressState(
                    isRunning = true,
                    progress = 0.05f,
                    statusText = "Rembobinage mécanique du rouleau…",
                    rollBeingDeveloped = roll
                )
            )
        }

        viewModelScope.launch {
            delay(500L)
            val developedRoll = repository.developRoll(
                roll = roll,
                method = method,
                losslessPng = currentSettings.exportLosslessPng,
                includeFrameBorder = currentSettings.frameBorderEnabled,
                lightLeakMode = currentSettings.lightLeakMode,
                onProgress = { progress, text ->
                    _uiState.update {
                        it.copy(
                            devProgress = it.devProgress.copy(
                                progress = progress,
                                statusText = text
                            )
                        )
                    }
                }
            )

            // Only load a fresh empty roll into the viewfinder if the developed roll was the currently held camera's roll.
            if (wasCurrentlyHeldRoll) {
                val state = _uiState.value
                val freshRoll = repository.loadFreshRoll(
                    body = state.selectedBody,
                    lens = state.selectedLens,
                    film = state.selectedFilm,
                    expirationDate = FilmExpirationDate.randomForNewRoll(),
                    isDiptychMode = currentSettings.diptychEnabled
                )
                syncUiToRoll(freshRoll, notifyIfExpired = false)
            }

            delay(350L)
            _uiState.update {
                it.copy(
                    devProgress = DevelopmentProgressState(isRunning = false)
                )
            }
            showToast("Rouleau N°%03d développé (${method.shortBadge}) et enregistré dans DCIM/Peloche !".format(developedRoll.rollNumber))
            openContactSheet(developedRoll)
        }
    }

    fun exportContactSheetForRoll(roll: FilmRollEntity) {
        val frames = contactSheetFrames.value
        if (frames.isEmpty()) return
        val lossless = settings.value.exportLosslessPng
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ExifAndExportManager.exportContactSheet(getApplication(), roll, frames, lossless)
            }
            if (uri != null) {
                showToast("Planche-contact exportée dans DCIM/Peloche !")
            } else {
                showToast("Exportation impossible sur cet appareil.")
            }
        }
    }

    fun exportSingleFrame(frame: FilmFrameEntity, roll: FilmRollEntity) {
        val path = frame.developedPath ?: return
        val file = File(path)
        if (!file.exists()) return
        val isPng = path.endsWith(".png", ignoreCase = true)
        val film = HardwareCatalog.filmById(frame.filmId)
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ExifAndExportManager.exportFrameWithOptionalNote(
                    context = getApplication(),
                    sourceFile = file,
                    handwrittenNote = frame.caption,
                    displayName = "PELOCHE_R%03d_%s_F%02d.%s".format(
                        roll.rollNumber, film.code, frame.frameNumber, if (isPng) "png" else "jpg"
                    ),
                    losslessPng = isPng
                )
            }
            if (uri != null) {
                showToast("Vue #${frame.frameNumber} exportée dans la galerie !")
            } else {
                showToast("Erreur lors de l'export de la vue.")
            }
        }
    }

    fun exportAllFramesInRoll(roll: FilmRollEntity) {
        val frames = contactSheetFrames.value
        if (frames.isEmpty()) return
        val film = HardwareCatalog.filmById(roll.filmId)
        viewModelScope.launch {
            val exported = withContext(Dispatchers.IO) {
                var ok = 0
                frames.forEach { frame ->
                    val path = frame.developedPath ?: return@forEach
                    val file = File(path)
                    if (!file.exists()) return@forEach
                    val isPng = path.endsWith(".png", ignoreCase = true)
                    val uri = ExifAndExportManager.exportFrameWithOptionalNote(
                        context = getApplication(),
                        sourceFile = file,
                        handwrittenNote = frame.caption,
                        displayName = "PELOCHE_R%03d_%s_F%02d.%s".format(
                            roll.rollNumber, film.code, frame.frameNumber, if (isPng) "png" else "jpg"
                        ),
                        losslessPng = isPng
                    )
                    if (uri != null) ok++
                }
                ok
            }
            if (exported > 0) {
                showToast("$exported photo${if (exported > 1) "s" else ""} exportée${if (exported > 1) "s" else ""} dans DCIM/Peloche !")
            } else {
                showToast("Exportation impossible sur cet appareil.")
            }
        }
    }

    fun shareSingleFrame(frame: FilmFrameEntity, roll: FilmRollEntity) {
        val path = frame.developedPath ?: return
        val file = File(path)
        if (!ExifAndExportManager.shareImageFile(getApplication(), file, "Partager la vue %02dA (${roll.displayTitle})".format(frame.frameNumber))) {
            showToast("Partage indisponible pour cette vue.")
        }
    }

    fun exportGalleryCardForFrame(frame: FilmFrameEntity, roll: FilmRollEntity, story916: Boolean) {
        val path = frame.developedPath ?: return
        val file = File(path)
        if (!file.exists()) return
        val body = HardwareCatalog.bodyById(frame.bodyId)
        val film = HardwareCatalog.filmById(frame.filmId)
        val caption = "${film.name} · ${body.name}"
        val prefix = "PELOCHE_R%03d_%s_F%02d".format(roll.rollNumber, film.code, frame.frameNumber)
        val lossless = settings.value.exportLosslessPng
        viewModelScope.launch {
            val (_, uri) = withContext(Dispatchers.IO) {
                ExifAndExportManager.exportGalleryPrintCard(
                    context = getApplication(),
                    sourceFile = file,
                    caption = caption,
                    filePrefix = prefix,
                    storyFormat916 = story916,
                    losslessPng = lossless,
                    handwrittenNote = frame.caption
                )
            }
            if (uri != null) {
                showToast(if (story916) "Story Galerie 9:16 enregistrée !" else "Carte Galerie 4:5 enregistrée !")
            } else {
                showToast("Exportation impossible sur cet appareil.")
            }
        }
    }

    fun shareGalleryCardForFrame(frame: FilmFrameEntity, roll: FilmRollEntity, story916: Boolean) {
        val path = frame.developedPath ?: return
        val file = File(path)
        if (!file.exists()) return
        val body = HardwareCatalog.bodyById(frame.bodyId)
        val film = HardwareCatalog.filmById(frame.filmId)
        val caption = "${film.name} · ${body.name}"
        val prefix = "PELOCHE_R%03d_%s_F%02d".format(roll.rollNumber, film.code, frame.frameNumber)
        val lossless = settings.value.exportLosslessPng
        viewModelScope.launch {
            val (outFile, _) = withContext(Dispatchers.IO) {
                ExifAndExportManager.exportGalleryPrintCard(
                    context = getApplication(),
                    sourceFile = file,
                    caption = caption,
                    filePrefix = prefix,
                    storyFormat916 = story916,
                    losslessPng = lossless,
                    handwrittenNote = frame.caption
                )
            }
            if (outFile == null || !ExifAndExportManager.shareImageFile(
                    getApplication(),
                    outFile,
                    "Partager Tirage Galerie ($caption)"
                )
            ) {
                showToast("Partage indisponible.")
            }
        }
    }

    fun shareContactSheet(roll: FilmRollEntity) {
        val frames = contactSheetFrames.value
        if (frames.isEmpty()) return
        val lossless = settings.value.exportLosslessPng
        viewModelScope.launch {
            val shareFile = withContext(Dispatchers.IO) {
                ExifAndExportManager.renderContactSheetForShare(getApplication(), roll, frames, lossless)
            }
            if (shareFile == null || !ExifAndExportManager.shareImageFile(
                    getApplication(),
                    shareFile,
                    "Partager la planche-contact N°%03d".format(roll.rollNumber)
                )
            ) {
                showToast("Impossible de partager la planche-contact.")
            }
        }
    }

    fun deleteDevelopedRoll(roll: FilmRollEntity) {
        viewModelScope.launch {
            repository.deleteRoll(roll.id)
            if (_uiState.value.selectedContactRollId == roll.id) {
                _uiState.update { it.copy(currentScreen = AppScreen.LABO, selectedContactRollId = null) }
            }
            showToast("Rouleau N°%03d supprimé.".format(roll.rollNumber))
        }
    }

    // Settings mutations
    fun updateSound(enabled: Boolean) = viewModelScope.launch { settingsStore.setSoundEnabled(enabled) }
    fun updateHaptics(enabled: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(enabled) }
    fun updateGrid(enabled: Boolean) = viewModelScope.launch { settingsStore.setGridEnabled(enabled) }
    fun updateDateStamp(enabled: Boolean) = viewModelScope.launch { settingsStore.setDateStampEnabled(enabled) }
    fun updateDateStampStyle(style: DateStampStyle) = viewModelScope.launch {
        playDetent()
        settingsStore.setDateStampStyle(style)
    }
    fun cycleDateStampStyle() {
        val next = settings.value.effectiveDateStampStyle.next()
        updateDateStampStyle(next)
        showToast("Dateur : ${next.labelFr}")
    }
    fun cycleSelfTimer() {
        playDetent()
        val next = when (settings.value.selfTimerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        viewModelScope.launch { settingsStore.setSelfTimerSeconds(next) }
        showToast(if (next == 0) "Retardateur désactivé" else "Retardateur : ${next}s")
    }
    fun toggleFavoriteFilm(filmId: String) {
        playDetent()
        viewModelScope.launch { settingsStore.toggleFavoriteFilm(filmId) }
    }
    fun updateLosslessPng(enabled: Boolean) = viewModelScope.launch { settingsStore.setExportLosslessPng(enabled) }
    fun updateFrameBorder(enabled: Boolean) = viewModelScope.launch {
        playDetent()
        settingsStore.setFrameBorderEnabled(enabled)
    }
    fun updateDiptych(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setDiptychEnabled(enabled)
        persistRollHardwareUpdate(diptychOverride = enabled)
    }
    fun updateSampleSceneNight(isNight: Boolean) = viewModelScope.launch { settingsStore.setSampleSceneNight(isNight) }
    fun updateInstantPreview(enabled: Boolean) = viewModelScope.launch {
        playDetent()
        settingsStore.setInstantPreviewEnabled(enabled)
    }
    fun updateAnimatedEffects(enabled: Boolean) = viewModelScope.launch {
        playDetent()
        settingsStore.setAnimatedEffectsEnabled(enabled)
    }
    fun updateLightLeakMode(mode: com.example.domain.model.LightLeakMode) = viewModelScope.launch {
        playDetent()
        settingsStore.setLightLeakMode(mode)
        showToast("Fuites de lumière : ${mode.labelFr}")
    }

    // Saved Combos ("Mes Combos" presets)
    fun saveCurrentComboPreset() {
        playDetent()
        val state = _uiState.value
        val s = settings.value
        val preset = SavedComboPreset(
            id = "combo_${System.currentTimeMillis()}",
            name = "${state.selectedFilm.name} · ${state.selectedBody.shortCode}",
            bodyId = state.selectedBody.id,
            lensId = state.selectedLens.id,
            filmId = state.selectedFilm.id,
            dateStampStyleName = s.effectiveDateStampStyle.name
        )
        viewModelScope.launch {
            settingsStore.saveComboPreset(preset)
        }
        showToast("Combo sauvegardé : ${preset.name}")
    }

    fun applyComboPreset(preset: SavedComboPreset) {
        playDetent()
        val body = HardwareCatalog.bodyById(preset.bodyId)
        val rawLens = HardwareCatalog.lensById(preset.lensId)
        val lens = if (HardwareCatalog.isLensCompatible(rawLens, body)) rawLens else HardwareCatalog.defaultLensFor(body)
        val film = HardwareCatalog.filmById(preset.filmId)
        val dateStyle = runCatching { DateStampStyle.valueOf(preset.dateStampStyleName) }
            .getOrDefault(DateStampStyle.QUARTZ_90S)

        val existingForBody = inProgressRollsByBody.value[body.id]
        val diptych = settings.value.diptychEnabled
        viewModelScope.launch {
            settingsStore.setDateStampStyle(dateStyle)
            val switched = repository.switchToBody(
                body = body,
                preferredLens = lens,
                preferredFilm = film,
                preferredExpiration = FilmExpirationDate.freshDefault(),
                isDiptychMode = diptych
            )
            val finalRoll = if (switched.shotsTaken == 0) {
                repository.updateActiveRollHardware(
                    roll = switched,
                    body = body,
                    lens = lens,
                    film = film,
                    expirationDate = switched.expirationDate,
                    isDiptychMode = diptych
                )
            } else {
                repository.updateActiveRollHardware(
                    roll = switched,
                    body = body,
                    lens = lens,
                    film = HardwareCatalog.filmById(switched.filmId),
                    expirationDate = switched.expirationDate,
                    isDiptychMode = diptych
                )
            }
            syncUiToRoll(finalRoll, notifyIfExpired = false)
            if (existingForBody != null && existingForBody.shotsTaken > 0 && existingForBody.filmId != film.id) {
                showToast("${body.shortCode} chargé (${existingForBody.shotsTaken}/${existingForBody.maxExposures}) : objectif & dateur appliqués.")
            } else {
                showToast("Combo appliqué : ${preset.name}")
            }
        }
    }

    fun applyCuratedEraCombo(combo: CuratedEraCombo) {
        playDetent()
        val body = HardwareCatalog.bodyById(combo.bodyId)
        val rawLens = HardwareCatalog.lensById(combo.lensId)
        val lens = if (HardwareCatalog.isLensCompatible(rawLens, body)) rawLens else HardwareCatalog.defaultLensFor(body)
        val film = HardwareCatalog.filmById(combo.filmId)
        val existingForBody = inProgressRollsByBody.value[body.id]
        val diptych = settings.value.diptychEnabled
        viewModelScope.launch {
            val switched = repository.switchToBody(
                body = body,
                preferredLens = lens,
                preferredFilm = film,
                preferredExpiration = FilmExpirationDate.freshDefault(),
                isDiptychMode = diptych
            )
            val finalRoll = if (switched.shotsTaken == 0) {
                repository.updateActiveRollHardware(
                    roll = switched,
                    body = body,
                    lens = lens,
                    film = film,
                    expirationDate = switched.expirationDate,
                    isDiptychMode = diptych
                )
            } else {
                repository.updateActiveRollHardware(
                    roll = switched,
                    body = body,
                    lens = lens,
                    film = HardwareCatalog.filmById(switched.filmId),
                    expirationDate = switched.expirationDate,
                    isDiptychMode = diptych
                )
            }
            syncUiToRoll(finalRoll, notifyIfExpired = false)
            if (existingForBody != null && existingForBody.shotsTaken > 0 && existingForBody.filmId != film.id) {
                showToast("${body.shortCode} déjà chargé (${existingForBody.shotsTaken}/${existingForBody.maxExposures}) : objectif monté.")
            } else {
                showToast("Kit ${combo.eraBadge} monté : ${combo.title}")
            }
        }
    }

    fun toggleGreasePencilFrame(frameId: Long) {
        playDetent()
        viewModelScope.launch {
            settingsStore.toggleGreasePencilFrame(frameId)
        }
    }

    fun deleteComboPreset(presetId: String) {
        playDetent()
        viewModelScope.launch {
            settingsStore.deleteComboPreset(presetId)
        }
        showToast("Combo supprimé.")
    }

    fun clearToast() {
        _uiState.update { it.copy(activeToastMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(activeToastMessage = msg) }
    }

    private fun playDetent() {
        val s = settings.value
        audioEngine.playDialDetent(s.soundEnabled, s.hapticsEnabled)
    }

    private fun persistRollHardwareUpdate(diptychOverride: Boolean? = null) {
        val roll = activeRoll.value ?: return
        val state = _uiState.value
        val diptych = diptychOverride ?: settings.value.diptychEnabled
        viewModelScope.launch {
            repository.updateActiveRollHardware(
                roll = roll,
                body = state.selectedBody,
                lens = state.selectedLens,
                film = state.selectedFilm,
                expirationDate = state.selectedExpiration,
                isDiptychMode = diptych
            )
        }
    }

    private suspend fun loadSampleBitmap(isNight: Boolean): Bitmap = withContext(Dispatchers.IO) {
        val resId = if (isNight) R.drawable.img_sample_scene_night else R.drawable.img_sample_scene_day
        BitmapFactory.decodeResource(getApplication<Application>().resources, resId)
    }

    private suspend fun decodeOrientedBitmapFromUri(uri: Uri, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
        }
        val curMax = maxOf(raw.width, raw.height)
        if (curMax > maxEdge) {
            val s = maxEdge.toFloat() / curMax.toFloat()
            matrix.postScale(s, s)
        }
        if (matrix.isIdentity) {
            raw
        } else {
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (rotated !== raw) raw.recycle()
            rotated
        }
    }
}
