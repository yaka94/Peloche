package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.FilmRollEntity
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.EmulsionType
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.OpticalChainParams
import com.example.ui.components.DevelopedRollArchiveCard
import com.example.ui.components.FilmCanisterThumbnail
import com.example.ui.components.LabStampTag
import com.example.ui.components.LiveOpticalPreviewSurface
import com.example.ui.components.MachinedScrewHead
import com.example.ui.components.RenameRollDialog
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
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.PositiveGreen
import com.example.ui.theme.SafelightRed
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import com.example.viewmodel.DevelopmentProgressState
import java.text.SimpleDateFormat
import java.util.Locale

private enum class LabTab { DEVELOPED_ROLLS, DARKROOM }

private enum class RollFilter(val label: String) {
    ALL("Toutes"),
    COLOR("Couleur"),
    BW("N&B"),
    EXPIRED("Périmées"),
    SPECIAL_DEV("Procédés spéciaux")
}

@Composable
fun LaboScreen(
    activeRoll: FilmRollEntity?,
    inProgressRolls: List<FilmRollEntity>,
    developedRolls: List<FilmRollEntity>,
    developedRollPreviews: Map<Long, List<String>>,
    onBack: () -> Unit,
    onOpenDevelopSheet: (FilmRollEntity) -> Unit,
    onResumeRollInViewfinder: (FilmRollEntity) -> Unit,
    onOpenContactSheet: (FilmRollEntity) -> Unit,
    onRenameRoll: (FilmRollEntity, String) -> Unit,
    onDeleteRoll: (FilmRollEntity) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.FRENCH) }
    var selectedTab by remember {
        mutableStateOf(
            if (developedRolls.isEmpty() && inProgressRolls.isNotEmpty()) LabTab.DARKROOM
            else LabTab.DEVELOPED_ROLLS
        )
    }
    var activeFilter by remember { mutableStateOf(RollFilter.ALL) }
    var sortNewestFirst by remember { mutableStateOf(true) }
    var rollPendingDelete by remember { mutableStateOf<FilmRollEntity?>(null) }
    var rollPendingRename by remember { mutableStateOf<FilmRollEntity?>(null) }

    val totalPrints = remember(developedRolls) { developedRolls.sumOf { it.shotsTaken } }

    val visibleRolls = remember(developedRolls, activeFilter, sortNewestFirst) {
        val filtered = developedRolls.filter { roll ->
            val film = HardwareCatalog.filmById(roll.filmId)
            when (activeFilter) {
                RollFilter.ALL -> true
                RollFilter.COLOR -> film.emulsionType != EmulsionType.BLACK_AND_WHITE
                RollFilter.BW -> film.emulsionType == EmulsionType.BLACK_AND_WHITE
                RollFilter.EXPIRED -> roll.expirationDate.isExpired
                RollFilter.SPECIAL_DEV -> roll.developmentMethod != DevelopmentMethod.STANDARD_C41
            }
        }
        if (sortNewestFirst) {
            filtered.sortedByDescending { it.developedAt ?: it.createdAt }
        } else {
            filtered.sortedBy { it.rollNumber }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blackLeatherTexture()
    ) {
        // ==================== 1. MACHINED BRUSHED-METAL TOP PLATE ====================
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
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            MachinedScrewHead(
                size = 8.dp,
                modifier = Modifier.align(Alignment.TopStart)
            )
            MachinedScrewHead(
                size = 8.dp,
                modifier = Modifier.align(Alignment.TopEnd)
            )

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
                        .brushedMetalPlate(
                            shape = CircleShape,
                            borderColor = HairlineMetal,
                            elevated = true
                        )
                        .testTag("labo_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = SilverChrome
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Peloche",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                            color = FilmCream
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(BrassAccent)
                        )
                        Text(
                            text = "LABO",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = BrassAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Recessed glass digital counter
                    Box(
                        modifier = Modifier
                            .recessedInstrumentWell(shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${developedRolls.size} ROULEAUX  •  $totalPrints TIRAGES",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                            color = SilverChrome
                        )
                    }
                }

                val primaryReadyRoll = inProgressRolls.firstOrNull()
                if (primaryReadyRoll != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(BrassAccent, Color(0xFFD49E26))
                                )
                            )
                            .border(1.dp, Color(0xFFFFE184), RoundedCornerShape(22.dp))
                            .clickable { onOpenDevelopSheet(primaryReadyRoll) }
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                            .testTag("header_develop_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = "Développer",
                            tint = MatteLeather,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (inProgressRolls.size > 1) "${inProgressRolls.size} bains" else "${primaryReadyRoll.shotsTaken}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatteLeather,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ==================== 2. MACHINED MECHANICAL ROCKER TABS ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .recessedInstrumentWell(shape = RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LabTabButton(
                label = "Pellicules (${developedRolls.size})",
                icon = Icons.Filled.PhotoLibrary,
                selected = selectedTab == LabTab.DEVELOPED_ROLLS,
                accentLed = BrassAccent,
                testTag = "lab_tab_developed",
                onClick = { selectedTab = LabTab.DEVELOPED_ROLLS },
                modifier = Modifier.weight(1f)
            )
            LabTabButton(
                label = if (inProgressRolls.isNotEmpty()) "En cours (${inProgressRolls.size})" else "Chambre Noire",
                icon = Icons.Filled.Science,
                selected = selectedTab == LabTab.DARKROOM,
                accentLed = if (inProgressRolls.any { it.isFull }) LeicaCrimson else KodakAmber,
                testTag = "lab_tab_darkroom",
                onClick = { selectedTab = LabTab.DARKROOM },
                modifier = Modifier.weight(1f)
            )
        }

        // ==================== 3. TAB CONTENT ====================
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedTab == LabTab.DEVELOPED_ROLLS) {
                // Quick Active Roll Trays for in-progress rolls sitting in the photographer's bag
                if (inProgressRolls.isNotEmpty()) {
                    items(inProgressRolls, key = { "inprog_quick_${it.id}" }) { rollInBag ->
                        val bagBody = HardwareCatalog.bodyById(rollInBag.bodyId)
                        val bagFilm = HardwareCatalog.filmById(rollInBag.filmId)
                        val ledColor = if (rollInBag.isFull) LeicaCrimson else KodakAmber
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedMetalPlate(
                                    shape = RoundedCornerShape(13.dp),
                                    borderColor = ledColor.copy(alpha = 0.65f),
                                    borderWidth = 1.1.dp,
                                    elevated = true
                                )
                                .clickable { onOpenDevelopSheet(rollInBag) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("quick_develop_roll_${rollInBag.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .recessedInstrumentWell(shape = CircleShape, borderColor = ledColor.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = null,
                                    tint = ledColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(ledColor)
                                    )
                                    Text(
                                        text = if (rollInBag.customTitle.isNotBlank()) rollInBag.customTitle else "${bagBody.name} • ${bagFilm.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = FilmCream,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "N°%03d • ${rollInBag.shotsTaken}/${rollInBag.maxExposures} poses prêtes pour le bain".format(rollInBag.rollNumber),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarmEngraving
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (rollInBag.isFull) LeicaCrimson else BrassAccent)
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Développer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rollInBag.isFull) FilmCream else MatteLeather,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Filter Chips & Sort Toggle (when developed rolls exist)
                if (developedRolls.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(RollFilter.entries) { filter ->
                                    val active = filter == activeFilter
                                    Box(
                                        modifier = Modifier
                                            .then(
                                                if (active) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(18.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(BrassAccent, Color(0xFFD5A128))
                                                            )
                                                        )
                                                        .border(1.dp, Color(0xFFFFE594), RoundedCornerShape(18.dp))
                                                } else {
                                                    Modifier.brushedMetalPlate(
                                                        shape = RoundedCornerShape(18.dp),
                                                        borderColor = HairlineMetal
                                                    )
                                                }
                                            )
                                            .clickable { activeFilter = filter }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) MatteLeather else SilverChrome,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .brushedMetalPlate(
                                        shape = RoundedCornerShape(9.dp),
                                        borderColor = HairlineMetal,
                                        elevated = true
                                    )
                                    .clickable { sortNewestFirst = !sortNewestFirst }
                                    .padding(horizontal = 9.dp, vertical = 7.dp)
                                    .testTag("lab_sort_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = "Trier",
                                    tint = BrassAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (sortNewestFirst) "Récent" else "N°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FilmCream,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (developedRolls.isEmpty()) {
                    item {
                        val readyRoll = inProgressRolls.firstOrNull()
                        EmptyDevelopedRollsCard(
                            hasActiveShots = readyRoll != null,
                            activeShotsCount = readyRoll?.shotsTaken ?: 0,
                            onDevelopNow = { if (readyRoll != null) onOpenDevelopSheet(readyRoll) },
                            onGoToViewfinder = onBack
                        )
                    }
                } else if (visibleRolls.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .brushedMetalPlate(shape = RoundedCornerShape(14.dp))
                                .padding(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun rouleau développé ne correspond à ce filtre.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarmEngraving
                            )
                        }
                    }
                } else {
                    items(visibleRolls, key = { it.id }) { roll ->
                        DevelopedRollArchiveCard(
                            roll = roll,
                            previewPaths = developedRollPreviews[roll.id].orEmpty(),
                            dateFmt = dateFmt,
                            onOpenContactSheet = { onOpenContactSheet(roll) },
                            onRequestRename = { rollPendingRename = roll },
                            onRequestDelete = { rollPendingDelete = roll }
                        )
                    }
                }
            } else {
                // ==================== CHAMBRE NOIRE TAB ====================
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.2.dp, SafelightRed.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_darkroom_lab),
                            contentDescription = "Laboratoire argentique",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x330B0B0D), Color(0xE009090C))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC1A080A))
                                .border(0.8.dp, SafelightRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(LeicaCrimson)
                            )
                            Text(
                                text = "INACTINIQUE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                                color = Color(0xFFFF9EA6),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "CHAMBRE NOIRE PELOCHE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                color = KodakAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "On ne peut voir qu'un rouleau développé",
                                style = MaterialTheme.typography.headlineMedium,
                                color = FilmCream
                            )
                            Text(
                                text = "Les vues exposées restent scellées jusqu'au bain chimique — développement possible dès la 1re vue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverChrome
                            )
                        }
                    }
                }

                val darkroomCards: List<FilmRollEntity> = if (inProgressRolls.isNotEmpty()) {
                    inProgressRolls
                } else {
                    listOfNotNull(activeRoll)
                }

                items(darkroomCards, key = { "darkroom_${it.id}" }) { roll ->
                    val body = HardwareCatalog.bodyById(roll.bodyId)
                    val lens = HardwareCatalog.lensById(roll.lensId)
                    val film = HardwareCatalog.filmById(roll.filmId)
                    val exp = roll.expirationDate
                    val progress = (roll.shotsTaken.toFloat() / roll.maxExposures.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val isCurrentlyHeld = activeRoll?.id == roll.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .brushedMetalPlate(
                                shape = RoundedCornerShape(16.dp),
                                borderColor = if (isCurrentlyHeld) BrassAccent.copy(alpha = 0.72f) else HairlineMetal,
                                borderWidth = 1.2.dp
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 62.dp, height = 72.dp)
                                    .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FilmCanisterThumbnail(
                                    film = film,
                                    expirationDate = exp,
                                    modifier = Modifier.size(width = 48.dp, height = 58.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                LabStampTag(
                                    text = buildString {
                                        append(if (isCurrentlyHeld) "EN MAIN • " else "DANS LE SAC • ")
                                        append("N°%03d".format(roll.rollNumber))
                                    },
                                    color = if (isCurrentlyHeld) BrassAccent else KodakAmber
                                )
                                Text(
                                    text = "${film.name} (ISO ${film.iso})",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    color = FilmCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${body.name} • ${lens.shortCode} • ${body.aspectRatioSpec.formatBadge}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SilverChrome,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${exp.stampCode} • ${film.emulsionType.labelFr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (exp.isExpired) KodakAmber else PositiveGreen
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .recessedInstrumentWell(shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${roll.shotsTaken}/${roll.maxExposures}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (roll.isFull) LeicaCrimson else BrassAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "POSES",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = WarmEngraving
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (roll.isFull) LeicaCrimson else BrassAccent,
                            trackColor = Color(0xFF0A0B0E)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .recessedInstrumentWell(shape = RoundedCornerShape(9.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = "Négatifs masqués",
                                tint = WarmEngraving,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.roll_locked_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = WarmEngraving
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onOpenDevelopSheet(roll) },
                                enabled = roll.shotsTaken > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrassAccent,
                                    contentColor = MatteLeather,
                                    disabledContainerColor = AnodizedSlate,
                                    disabledContentColor = WarmEngraving
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("develop_roll_button_${roll.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (roll.isFull) "Développer" else "Rembobiner & Développer",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isCurrentlyHeld) onBack() else onResumeRollInViewfinder(roll)
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("return_to_viewfinder_button_${roll.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = SilverChrome,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (isCurrentlyHeld) "Viser" else "Prendre",
                                    color = SilverChrome,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(14.dp)) }
        }
    }

    // Roll rename dialog
    rollPendingRename?.let { roll ->
        RenameRollDialog(
            roll = roll,
            onDismiss = { rollPendingRename = null },
            onConfirm = { newTitle -> onRenameRoll(roll, newTitle) }
        )
    }

    // Safe delete confirmation dialog
    rollPendingDelete?.let { roll ->
        AlertDialog(
            onDismissRequest = { rollPendingDelete = null },
            containerColor = BrushedGunmetal,
            titleContentColor = FilmCream,
            textContentColor = SilverChrome,
            title = {
                Text(
                    text = "Supprimer ${roll.displayTitle} ?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "La planche-contact et les ${roll.shotsTaken} vues stockées dans le Labo seront supprimées de l'application (les photos déjà exportées dans DCIM/Peloche restent intactes).",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRoll(roll)
                        rollPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LeicaCrimson, contentColor = FilmCream)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { rollPendingDelete = null }) {
                    Text("Annuler", color = SilverChrome)
                }
            }
        )
    }
}

@Composable
private fun LabTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentLed: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.brushedMetalPlate(
                        shape = RoundedCornerShape(9.dp),
                        borderColor = accentLed.copy(alpha = 0.6f),
                        elevated = true
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(9.dp))
                }
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentLed else WarmEngraving,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) FilmCream else WarmEngraving,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) accentLed else Color.Transparent)
        )
    }
}

