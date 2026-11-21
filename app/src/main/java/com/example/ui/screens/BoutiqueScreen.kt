package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CameraSettings
import com.example.data.local.FilmRollEntity
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.CuratedEraCombo
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmMood
import com.example.domain.model.FilmSortOption
import com.example.domain.model.FilmStockModel
import com.example.domain.model.FilmTypeFilter
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.LensModel
import com.example.domain.model.SavedComboPreset
import com.example.engine.CameraSurfaceHolder
import com.example.ui.components.AnalogFrameContour
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

@Composable
fun BoutiqueScreen(
    uiState: ArgentiqueUiState,
    settings: CameraSettings,
    activeRoll: FilmRollEntity?,
    inProgressRollsByBody: Map<String, FilmRollEntity>,
    hasCameraPermission: Boolean,
    previewUseCamera: Boolean,
    onSetPreviewUseCamera: (Boolean) -> Unit,
    onSelectTab: (BoutiqueFocusTab) -> Unit,
    onBack: () -> Unit,
    onSelectBody: (CameraBodyModel) -> Unit,
    onSelectLens: (LensModel) -> Unit,
    onSelectFilm: (FilmStockModel) -> Unit,
    onRandomizeBody: () -> Unit,
    onRandomizeLens: () -> Unit,
    onRandomizeFilm: () -> Unit,
    onRandomizeFullCombo: () -> Unit,
    onSelectFilmType: (FilmTypeFilter) -> Unit,
    onSelectMood: (FilmMood) -> Unit,
    onSelectFilmSort: (FilmSortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleFavoriteFilm: (String) -> Unit,
    onResetFilmFilters: () -> Unit,
    onSelectExpiration: (FilmExpirationDate) -> Unit,
    onRandomizeExpiration: () -> Unit,
    onToggleSampleNight: (Boolean) -> Unit,
    onSurfaceTextureReady: (CameraSurfaceHolder) -> Unit,
    onSurfaceTextureDestroyed: (CameraSurfaceHolder) -> Unit,
    onOpenLaboToRewind: () -> Unit,
    onSaveCurrentCombo: () -> Unit,
    onApplyComboPreset: (SavedComboPreset) -> Unit,
    onDeleteComboPreset: (String) -> Unit,
    onApplyCuratedEraCombo: (CuratedEraCombo) -> Unit
) {
    val activeTab = uiState.boutiqueFocusTab
    val rollLocked = (activeRoll?.shotsTaken ?: 0) > 0
    val activeBagRollCount = inProgressRollsByBody.size
    val favoriteIds = settings.favoriteFilmIds
    var secondaryFiltersExpanded by remember { mutableStateOf(false) }

    val compatibleLenses = remember(uiState.selectedBody.id) {
        HardwareCatalog.compatibleLensesFor(uiState.selectedBody)
    }
    val filmCountsByType = remember {
        FilmTypeFilter.entries.associateWith { type ->
            HardwareCatalog.films.count { type.matches(it) }
        }
    }
    val filteredFilms = remember(
        uiState.selectedFilmTypeFilter,
        uiState.selectedMoodFilter,
        uiState.selectedFilmSort,
        uiState.filmSearchQuery,
        uiState.showFavoritesOnly,
        favoriteIds
    ) {
        val q = uiState.filmSearchQuery.trim()
        val matching = HardwareCatalog.films.filter { film ->
            uiState.selectedFilmTypeFilter.matches(film) &&
                (uiState.selectedMoodFilter == FilmMood.ALL || film.mood == uiState.selectedMoodFilter) &&
                (!uiState.showFavoritesOnly || film.id in favoriteIds) &&
                (q.isEmpty() ||
                    film.name.contains(q, ignoreCase = true) ||
                    film.code.contains(q, ignoreCase = true) ||
                    film.tagline.contains(q, ignoreCase = true) ||
                    "iso ${film.iso}".contains(q, ignoreCase = true))
        }
        uiState.selectedFilmSort.sort(matching)
    }
    val hasSecondaryFilterActive = uiState.selectedMoodFilter != FilmMood.ALL ||
        uiState.selectedFilmSort != FilmSortOption.CATALOG ||
        uiState.selectedExpiration.isExpired
    val hasActiveFilmFilter = uiState.selectedFilmTypeFilter != FilmTypeFilter.ALL ||
        uiState.selectedMoodFilter != FilmMood.ALL ||
        uiState.selectedFilmSort != FilmSortOption.CATALOG ||
        uiState.showFavoritesOnly ||
        uiState.filmSearchQuery.isNotBlank()

    val bodiesGridState = rememberLazyGridState()
    val lensesGridState = rememberLazyGridState()
    val filmsGridState = rememberLazyGridState()

    LaunchedEffect(uiState.selectedBody.id, activeTab) {
        if (activeTab == BoutiqueFocusTab.BODY) {
            val idx = HardwareCatalog.bodies.indexOfFirst { it.id == uiState.selectedBody.id }
            if (idx >= 0) bodiesGridState.animateScrollToItem(idx)
        }
    }
    LaunchedEffect(uiState.selectedLens.id, activeTab, compatibleLenses) {
        if (activeTab == BoutiqueFocusTab.LENS) {
            val idx = compatibleLenses.indexOfFirst { it.id == uiState.selectedLens.id }
            if (idx >= 0) lensesGridState.animateScrollToItem(idx)
        }
    }
    LaunchedEffect(uiState.selectedFilm.id, activeTab) {
        if (activeTab == BoutiqueFocusTab.FILM && uiState.filmSearchQuery.isBlank()) {
            val idx = filteredFilms.indexOfFirst { it.id == uiState.selectedFilm.id }
            if (idx >= 0) filmsGridState.animateScrollToItem(idx + 3)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blackLeatherTexture()
    ) {
        // ==================== 1. MACHINED METAL TOP HEADER BAR ====================
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
                .displayCutoutPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp)
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .brushedMetalPlate(shape = CircleShape, borderColor = HairlineMetal, elevated = true)
                        .testTag("boutique_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = SilverChrome
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Sac Photo",
                        style = MaterialTheme.typography.titleLarge,
                        color = FilmCream,
                        maxLines = 1
                    )
                    Text(
                        text = when {
                            activeBagRollCount > 0 -> "$activeBagRollCount PELLICULE${if (activeBagRollCount > 1) "S" else ""} EN COURS"
                            else -> "APERÇU OPTIQUE EN DIRECT"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (activeBagRollCount > 0) KodakAmber else BrassAccent,
                        maxLines = 1
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .brushedMetalPlate(
                                shape = RoundedCornerShape(24.dp),
                                borderColor = BrassAccent.copy(alpha = 0.72f),
                                elevated = true
                            )
                            .clickable { onRandomizeFullCombo() }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                            .testTag("boutique_randomize_all_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Casino,
                            contentDescription = "Combo aléatoire",
                            tint = BrassAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Surprise",
                            style = MaterialTheme.typography.labelSmall,
                            color = FilmCream,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BrassAccent)
                            .testTag("boutique_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Valider la configuration",
                            tint = MatteLeather
                        )
                    }
                }
            }
        }

        // ==================== 2..5. ADAPTIVE PORTRAIT / LANDSCAPE BODY ====================
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isLandscape = maxWidth > maxHeight

            val previewSection: @Composable (Modifier, Boolean) -> Unit = { cardModifier, fillVertical ->
                Column(
                    modifier = cardModifier.brushedMetalPlate(shape = RoundedCornerShape(14.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fillVertical) Modifier.weight(1f) else Modifier.height(190.dp))
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(Color(0xFF07080B)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnalogFrameContour(
                            body = uiState.selectedBody,
                            film = uiState.selectedFilm,
                            frameNumber = (activeRoll?.shotsTaken ?: 0) + 1,
                            borderEnabled = settings.frameBorderEnabled,
                            compact = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            LiveOpticalPreviewSurface(
                                params = uiState.liveOpticalParams,
                                useCameraStream = previewUseCamera && hasCameraPermission,
                                sampleSceneNight = settings.sampleSceneNight,
                                animatedEffectsEnabled = settings.animatedEffectsEnabled,
                                onSurfaceTextureReady = onSurfaceTextureReady,
                                onSurfaceTextureDestroyed = onSurfaceTextureDestroyed,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Preview Source Switcher Pills (Jour / Nuit / Live)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0B0B0D))
                                .border(1.dp, HairlineMetal, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PreviewSourcePill(
                                label = "JOUR",
                                icon = Icons.Filled.WbSunny,
                                selected = !previewUseCamera && !settings.sampleSceneNight,
                                onClick = {
                                    onSetPreviewUseCamera(false)
                                    onToggleSampleNight(false)
                                }
                            )
                            PreviewSourcePill(
                                label = "NUIT",
                                icon = Icons.Filled.DarkMode,
                                selected = !previewUseCamera && settings.sampleSceneNight,
                                onClick = {
                                    onSetPreviewUseCamera(false)
                                    onToggleSampleNight(true)
                                }
                            )
                            if (hasCameraPermission) {
                                PreviewSourcePill(
                                    label = "LIVE",
                                    icon = Icons.Filled.Videocam,
                                    selected = previewUseCamera,
                                    onClick = { onSetPreviewUseCamera(true) }
                                )
                            }
                        }

                        // Format + Focus mode pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC0B0B0D))
                                .border(1.dp, HairlineMetal, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${uiState.selectedBody.aspectRatioSpec.formatBadge} • ${uiState.selectedBody.focusModeLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (uiState.selectedBody.isFixedFocusToyCam) KodakAmber else BrassAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Docked camera LCD data strip directly below the frame
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(Color(0xFF0C0D11))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.selectedBody.shortCode}  •  ${uiState.selectedLens.shortCode}  •  ${uiState.selectedFilm.name} (${uiState.selectedFilm.iso})",
                            style = MaterialTheme.typography.labelSmall,
                            color = FilmCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.selectedExpiration.stampCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.selectedExpiration.isExpired) KodakAmber else PositiveGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val catalogSection: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(modifier = paneModifier) {
                    // ==================== 2b. SAVED COMBOS ("MES COMBOS") QUICK STRIP ====================
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("boutique_saved_combos_row"),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item(key = "save_current_combo") {
                            Row(
                                modifier = Modifier
                                    .brushedMetalPlate(
                                        shape = RoundedCornerShape(8.dp),
                                        borderColor = BrassAccent.copy(alpha = 0.7f),
                                        elevated = true
                                    )
                                    .clickable { onSaveCurrentCombo() }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                                    .testTag("save_current_combo_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = "Sauver ce combo",
                                    tint = BrassAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Sauver combo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = FilmCream,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(settings.savedCombos, key = { it.id }) { preset ->
                            Row(
                                modifier = Modifier
                                    .recessedInstrumentWell(
                                        shape = RoundedCornerShape(8.dp),
                                        borderColor = HairlineMetal
                                    )
                                    .clickable { onApplyComboPreset(preset) }
                                    .padding(start = 9.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                                    .testTag("combo_preset_${preset.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = SilverChrome,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable { onDeleteComboPreset(preset.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Supprimer le combo",
                                        tint = WarmEngraving,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }

                        // Curated Era Kits (1842 Daguerreotype, 1855 Collodion, 1907 Autochrome, 1974 Riviera, 1998 XPan, 1982 Holga)
                        items(HardwareCatalog.curatedEraCombos, key = { "era_${it.id}" }) { era ->
                            val isActiveEra = uiState.selectedBody.id == era.bodyId && uiState.selectedFilm.id == era.filmId
                            val accentCol = Color(era.accentHex)
                            Row(
                                modifier = Modifier
                                    .brushedMetalPlate(
                                        shape = RoundedCornerShape(8.dp),
                                        borderColor = if (isActiveEra) accentCol else accentCol.copy(alpha = 0.42f),
                                        borderWidth = if (isActiveEra) 1.3.dp else 0.8.dp,
                                        elevated = isActiveEra
                                    )
                                    .clickable { onApplyCuratedEraCombo(era) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("curated_era_${era.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = era.eraBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = accentCol,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = era.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isActiveEra) FilmCream else SilverChrome,
                                    fontWeight = if (isActiveEra) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // ==================== 3. HARDWARE CATEGORY TABS ====================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
            BoutiqueTabPill(
                label = if (activeBagRollCount > 0) "Boîtiers • $activeBagRollCount" else "Boîtiers (${HardwareCatalog.bodies.size})",
                selected = activeTab == BoutiqueFocusTab.BODY,
                testTag = "boutique_tab_body",
                onClick = { onSelectTab(BoutiqueFocusTab.BODY) },
                modifier = Modifier.weight(1f)
            )
            BoutiqueTabPill(
                label = "Objectifs (${compatibleLenses.size})",
                selected = activeTab == BoutiqueFocusTab.LENS,
                testTag = "boutique_tab_lens",
                onClick = { onSelectTab(BoutiqueFocusTab.LENS) },
                modifier = Modifier.weight(1f)
            )
            BoutiqueTabPill(
                label = "Films (${HardwareCatalog.films.size})",
                selected = activeTab == BoutiqueFocusTab.FILM,
                testTag = "boutique_tab_film",
                onClick = { onSelectTab(BoutiqueFocusTab.FILM) },
                modifier = Modifier.weight(1f)
            )
        }

        // Contextual mid-roll bar with 1-tap "Changer de boîtier" action on FILM tab
        if (rollLocked && activeRoll != null && activeTab == BoutiqueFocusTab.FILM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .brushedMetalPlate(
                        shape = RoundedCornerShape(9.dp),
                        borderColor = KodakAmber.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = KodakAmber,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${uiState.selectedBody.shortCode} chargé (${activeRoll.shotsTaken}/${activeRoll.maxExposures})",
                    style = MaterialTheme.typography.bodySmall,
                    color = FilmCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrassAccent)
                        .clickable { onSelectTab(BoutiqueFocusTab.BODY) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("lock_banner_switch_body_button")
                ) {
                    Text(
                        text = "Changer de boîtier",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MatteLeather,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AnodizedSlate)
                        .border(1.dp, HairlineMetal, RoundedCornerShape(6.dp))
                        .clickable { onOpenLaboToRewind() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Labo",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = SilverChrome,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (rollLocked && activeRoll != null && activeTab == BoutiqueFocusTab.LENS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .brushedMetalPlate(
                        shape = RoundedCornerShape(9.dp),
                        borderColor = OpticalTeal.copy(alpha = 0.55f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = OpticalTeal,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${uiState.selectedBody.mount.labelFr} • Changement d'objectif libre sans changer de pellicule (${activeRoll.shotsTaken}/${activeRoll.maxExposures}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = FilmCream,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ==================== 4. ACTIVE SHELF HEADER + PER-CATEGORY RANDOM BUTTON ====================
        val sectionTitle = when (activeTab) {
            BoutiqueFocusTab.BODY -> "${uiState.selectedBody.name} • ${uiState.selectedBody.focusModeLabel}"
            BoutiqueFocusTab.LENS -> "${uiState.selectedBody.mount.labelFr} • ${uiState.selectedLens.name}"
            BoutiqueFocusTab.FILM -> "${uiState.selectedFilm.name} • ${uiState.selectedFilm.emulsionType.labelFr}"
        }
        val randomAction = when (activeTab) {
            BoutiqueFocusTab.BODY -> onRandomizeBody
            BoutiqueFocusTab.LENS -> onRandomizeLens
            BoutiqueFocusTab.FILM -> onRandomizeFilm
        }
        val randomTestTag = when (activeTab) {
            BoutiqueFocusTab.BODY -> "randomize_body_button"
            BoutiqueFocusTab.LENS -> "randomize_lens_button"
            BoutiqueFocusTab.FILM -> "randomize_film_button"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelMedium,
                color = SilverChrome,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .brushedMetalPlate(
                        shape = RoundedCornerShape(8.dp),
                        borderColor = BrassAccent.copy(alpha = 0.65f),
                        elevated = true
                    )
                    .clickable { randomAction() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag(randomTestTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Casino,
                    contentDescription = "Aléatoire",
                    tint = BrassAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Aléatoire",
                    style = MaterialTheme.typography.labelSmall,
                    color = FilmCream,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==================== 5. SCROLLABLE HARDWARE GRID ====================
        when (activeTab) {
            BoutiqueFocusTab.BODY -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = bodiesGridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .testTag("boutique_bodies_carousel"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (inProgressRollsByBody.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = "EN COURS DANS VOTRE SAC",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = KodakAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    modifier = Modifier.testTag("bag_in_progress_quick_strip")
                                ) {
                                    items(inProgressRollsByBody.values.toList(), key = { "bag_chip_${it.id}" }) { roll ->
                                        val bagBody = HardwareCatalog.bodyById(roll.bodyId)
                                        val bagFilm = HardwareCatalog.filmById(roll.filmId)
                                        val isCurrent = bagBody.id == uiState.selectedBody.id
                                        Row(
                                            modifier = Modifier
                                                .brushedMetalPlate(
                                                    shape = RoundedCornerShape(10.dp),
                                                    borderColor = when {
                                                        isCurrent -> BrassAccent
                                                        roll.isFull -> LeicaCrimson.copy(alpha = 0.8f)
                                                        else -> KodakAmber.copy(alpha = 0.65f)
                                                    },
                                                    borderWidth = if (isCurrent) 1.5.dp else 1.dp,
                                                    elevated = isCurrent
                                                )
                                                .clickable { onSelectBody(bagBody) }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CameraRoll,
                                                contentDescription = null,
                                                tint = if (roll.isFull) LeicaCrimson else KodakAmber,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "${bagBody.shortCode} • ${roll.shotsTaken}/${roll.maxExposures}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = FilmCream,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = bagFilm.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = WarmEngraving,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(HardwareCatalog.bodies, key = { it.id }) { body ->
                        val selected = body.id == uiState.selectedBody.id
                        val loadedRoll = inProgressRollsByBody[body.id]
                        val loadedFilm = loadedRoll?.let { HardwareCatalog.filmById(it.filmId) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedMetalPlate(
                                    shape = RoundedCornerShape(12.dp),
                                    borderColor = when {
                                        selected -> BrassAccent
                                        loadedRoll?.isFull == true -> LeicaCrimson.copy(alpha = 0.85f)
                                        loadedRoll != null -> KodakAmber.copy(alpha = 0.75f)
                                        else -> HairlineMetal
                                    },
                                    borderWidth = if (selected) 1.8.dp else if (loadedRoll != null) 1.2.dp else 1.dp,
                                    elevated = selected
                                )
                                .clickable { onSelectBody(body) }
                                .padding(10.dp)
                                .testTag("body_card_${body.id}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(if (loadedRoll != null) 14.dp else 2.dp))
                                CameraBodyThumbnail(body = body, modifier = Modifier.size(width = 82.dp, height = 52.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = body.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = FilmCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${body.aspectRatioSpec.formatBadge} • ${body.maxExposures}p • ${body.focusModeLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (body.isFixedFocusToyCam) KodakAmber else BrassAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (loadedRoll != null && loadedFilm != null) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (loadedRoll.isFull) "COMPLET • ${loadedFilm.name}" else "EN COURS • ${loadedFilm.name}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (loadedRoll.isFull) LeicaCrimson else KodakAmber,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = body.signatureTag,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = WarmEngraving,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (loadedRoll != null) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (loadedRoll.isFull) LeicaCrimson else KodakAmber)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("body_in_progress_badge_${body.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraRoll,
                                        contentDescription = "Pellicule en cours",
                                        tint = if (loadedRoll.isFull) FilmCream else MatteLeather,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${loadedRoll.shotsTaken}/${loadedRoll.maxExposures}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (loadedRoll.isFull) FilmCream else MatteLeather,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BoutiqueFocusTab.LENS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = lensesGridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .testTag("boutique_lenses_carousel"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(compatibleLenses, key = { it.id }) { lens ->
                        val selected = lens.id == uiState.selectedLens.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedMetalPlate(
                                    shape = RoundedCornerShape(12.dp),
                                    borderColor = if (selected) BrassAccent else HairlineMetal,
                                    borderWidth = if (selected) 1.8.dp else 1.dp,
                                    elevated = selected
                                )
                                .clickable { onSelectLens(lens) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LensThumbnail(lens = lens, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${lens.focalLabel} ${lens.maxAperture}",
                                style = MaterialTheme.typography.titleSmall,
                                color = FilmCream,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = lens.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrassAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (uiState.selectedBody.isFixedFocusToyCam) "Focale fixe • Sans AF" else lens.characterTag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = WarmEngraving,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            BoutiqueFocusTab.FILM -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = filmsGridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .testTag("boutique_films_carousel"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // 1. Compact Search Field + Collapsible "Filtres & Lot" Toggle
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Rechercher une pellicule",
                                    tint = WarmEngraving,
                                    modifier = Modifier.size(16.dp)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (uiState.filmSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Rechercher (Portra, Tri-X, 400…)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarmEngraving,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.filmSearchQuery,
                                        onValueChange = onSearchQueryChange,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = FilmCream),
                                        cursorBrush = SolidColor(BrassAccent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("film_search_input")
                                    )
                                }
                                if (uiState.filmSearchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Effacer la recherche",
                                        tint = SilverChrome,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onSearchQueryChange("") }
                                    )
                                }
                            }

                            // Collapsible Secondary Filters ("Filtres & Lot") Button
                            Row(
                                modifier = Modifier
                                    .brushedMetalPlate(
                                        shape = RoundedCornerShape(10.dp),
                                        borderColor = if (secondaryFiltersExpanded || hasSecondaryFilterActive) BrassAccent else HairlineMetal,
                                        elevated = true
                                    )
                                    .clickable { secondaryFiltersExpanded = !secondaryFiltersExpanded }
                                    .padding(horizontal = 9.dp, vertical = 8.dp)
                                    .testTag("toggle_secondary_filters_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Filtres avancés et lot",
                                    tint = if (hasSecondaryFilterActive) BrassAccent else SilverChrome,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Tri & Lot",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSecondaryFilterActive) BrassAccent else FilmCream,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (secondaryFiltersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = SilverChrome,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // 2. Primary 1-tap Emulsion & Favorites Row (★ Favoris + Toutes / N&B / Couleur / Diapo / Ciné)
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("film_type_filters"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    val favActive = uiState.showFavoritesOnly
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (favActive) KodakAmber else AnodizedSlate)
                                            .border(
                                                1.dp,
                                                if (favActive) KodakAmber else HairlineMetal,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .clickable { onToggleFavoritesOnly() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("film_filter_favorites"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Favoris",
                                            tint = if (favActive) MatteLeather else KodakAmber,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Favoris (${favoriteIds.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (favActive) MatteLeather else SilverChrome,
                                            fontWeight = if (favActive) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }

                                items(FilmTypeFilter.entries) { type ->
                                    val active = type == uiState.selectedFilmTypeFilter
                                    val count = filmCountsByType[type] ?: 0
                                    val activeColor = when (type) {
                                        FilmTypeFilter.BW -> SilverChrome
                                        FilmTypeFilter.SLIDE -> OpticalTeal
                                        FilmTypeFilter.CINEMA -> KodakAmber
                                        else -> BrassAccent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (active) activeColor else AnodizedSlate)
                                            .border(
                                                1.dp,
                                                if (active) activeColor else HairlineMetal,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .clickable { onSelectFilmType(type) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("film_type_${type.name}")
                                    ) {
                                        Text(
                                            text = "${type.labelFr} ($count)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) MatteLeather else SilverChrome,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${filteredFilms.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (hasActiveFilmFilter) BrassAccent else WarmEngraving,
                                fontWeight = FontWeight.Bold
                            )
                            if (hasActiveFilmFilter) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(AnodizedSlate)
                                        .border(1.dp, HairlineMetal, RoundedCornerShape(7.dp))
                                        .clickable { onResetFilmFilters() }
                                        .padding(5.dp)
                                        .testTag("reset_film_filters_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Réinitialiser les filtres",
                                        tint = SilverChrome,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Collapsible Secondary Filters Drawer (Sort + Mood + Expiration Lot)
                    item(span = { GridItemSpan(2) }) {
                        AnimatedVisibility(visible = secondaryFiltersExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sort Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapVert,
                                        contentDescription = "Trier les pellicules",
                                        tint = BrassAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    LazyRow(
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("film_sort_options"),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        items(FilmSortOption.entries) { option ->
                                            val active = option == uiState.selectedFilmSort
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (active) MachinedSurface else AnodizedSlate)
                                                    .border(
                                                        1.dp,
                                                        if (active) BrassAccent else HairlineMetal,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { onSelectFilmSort(option) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .testTag("film_sort_${option.name}")
                                            ) {
                                                Text(
                                                    text = option.labelFr,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = if (active) BrassAccent else WarmEngraving,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                // Mood Row
                                val visibleMoods = remember(uiState.selectedFilmTypeFilter) {
                                    if (uiState.selectedFilmTypeFilter == FilmTypeFilter.BW) {
                                        listOf(FilmMood.ALL, FilmMood.BW, FilmMood.EXPERIMENTAL)
                                    } else {
                                        FilmMood.entries.filter { it != FilmMood.BW }
                                    }
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.testTag("film_mood_filters")
                                ) {
                                    items(visibleMoods) { mood ->
                                        val active = mood == uiState.selectedMoodFilter
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (active) BrassAccent.copy(alpha = 0.22f) else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (active) BrassAccent else HairlineMetal.copy(alpha = 0.7f),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable { onSelectMood(mood) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (mood == FilmMood.ALL) "Ambiance : Toutes" else mood.labelFr,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (active) BrassAccent else WarmEngraving,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                // Expiration Batch Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedInstrumentWell(shape = RoundedCornerShape(9.dp))
                                        .padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "LOT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = WarmEngraving,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(FilmExpirationDate.presets) { (label, preset) ->
                                            val isMatch = uiState.selectedExpiration.expYear == preset.expYear
                                            val isExp = preset.isExpired
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(
                                                        if (isMatch) (if (isExp) KodakAmber else PositiveGreen)
                                                        else AnodizedSlate
                                                    )
                                                    .clickable { onSelectExpiration(preset) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = if (isMatch) MatteLeather else SilverChrome,
                                                    fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(AnodizedSlate)
                                            .border(1.dp, HairlineMetal, RoundedCornerShape(7.dp))
                                            .clickable { onRandomizeExpiration() }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                            .testTag("randomize_expiration_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Casino,
                                            contentDescription = "Lot aléatoire",
                                            tint = BrassAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty filter fallback
                    if (filteredFilms.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (uiState.showFavoritesOnly && favoriteIds.isEmpty()) {
                                        "Aucun film en favoris. Appuyez sur l'étoile d'une pellicule pour l'épingler !"
                                    } else {
                                        "Aucune pellicule ne correspond à ces critères."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarmEngraving
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrassAccent)
                                        .clickable { onResetFilmFilters() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "Afficher les ${HardwareCatalog.films.size} pellicules",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MatteLeather,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Film Canisters Grid with Favorite Toggle & Locked-Roll Dimming
                    items(filteredFilms, key = { it.id }) { film ->
                        val selected = film.id == uiState.selectedFilm.id
                        val isFav = film.id in favoriteIds
                        val dimmed = rollLocked && !selected

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (dimmed) 0.56f else 1f)
                                .brushedMetalPlate(
                                    shape = RoundedCornerShape(12.dp),
                                    borderColor = if (selected) BrassAccent else HairlineMetal,
                                    borderWidth = if (selected) 1.8.dp else 1.dp,
                                    elevated = selected
                                )
                                .clickable { onSelectFilm(film) }
                                .padding(9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilmCanisterThumbnail(
                                    film = film,
                                    expirationDate = if (selected) uiState.selectedExpiration else null,
                                    modifier = Modifier.size(width = 38.dp, height = 46.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 18.dp)
                                ) {
                                    Text(
                                        text = film.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FilmCream,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ISO ${film.iso} • ${film.emulsionType.processCode}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = BrassAccent
                                    )
                                    Text(
                                        text = film.tagline,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = WarmEngraving,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Favorite Star Toggle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .clickable { onToggleFavoriteFilm(film.id) }
                                    .padding(3.dp)
                                    .testTag("fav_film_${film.id}")
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = if (isFav) "Retirer des favoris" else "Ajouter aux favoris",
                                    tint = if (isFav) KodakAmber else WarmEngraving.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    previewSection(
                        Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .navigationBarsPadding(),
                        true
                    )
                    catalogSection(
                        Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    previewSection(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        false
                    )
                    catalogSection(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BoutiqueTabPill(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrassAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MatteLeather else SilverChrome,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewSourcePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrassAccent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MatteLeather else SilverChrome,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (selected) MatteLeather else SilverChrome,
            fontWeight = FontWeight.Bold
        )
    }
}
