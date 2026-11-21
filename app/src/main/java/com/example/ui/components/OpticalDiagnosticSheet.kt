package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DiagnosticChartType
import com.example.engine.DiagnosticRenderTile
import com.example.engine.DiagnosticShaderTarget
import com.example.engine.OpticalDiagnosticRenderer
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.FilmCream
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Interactive Optical Diagnostic Bench rendering standardized Solid 18% Gray & High-Contrast
 * Color/Resolution charts through the real GLSL shader pipeline at 0%, 25%, 50%, 75%, and 100%
 * intensity for Light Leaks, Plastic Scratches, and Tilt-Shift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticalDiagnosticBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTarget by remember { mutableStateOf(DiagnosticShaderTarget.LIGHT_LEAKS) }
    var selectedChart by remember { mutableStateOf(DiagnosticChartType.SOLID_GRAY) }
    var seed by remember { mutableFloatStateOf(0.064f) }
    var tiles by remember { mutableStateOf<List<DiagnosticRenderTile>>(emptyList()) }
    var isRendering by remember { mutableStateOf(true) }

    // Recycle old tiles whenever inputs change or sheet closes
    DisposableEffect(Unit) {
        onDispose {
            tiles.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    LaunchedEffect(selectedTarget, selectedChart, seed) {
        isRendering = true
        val previous = tiles
        val rendered = withContext(Dispatchers.Default) {
            OpticalDiagnosticRenderer.renderIntensitySweep(
                chartType = selectedChart,
                target = selectedTarget,
                seed = seed,
                tileWidth = 360,
                tileHeight = 240
            )
        }
        tiles = rendered
        isRendering = false
        previous.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14151B),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = "Banc de diagnostic optique",
                        tint = BrassAccent
                    )
                    Column {
                        Text(
                            text = "Banc de diagnostic optique",
                            style = MaterialTheme.typography.titleLarge,
                            color = FilmCream
                        )
                        Text(
                            text = "Rendu réel des shaders • Balayage 0 % → 100 %",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrassAccent
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { seed = Random.nextFloat() },
                        modifier = Modifier.testTag("diag_reseed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Casino,
                            contentDescription = "Nouvelle graine aléatoire",
                            tint = BrassAccent
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("diag_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer",
                            tint = SilverChrome
                        )
                    }
                }
            }

            // Target shader selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticShaderTarget.entries.forEach { target ->
                    val active = target == selectedTarget
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .recessedInstrumentWell(
                                shape = RoundedCornerShape(10.dp),
                                borderColor = if (active) BrassAccent else HairlineMetal
                            )
                            .clickable { selectedTarget = target }
                            .padding(vertical = 10.dp)
                            .testTag("diag_target_${target.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = target.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) BrassAccent else SilverChrome,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Chart selector (Solid 18% Gray vs High-Contrast Color & Frequency Chart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticChartType.entries.forEach { chart ->
                    val active = chart == selectedChart
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .recessedInstrumentWell(
                                shape = RoundedCornerShape(10.dp),
                                borderColor = if (active) BrassAccent else HairlineMetal
                            )
                            .clickable { selectedChart = chart }
                            .padding(vertical = 9.dp)
                            .testTag("diag_chart_${chart.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chart.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) BrassAccent else SilverChrome,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Text(
                text = selectedTarget.descriptionFr,
                style = MaterialTheme.typography.bodySmall,
                color = WarmEngraving
            )

            if (isRendering) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = BrassAccent, modifier = Modifier.size(30.dp))
                        Text(
                            text = "Calcul des mires optiques…",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverChrome
                        )
                    }
                }
            } else {
                tiles.forEach { tile ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .brushedMetalPlate(shape = RoundedCornerShape(12.dp))
                            .padding(10.dp)
                            .testTag("diag_tile_${tile.intensityPercent}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INTENSITÉ ${tile.label}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (tile.intensityPercent == 0) WarmEngraving else BrassAccent
                            )
                            val telemetry = if (selectedTarget == DiagnosticShaderTarget.TILT_SHIFT) {
                                "Flou bords −%.0f%% • Centre/Bords %.2f×".format(
                                    tile.edgeSharpnessDropPct,
                                    tile.centerToEdgeRatio
                                )
                            } else {
                                "Δmoy %.1f%% • Δpic +%.1f%%".format(
                                    tile.meanDeltaPct,
                                    tile.peakDeltaPct
                                )
                            }
                            Text(
                                text = telemetry,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = SilverChrome
                            )
                        }
                        if (!tile.bitmap.isRecycled) {
                            Image(
                                bitmap = tile.bitmap.asImageBitmap(),
                                contentDescription = "${selectedTarget.labelFr} à ${tile.label}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.5f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, HairlineMetal, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
