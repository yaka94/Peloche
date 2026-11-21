package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.FilmRollEntity
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.HardwareCatalog
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.OpticalTeal
import com.example.ui.theme.PositiveGreen
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dark skeuomorphic archival roll visualization card ("Pochette & Table Lumineuse").
 * Combines a machined brushed-metal outer chassis, a recessed black leatherette canister socket,
 * a backlit 35mm perforated contact strip preview, and a full technical development receipt
 * detailing the applied chemical bath, temperature/time, ISO->EI shift, and sensitometric impact.
 */
@Composable
fun DevelopedRollArchiveCard(
    roll: FilmRollEntity,
    previewPaths: List<String>,
    dateFmt: SimpleDateFormat,
    onOpenContactSheet: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val body = HardwareCatalog.bodyById(roll.bodyId)
    val lens = HardwareCatalog.lensById(roll.lensId)
    val film = HardwareCatalog.filmById(roll.filmId)
    val dev = roll.developmentMethod
    val exp = roll.expirationDate
    val effectiveIso = dev.effectiveIso(film.iso)
    val isSpecialProcess = dev != DevelopmentMethod.STANDARD_C41

    val processAccent = when (dev) {
        DevelopmentMethod.STANDARD_C41 -> PositiveGreen
        DevelopmentMethod.PUSH_1, DevelopmentMethod.PUSH_2 -> KodakAmber
        DevelopmentMethod.PULL_1 -> OpticalTeal
        DevelopmentMethod.CROSS_PROCESS -> Color(0xFFD07BF7)
        DevelopmentMethod.BLEACH_BYPASS -> SilverChrome
        DevelopmentMethod.SEPIA_TONING -> Color(0xFFD9A066)
        DevelopmentMethod.CYANOTYPE_TONING -> Color(0xFF5DA9E9)
        DevelopmentMethod.RODINAL_STAND -> BrassAccent
        DevelopmentMethod.SILVER_NITRATE_COLLODION -> Color(0xFFC8BEAE)
        DevelopmentMethod.MERCURY_DAGUERRE -> Color(0xFF9AC5C8)
        DevelopmentMethod.GOLD_PLATINUM_TONING -> Color(0xFFE4CFA0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .brushedMetalPlate(
                shape = RoundedCornerShape(16.dp),
                borderColor = if (isSpecialProcess) processAccent.copy(alpha = 0.42f) else HairlineMetal,
                borderWidth = 1.2.dp,
                elevated = false
            )
            .clickable { onOpenContactSheet() }
            .padding(12.dp)
            .testTag("developed_roll_card_${roll.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==================== 1. MACHINED SERIAL PLATE & CANISTER SOCKET ====================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MachinedScrewHead(size = 8.dp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0A0B0E))
                        .border(0.8.dp, BrassAccent.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ROULEAU N°%03d".format(roll.rollNumber),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                        color = BrassAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                roll.developedAt?.let { ts ->
                    Text(
                        text = dateFmt.format(Date(ts)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = WarmEngraving,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRequestRename,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rename_roll_button_${roll.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Renommer ce rouleau",
                        tint = BrassAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_roll_button_${roll.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Supprimer ce rouleau",
                        tint = WarmEngraving,
                        modifier = Modifier.size(19.dp)
                    )
                }
                MachinedScrewHead(size = 8.dp)
            }
        }

        // Main Identity Row: Recessed leather socket with canister + film & rig engraving
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 66.dp)
                    .recessedInstrumentWell(shape = RoundedCornerShape(10.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                FilmCanisterThumbnail(
                    film = film,
                    expirationDate = exp,
                    modifier = Modifier.size(width = 44.dp, height = 54.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (roll.customTitle.isNotBlank()) roll.customTitle else film.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = FilmCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (roll.customTitle.isNotBlank()) {
                        "${film.name}  •  ${body.name}  •  ${lens.shortCode}"
                    } else {
                        "${body.name}  •  ${lens.shortCode}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverChrome,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabStampTag(
                        text = "${roll.shotsTaken} VUES",
                        color = BrassAccent
                    )
                    LabStampTag(
                        text = body.aspectRatioSpec.formatBadge,
                        color = SilverChrome
                    )
                    LabStampTag(
                        text = if (exp.isExpired) exp.shortBadge else "LOT FRAIS",
                        color = if (exp.isExpired) KodakAmber else PositiveGreen
                    )
                }
            }
        }

        // ==================== 2. BACKLIT 35MM FILMSTRIP LIGHT-TABLE WINDOW ====================
        if (previewPaths.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .recessedInstrumentWell(
                        shape = RoundedCornerShape(10.dp),
                        borderColor = Color(0xFF2B2E38)
                    )
                    .padding(vertical = 5.dp, horizontal = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top film rebate edge printing + sprockets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PELOCHE  •  ${film.code}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
                        color = BrassAccent.copy(alpha = 0.72f)
                    )
                    Text(
                        text = "EI $effectiveIso  •  ${dev.shortBadge}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                        color = processAccent.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold
                    )
                }

                MiniSprocketStrip()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val visibleThumbs = previewPaths.take(4)
                    val remainingShots = (roll.shotsTaken - visibleThumbs.size).coerceAtLeast(0)
                    visibleThumbs.forEachIndexed { idx, path ->
                        val file = File(path)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.22f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black)
                                .border(0.6.dp, Color(0xFF323540), RoundedCornerShape(3.dp))
                        ) {
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Aperçu vue ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Frame index badge on top-left corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color(0xAA070709))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "%02d".format(idx + 1),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                    color = BrassAccent
                                )
                            }
                            if (idx == visibleThumbs.lastIndex && remainingShots > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xA6070709)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+$remainingShots",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = BrassAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    repeat((4 - previewPaths.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                MiniSprocketStrip()
            }
        }

        // ==================== 3. APPLIED DEVELOPMENT DETAILS ("FICHE LABO") ====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .recessedInstrumentWell(
                    shape = RoundedCornerShape(10.dp),
                    borderColor = processAccent.copy(alpha = 0.35f)
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: Process name + glowing chemistry LED + badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(processAccent)
                    )
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = null,
                        tint = processAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = dev.titleFr,
                        style = MaterialTheme.typography.labelMedium,
                        color = FilmCream,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                LabStampTag(text = dev.shortBadge, color = processAccent)
            }

            // Technical Bath Parameters Row: Temperature/Duration + ISO -> EI + Exposure delta
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF13151C))
                    .border(0.7.dp, Color(0xFF242733), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Thermostat,
                        contentDescription = null,
                        tint = KodakAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = dev.tempAndTime,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = SilverChrome
                    )
                }

                val isoShiftText = if (effectiveIso != film.iso) {
                    "ISO ${film.iso} → EI $effectiveIso"
                } else {
                    "ISO ${film.iso} nominal"
                }
                Text(
                    text = isoShiftText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (effectiveIso != film.iso) KodakAmber else PositiveGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sensitometric Chemical Multipliers (Contraste, Grain, Saturation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChemicalMetricGauge(
                    label = "CONTRASTE",
                    valueText = formatMultiplier(dev.contrastMul),
                    fraction = (dev.contrastMul / 1.55f),
                    accentColor = BrassAccent,
                    modifier = Modifier.weight(1f)
                )
                ChemicalMetricGauge(
                    label = "GRAIN",
                    valueText = formatMultiplier(dev.grainMul),
                    fraction = (dev.grainMul / 1.75f),
                    accentColor = KodakAmber,
                    modifier = Modifier.weight(1f)
                )
                ChemicalMetricGauge(
                    label = "COULEUR",
                    valueText = formatMultiplier(dev.saturationMul),
                    fraction = (dev.saturationMul / 1.5f),
                    accentColor = OpticalTeal,
                    modifier = Modifier.weight(1f)
                )
            }

            // Concise chemical recipe note + expiration interaction if applicable
            Text(
                text = buildString {
                    append(dev.descriptionFr)
                    if (exp.isExpired) {
                        append(" • Interaction ${exp.stampCode} fixée au bain.")
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = WarmEngraving,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ==================== 4. BEVELED METALLIC CONTACT SHEET CTA ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1D2029),
                            Color(0xFF262A36),
                            Color(0xFF1D2029)
                        )
                    )
                )
                .border(0.9.dp, BrassAccent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = null,
                    tint = BrassAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Ouvrir la planche-contact",
                    style = MaterialTheme.typography.labelMedium,
                    color = FilmCream,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${roll.shotsTaken} TIRAGES",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = BrassAccent
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = BrassAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatMultiplier(value: Float): String {
    val pct = ((value - 1f) * 100f).toInt()
    return when {
        pct == 0 -> "NOM."
        pct > 0 -> "+$pct%"
        else -> "$pct%"
    }
}

@Composable
private fun MiniSprocketStrip() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
    ) {
        val holeW = 11f
        val gap = 10f
        var x = 2f
        while (x + holeW < size.width) {
            drawRoundRect(
                color = Color(0xFF1D2028),
                topLeft = Offset(x, 0f),
                size = Size(holeW, size.height),
                cornerRadius = CornerRadius(1.8f, 1.8f)
            )
            drawRoundRect(
                color = Color(0xFF030304),
                topLeft = Offset(x + 0.8f, 0.7f),
                size = Size(holeW - 1.6f, size.height - 1.4f),
                cornerRadius = CornerRadius(1.4f, 1.4f)
            )
            x += holeW + gap
        }
    }
}

