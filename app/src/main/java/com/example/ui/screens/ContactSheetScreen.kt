package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.FilmFrameEntity
import com.example.data.local.FilmRollEntity
import com.example.domain.model.HardwareCatalog
import com.example.ui.components.CinemaClapperCard
import com.example.ui.components.MachinedScrewHead
import com.example.ui.components.blackLeatherTexture
import com.example.ui.components.brushedMetalPlate
import com.example.ui.components.recessedInstrumentWell
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.OpticalTeal
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactSheetScreen(
    roll: FilmRollEntity,
    frames: List<FilmFrameEntity>,
    onBack: () -> Unit,
    onExportContactSheet: () -> Unit,
    onExportAllFrames: () -> Unit,
    onShareContactSheet: () -> Unit,
    onExportSingleFrame: (FilmFrameEntity) -> Unit,
    onShareSingleFrame: (FilmFrameEntity) -> Unit
) {
    val body = HardwareCatalog.bodyById(roll.bodyId)
    val lens = HardwareCatalog.lensById(roll.lensId)
    val film = HardwareCatalog.filmById(roll.filmId)
    val dev = roll.developmentMethod
    val exp = roll.expirationDate

    var inspectedIndex by remember { mutableStateOf<Int?>(null) }
    var columnsPerStrip by remember { mutableIntStateOf(3) }
    // Red grease-pencil selections ("Coup de cœur" darkroom contact-sheet curation)
    var greasePencilMarks by remember(roll.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var showMarkedOnly by remember(roll.id) { mutableStateOf(false) }

    val visibleFrames = remember(frames, greasePencilMarks, showMarkedOnly) {
        if (showMarkedOnly && greasePencilMarks.isNotEmpty()) {
            frames.filter { it.id in greasePencilMarks }
        } else {
            frames
        }
    }
    val stripChunks = remember(visibleFrames, columnsPerStrip) { visibleFrames.chunked(columnsPerStrip) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .blackLeatherTexture()
    ) {
        // Machined Metal Header Bar
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                        .testTag("contact_sheet_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = SilverChrome
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Planche-Contact N°%03d".format(roll.rollNumber),
                        style = MaterialTheme.typography.titleLarge,
                        color = FilmCream
                    )
                    Text(
                        text = "${film.code} • ${dev.shortBadge} (EI ${dev.effectiveIso(film.iso)}) • ${frames.size} VUES",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = KodakAmber
                    )
                }

                IconButton(
                    onClick = onShareContactSheet,
                    modifier = Modifier
                        .size(48.dp)
                        .brushedMetalPlate(shape = CircleShape, borderColor = BrassAccent.copy(alpha = 0.7f), elevated = true)
                        .testTag("share_contact_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Partager la planche-contact",
                        tint = BrassAccent
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CinemaClapperCard(
                    rollNumber = roll.rollNumber,
                    body = body,
                    lens = lens,
                    film = film,
                    expiration = exp
                )
            }

            // Lab Development Summary + Grid Density + Export / Batch Export Actions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LABO : ${dev.titleFr}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OpticalTeal,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${exp.stampCode} • ${body.aspectRatioSpec.filmGaugeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarmEngraving
                            )
                        }

                        // Density selector (2 / 3 / 4 columns)
                        Row(
                            modifier = Modifier
                                .recessedInstrumentWell(shape = RoundedCornerShape(8.dp))
                                .padding(2.dp)
                                .testTag("contact_sheet_density_selector"),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(2, 3, 4).forEach { cols ->
                                val active = columnsPerStrip == cols
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) BrassAccent else Color.Transparent)
                                        .clickable { columnsPerStrip = cols }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${cols}×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MatteLeather else SilverChrome,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Action Toolbar: Export Planche + Export All + Grease Pencil Selection Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onExportContactSheet,
                            colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_contact_sheet_button")
                        ) {
                            Icon(imageVector = Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Planche", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onExportAllFrames,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_all_frames_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DownloadDone,
                                contentDescription = null,
                                tint = SilverChrome,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Tout exporter", style = MaterialTheme.typography.labelMedium, color = SilverChrome)
                        }

                        if (greasePencilMarks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showMarkedOnly) LeicaCrimson else AnodizedSlate)
                                    .border(1.dp, LeicaCrimson.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                    .clickable { showMarkedOnly = !showMarkedOnly }
                                    .padding(horizontal = 9.dp, vertical = 9.dp)
                                    .testTag("filter_marked_frames_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Filtrer les coups de cœur",
                                    tint = if (showMarkedOnly) FilmCream else LeicaCrimson,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${greasePencilMarks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FilmCream,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Perforated 35mm / 120 Film Strips!
            items(stripChunks) { rowFrames ->
                PerforatedFilmStripRow(
                    frames = rowFrames,
                    columns = columnsPerStrip,
                    filmCode = film.code,
                    aspectRatio = body.aspectRatioSpec.aspectRatio.coerceIn(0.70f, 2.3f),
                    markedFrameIds = greasePencilMarks,
                    onFrameClick = { clicked ->
                        val idx = frames.indexOfFirst { it.id == clicked.id }
                        if (idx >= 0) inspectedIndex = idx
                    },
                    onToggleMark = { frameId ->
                        greasePencilMarks = if (frameId in greasePencilMarks) {
                            greasePencilMarks - frameId
                        } else {
                            greasePencilMarks + frameId
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(14.dp)) }
        }
    }

    // Fullscreen Loupe & EXIF Metadata Inspector Dialog with Share + Grease-Pencil Curation
    inspectedIndex?.let { currentIdx ->
        val frame = frames.getOrNull(currentIdx)
        if (frame != null) {
            val isMarked = frame.id in greasePencilMarks
            FrameLoupeDialog(
                roll = roll,
                frame = frame,
                frameIndex = currentIdx,
                totalFrames = frames.size,
                isMarked = isMarked,
                onToggleMark = {
                    greasePencilMarks = if (isMarked) {
                        greasePencilMarks - frame.id
                    } else {
                        greasePencilMarks + frame.id
                    }
                },
                onPrevious = { if (currentIdx > 0) inspectedIndex = currentIdx - 1 },
                onNext = { if (currentIdx < frames.lastIndex) inspectedIndex = currentIdx + 1 },
                onDismiss = { inspectedIndex = null },
                onExportFrame = { onExportSingleFrame(frame) },
                onShareFrame = { onShareSingleFrame(frame) }
            )
        }
    }
}

@Composable
private fun PerforatedFilmStripRow(
    frames: List<FilmFrameEntity>,
    columns: Int,
    filmCode: String,
    aspectRatio: Float,
    markedFrameIds: Set<Long>,
    onFrameClick: (FilmFrameEntity) -> Unit,
    onToggleMark: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101116))
            .border(1.dp, Color(0xFF262932), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp)
    ) {
        SprocketTrack(edgeLabel = "PELOCHE  •  $filmCode")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            frames.forEach { frame ->
                val isMarked = frame.id in markedFrameIds
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black)
                        .border(
                            width = if (isMarked) 2.2.dp else 0.8.dp,
                            color = if (isMarked) LeicaCrimson else HairlineMetal,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .clickable { onFrameClick(frame) }
                        .testTag("contact_frame_${frame.frameNumber}")
                ) {
                    val file = frame.developedPath?.let { File(it) }
                    if (file != null && file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Vue ${frame.frameNumber}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Frame number corner label
                    Text(
                        text = "%02dA".format(frame.frameNumber),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = KodakAmber,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color(0xAA0D0C0B))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    // Grease-pencil mark button in top-right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (isMarked) LeicaCrimson.copy(alpha = 0.90f) else Color(0x880B0B0D))
                            .clickable { onToggleMark(frame.id) }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Marquer au crayon gras",
                            tint = if (isMarked) FilmCream else SilverChrome.copy(alpha = 0.8f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            repeat((columns - frames.size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        val numbersText = frames.joinToString("        ") { "▲ %02dA".format(it.frameNumber) }
        SprocketTrack(edgeLabel = numbersText)
    }
}

@Composable
private fun SprocketTrack(edgeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = edgeLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
            color = KodakAmber.copy(alpha = 0.85f)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val holeW = 18f
            val holeH = 10f
            val gap = 16f
            var x = 4f
            while (x + holeW < size.width) {
                drawRoundRect(
                    color = Color(0xFF0A0908),
                    topLeft = Offset(x, 0f),
                    size = Size(holeW, holeH),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                x += holeW + gap
            }
        }
    }
}

@Composable
private fun FrameLoupeDialog(
    roll: FilmRollEntity,
    frame: FilmFrameEntity,
    frameIndex: Int,
    totalFrames: Int,
    isMarked: Boolean,
    onToggleMark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onExportFrame: () -> Unit,
    onShareFrame: () -> Unit
) {
    val body = HardwareCatalog.bodyById(frame.bodyId)
    val lens = HardwareCatalog.lensById(frame.lensId)
    val film = HardwareCatalog.filmById(frame.filmId)
    val exp = roll.expirationDate
    val dev = roll.developmentMethod
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF50A0908))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar with frame position, grease-pencil mark & close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VUE %02dA (${frameIndex + 1}/$totalFrames) • N°%03d".format(frame.frameNumber, roll.rollNumber),
                    style = MaterialTheme.typography.titleSmall,
                    color = KodakAmber
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleMark,
                        modifier = Modifier.testTag("loupe_mark_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isMarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Marquer comme coup de cœur",
                            tint = if (isMarked) LeicaCrimson else SilverChrome
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer l'aperçu",
                            tint = FilmCream
                        )
                    }
                }
            }

            // Full Photo + Overlay Previous/Next Controls
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val file = frame.developedPath?.let { File(it) }
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Photo développée ${frame.frameNumber}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    )
                }

                if (frameIndex > 0) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA121110))
                            .border(1.dp, HairlineMetal, CircleShape)
                            .testTag("loupe_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Vue précédente",
                            tint = FilmCream
                        )
                    }
                }
                if (frameIndex < totalFrames - 1) {
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA121110))
                            .border(1.dp, HairlineMetal, CircleShape)
                            .testTag("loupe_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Vue suivante",
                            tint = FilmCream
                        )
                    }
                }
            }

            // Enriched Analog EXIF Technical Card + Export & Share Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "MÉTADONNÉES EXIF ARGENTIQUES",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrassAccent
                )
                ExifLine("Boîtier", "${body.name} (${body.aspectRatioSpec.displayLabel})")
                ExifLine("Objectif", "${lens.name} • ${lens.focalLabel} ${lens.maxAperture}")
                ExifLine("Pellicule", "${film.name} (${film.emulsionType.labelFr}, ISO ${film.iso})")
                ExifLine("Péremption", "${exp.stampCode} (${exp.severityLabel})")
                ExifLine("Développement", "${dev.titleFr} • EI ${dev.effectiveIso(film.iso)}")
                ExifLine("Exposition", "EV %+.1f IL • %s".format(frame.evCompensation, fmt.format(Date(frame.capturedAt))))

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportFrame,
                        colors = ButtonDefaults.buttonColors(containerColor = BrassAccent, contentColor = MatteLeather),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_single_frame_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enregistrer", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onShareFrame,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_single_frame_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = BrassAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Partager", style = MaterialTheme.typography.labelLarge, color = FilmCream)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = WarmEngraving)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = FilmCream,
            fontWeight = FontWeight.Medium
        )
    }
}