@Composable
private fun EmptyDevelopedRollsCard(
    hasActiveShots: Boolean,
    activeShotsCount: Int,
    onDevelopNow: () -> Unit,
    onGoToViewfinder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brushedMetalPlate(shape = RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .recessedInstrumentWell(shape = CircleShape, borderColor = BrassAccent.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = BrassAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "Aucune pellicule développée",
            style = MaterialTheme.typography.titleLarge,
            color = FilmCream
        )
        Text(
            text = if (hasActiveShots) {
                "Vous avez $activeShotsCount vue${if (activeShotsCount > 1) "s" else ""} dans votre rouleau actuel. Passez-le au bain chimique pour révéler sa planche-contact !"
            } else {
                "Prenez des photos depuis le viseur, puis rembobinez et développez votre pellicule au Labo pour consulter vos planches-contact."
            },
            style = MaterialTheme.typography.bodySmall,
            color = WarmEngraving
        )
        Button(
            onClick = if (hasActiveShots) onDevelopNow else onGoToViewfinder,
            colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
            modifier = Modifier
                .height(48.dp)
                .testTag("empty_lab_primary_cta")
        ) {
            Icon(
                imageVector = if (hasActiveShots) Icons.Filled.Science else Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (hasActiveShots) "Développer ($activeShotsCount vues)" else "Ouvrir le viseur",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Advanced Lab Development Method Selector Modal Bottom Sheet.
 * Displays a real-time OpenGL ES chemical test strip preview of the selected method
 * combined with the roll's exact Film Stock, Camera Body, Lens, and Expiration Date!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentMethodSheet(
    activeRoll: FilmRollEntity,
    selectedMethod: DevelopmentMethod,
    sampleSceneNight: Boolean,
    animatedEffectsEnabled: Boolean,
    onSelectMethod: (DevelopmentMethod) -> Unit,
    onDismiss: () -> Unit,
    onStartDevelopment: (DevelopmentMethod) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val body = HardwareCatalog.bodyById(activeRoll.bodyId)
    val lens = HardwareCatalog.lensById(activeRoll.lensId)
    val film = HardwareCatalog.filmById(activeRoll.filmId)
    val exp = activeRoll.expirationDate

    val previewParams = OpticalChainParams(
        lens = lens,
        body = body,
        film = film,
        expirationDate = exp,
        development = selectedMethod,
        evCompensation = 0f,
        zoomMultiplier = 1f,
        lightLeakSeed = 0.41f
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrushedGunmetal,
        contentColor = SilverChrome
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Procédé de développement",
                style = MaterialTheme.typography.headlineMedium,
                color = FilmCream
            )
            Text(
                text = "Rouleau N°%03d • ${film.name} (${film.emulsionType.labelFr}) • ${activeRoll.shotsTaken} poses".format(activeRoll.rollNumber),
                style = MaterialTheme.typography.bodySmall,
                color = BrassAccent
            )

            // Real-Time Darkroom Chemical Test Strip Preview!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .recessedInstrumentWell(shape = RoundedCornerShape(12.dp))
            ) {
                LiveOpticalPreviewSurface(
                    params = previewParams,
                    useCameraStream = false,
                    sampleSceneNight = sampleSceneNight,
                    animatedEffectsEnabled = animatedEffectsEnabled,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0xCC09090C))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bande test : ${selectedMethod.titleFr}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FilmCream
                    )
                    Text(
                        text = "ISO ${film.iso} → EI ${selectedMethod.effectiveIso(film.iso)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = KodakAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Carousel of all 9 Advanced Development Methods in tactile metal tiles
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("dev_methods_carousel")
            ) {
                items(DevelopmentMethod.entries) { method ->
                    val isSelected = method == selectedMethod
                    val isRecommended = film.emulsionType in method.recommendedFor
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .brushedMetalPlate(
                                shape = RoundedCornerShape(12.dp),
                                borderColor = if (isSelected) BrassAccent else HairlineMetal,
                                borderWidth = if (isSelected) 1.5.dp else 1.dp,
                                elevated = isSelected
                            )
                            .clickable { onSelectMethod(method) }
                            .padding(10.dp)
                            .testTag("dev_method_${method.name}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = method.shortBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) BrassAccent else KodakAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "EI ${method.effectiveIso(film.iso)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverChrome
                            )
                        }
                        Text(
                            text = method.titleFr,
                            style = MaterialTheme.typography.titleSmall,
                            color = FilmCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = method.tempAndTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = WarmEngraving
                        )
                        Text(
                            text = method.compatibilityLabelFr(film.emulsionType),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isRecommended) PositiveGreen else KodakAmber
                        )
                    }
                }
            }

            // Detailed Chemical Impact Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${selectedMethod.titleFr} • ${selectedMethod.tempAndTime}",
                    style = MaterialTheme.typography.titleMedium,
                    color = FilmCream
                )
                Text(
                    text = selectedMethod.descriptionFr,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverChrome
                )
                if (exp.isExpired) {
                    Text(
                        text = "+ Interaction péremption (${exp.stampCode}) incluse dans le bain.",
                        style = MaterialTheme.typography.labelSmall,
                        color = KodakAmber
                    )
                }
            }

            Button(
                onClick = { onStartDevelopment(selectedMethod) },
                colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("launch_development_button")
            ) {
                Icon(imageVector = Icons.Filled.Science, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lancer le bain ${selectedMethod.shortBadge} (${activeRoll.shotsTaken} vues)",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Full darkroom processing dialog while rewinding and rendering full-res developed frames.
 */
@Composable
fun DarkroomProgressDialog(progressState: DevelopmentProgressState) {
    if (!progressState.isRunning) return
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brushedMetalPlate(
                    shape = RoundedCornerShape(16.dp),
                    borderColor = SafelightRed,
                    borderWidth = 1.5.dp,
                    elevated = true
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .recessedInstrumentWell(shape = CircleShape, borderColor = SafelightRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Science,
                    contentDescription = null,
                    tint = KodakAmber,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Développement en cours…",
                style = MaterialTheme.typography.titleLarge,
                color = FilmCream
            )
            LinearProgressIndicator(
                progress = { progressState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = KodakAmber,
                trackColor = Color(0xFF09090C)
            )
            Text(
                text = progressState.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = SilverChrome
            )
        }
    }
}