@Composable
fun LabStampTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.62f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Dark skeuomorphic archival roll naming dialog (e.g. "Week-end Lisbonne", "Soirée 35mm").
 */
@Composable
fun RenameRollDialog(
    roll: FilmRollEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(roll.id, roll.customTitle) { mutableStateOf(roll.customTitle) }
    val suggestions = remember { listOf("Week-end mer", "Soirée 35mm", "Portraits", "Voyage") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF17181E),
        titleContentColor = FilmCream,
        textContentColor = SilverChrome,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Nommer le rouleau N°%03d".format(roll.rollNumber),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Gravé sur la pochette Labo et la planche-contact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmEngraving
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(48) },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Ex. Week-end Lisbonne…",
                            color = WarmEngraving.copy(alpha = 0.7f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrassAccent,
                        unfocusedBorderColor = HairlineMetal,
                        focusedTextColor = FilmCream,
                        unfocusedTextColor = FilmCream,
                        cursorColor = BrassAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_roll_text_field")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(AnodizedSlate)
                                .border(0.8.dp, HairlineMetal, RoundedCornerShape(14.dp))
                                .clickable { text = preset }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = SilverChrome,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(text.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrassAccent,
                    contentColor = MatteLeather
                ),
                modifier = Modifier.testTag("confirm_rename_roll_button")
            ) {
                Text("Enregistrer", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = SilverChrome)
            }
        }
    )
}
