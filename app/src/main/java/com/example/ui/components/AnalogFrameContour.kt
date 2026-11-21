package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.FilmStockModel
import com.example.domain.model.FrameBorderStyle
import com.example.ui.theme.BrassAccent
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson

private data class ContourMetrics(
    val padStart: Dp,
    val padTop: Dp,
    val padEnd: Dp,
    val padBottom: Dp,
    val outerCorner: Dp,
    val innerCorner: Dp
)

/**
 * Wraps the live viewfinder or preview aperture inside the selected Camera Body's
 * authentic physical frame contour (35mm perforated strip, Polaroid SX-70 white card,
 * Instax Mini white card, Hasselblad 6x6 V-notched mask, XPan panoramic sprockets,
 * 90s satin print border, Holga rough plastic mask, or Disposable window), while
 * mathematically guaranteeing that the inner optical aperture matches the exact
 * aspect ratio of the body with ZERO stretching or overflow in both Portrait & Landscape.
 */
@Composable
fun AnalogFrameContour(
    body: CameraBodyModel,
    film: FilmStockModel,
    frameNumber: Int,
    borderEnabled: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val style = body.frameBorderStyle
    val targetRatio = body.aspectRatioSpec.aspectRatio.coerceIn(0.65f, 2.75f)
    val scale = if (compact) 0.56f else 1.0f

    val metrics = if (!borderEnabled) {
        ContourMetrics(2.dp, 2.dp, 2.dp, 2.dp, 8.dp, 6.dp)
    } else {
        when (style) {
            FrameBorderStyle.FILM_35MM_SPROCKETS -> ContourMetrics(
                padStart = (5 * scale).dp,
                padTop = (20 * scale).dp,
                padEnd = (5 * scale).dp,
                padBottom = (20 * scale).dp,
                outerCorner = 6.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.PANORAMIC_XPAN -> ContourMetrics(
                padStart = (6 * scale).dp,
                padTop = (18 * scale).dp,
                padEnd = (6 * scale).dp,
                padBottom = (18 * scale).dp,
                outerCorner = 5.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.HALF_FRAME_STRIP -> ContourMetrics(
                padStart = (18 * scale).dp,
                padTop = (6 * scale).dp,
                padEnd = (18 * scale).dp,
                padBottom = (6 * scale).dp,
                outerCorner = 6.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.MEDIUM_FORMAT_6X6 -> ContourMetrics(
                padStart = (14 * scale).dp,
                padTop = (10 * scale).dp,
                padEnd = (10 * scale).dp,
                padBottom = (14 * scale).dp,
                outerCorner = 5.dp,
                innerCorner = 1.dp
            )
            FrameBorderStyle.COMPACT_LAB_PRINT -> ContourMetrics(
                padStart = (10 * scale).dp,
                padTop = (10 * scale).dp,
                padEnd = (10 * scale).dp,
                padBottom = (12 * scale).dp,
                outerCorner = 4.dp,
                innerCorner = 1.dp
            )
            FrameBorderStyle.HOLGA_MASK_6X6 -> ContourMetrics(
                padStart = (11 * scale).dp,
                padTop = (11 * scale).dp,
                padEnd = (11 * scale).dp,
                padBottom = (16 * scale).dp,
                outerCorner = 7.dp,
                innerCorner = 10.dp
            )
            FrameBorderStyle.DISPOSABLE_CARD -> ContourMetrics(
                padStart = (9 * scale).dp,
                padTop = (11 * scale).dp,
                padEnd = (9 * scale).dp,
                padBottom = (16 * scale).dp,
                outerCorner = 9.dp,
                innerCorner = 6.dp
            )
            FrameBorderStyle.POLAROID_WHITE -> ContourMetrics(
                padStart = (13 * scale).dp,
                padTop = (13 * scale).dp,
                padEnd = (13 * scale).dp,
                padBottom = (44 * scale).dp,
                outerCorner = 4.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.INSTAX_MINI_WHITE -> ContourMetrics(
                padStart = (11 * scale).dp,
                padTop = (12 * scale).dp,
                padEnd = (11 * scale).dp,
                padBottom = (34 * scale).dp,
                outerCorner = 5.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.WET_PLATE_COLLODION -> ContourMetrics(
                padStart = (12 * scale).dp,
                padTop = (12 * scale).dp,
                padEnd = (12 * scale).dp,
                padBottom = (22 * scale).dp,
                outerCorner = 3.dp,
                innerCorner = 2.dp
            )
            FrameBorderStyle.DAGUERREOTYPE_CASE -> ContourMetrics(
                padStart = (16 * scale).dp,
                padTop = (16 * scale).dp,
                padEnd = (16 * scale).dp,
                padBottom = (24 * scale).dp,
                outerCorner = 6.dp,
                innerCorner = 20.dp
            )
            FrameBorderStyle.AUTOCHROME_GLASS -> ContourMetrics(
                padStart = (11 * scale).dp,
                padTop = (11 * scale).dp,
                padEnd = (11 * scale).dp,
                padBottom = (20 * scale).dp,
                outerCorner = 2.dp,
                innerCorner = 1.dp
            )
            FrameBorderStyle.PINHOLE_WOOD_BOX -> ContourMetrics(
                padStart = (13 * scale).dp,
                padTop = (13 * scale).dp,
                padEnd = (13 * scale).dp,
                padBottom = (20 * scale).dp,
                outerCorner = 5.dp,
                innerCorner = 4.dp
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val availW = (maxWidth - metrics.padStart - metrics.padEnd).coerceAtLeast(48.dp)
        val availH = (maxHeight - metrics.padTop - metrics.padBottom).coerceAtLeast(48.dp)

        val innerW: Dp
        val innerH: Dp
        if (availW / availH > targetRatio) {
            innerH = availH
            innerW = innerH * targetRatio
        } else {
            innerW = availW
            innerH = innerW / targetRatio
        }

        val outerW = innerW + metrics.padStart + metrics.padEnd
        val outerH = innerH + metrics.padTop + metrics.padBottom

        if (!borderEnabled) {
            Box(
                modifier = Modifier
                    .size(innerW, innerH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.4.dp, HairlineMetal, RoundedCornerShape(8.dp)),
                content = content
            )
            return@BoxWithConstraints
        }

        val isPaperCard = style == FrameBorderStyle.POLAROID_WHITE ||
            style == FrameBorderStyle.INSTAX_MINI_WHITE ||
            style == FrameBorderStyle.COMPACT_LAB_PRINT

        val outerBgBrush = when (style) {
            FrameBorderStyle.POLAROID_WHITE -> Brush.verticalGradient(
                listOf(Color(0xFFF6F3EB), Color(0xFFECE7DB))
            )
            FrameBorderStyle.INSTAX_MINI_WHITE -> Brush.verticalGradient(
                listOf(Color(0xFFFAF9F6), Color(0xFFF0EFEA))
            )
            FrameBorderStyle.COMPACT_LAB_PRINT -> Brush.verticalGradient(
                listOf(Color(0xFFF2ECE0), Color(0xFFE7DFD0))
            )
            FrameBorderStyle.DISPOSABLE_CARD -> Brush.verticalGradient(
                listOf(Color(0xFF23221F), Color(0xFF141416))
            )
            FrameBorderStyle.WET_PLATE_COLLODION -> Brush.verticalGradient(
                listOf(Color(0xFF1B1916), Color(0xFF0E0D0B))
            )
            FrameBorderStyle.DAGUERREOTYPE_CASE -> Brush.verticalGradient(
                listOf(Color(0xFF3B1A18), Color(0xFF220E0D))
            )
            FrameBorderStyle.AUTOCHROME_GLASS -> Brush.verticalGradient(
                listOf(Color(0xFF1A1816), Color(0xFF0D0C0B))
            )
            FrameBorderStyle.PINHOLE_WOOD_BOX -> Brush.verticalGradient(
                listOf(Color(0xFF3E2716), Color(0xFF24160C))
            )
            else -> Brush.verticalGradient(
                listOf(Color(0xFF101014), Color(0xFF08080A))
            )
        }

        val outerBorderColor = when {
            isPaperCard -> Color(0xFFD4CEC2)
            style == FrameBorderStyle.DISPOSABLE_CARD -> Color(0xFFE8B931).copy(alpha = 0.65f)
            style == FrameBorderStyle.DAGUERREOTYPE_CASE -> Color(0xFFD4AF37)
            style == FrameBorderStyle.WET_PLATE_COLLODION -> Color(0xFFC5A87E).copy(alpha = 0.70f)
            style == FrameBorderStyle.PINHOLE_WOOD_BOX -> Color(0xFFB88A4C).copy(alpha = 0.72f)
            else -> Color(0xFF2B2E38)
        }

        Box(
            modifier = Modifier
                .size(outerW, outerH)
                .shadow(
                    elevation = if (isPaperCard) 10.dp else 6.dp,
                    shape = RoundedCornerShape(metrics.outerCorner),
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(metrics.outerCorner))
                .background(outerBgBrush)
                .border(1.dp, outerBorderColor, RoundedCornerShape(metrics.outerCorner))
        ) {
            // 1. Background Contour Artwork (sprockets, V-notches, markings, Polaroid chin)
            when (style) {
                FrameBorderStyle.FILM_35MM_SPROCKETS, FrameBorderStyle.PANORAMIC_XPAN -> {
                    val labelTop = if (style == FrameBorderStyle.PANORAMIC_XPAN) {
                        "XPAN 24×65  •  ${film.code}  •  ISO ${film.iso}"
                    } else {
                        "PELOCHE 135  •  ${film.code}  •  ISO ${film.iso}"
                    }
                    val labelBottom = "▲ %02dA      ${body.shortCode}      %02d ▲".format(frameNumber, frameNumber + 1)
                    // Top sprocket rail
                    HorizontalSprocketRail(
                        label = labelTop,
                        compact = compact,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(metrics.padTop)
                    )
                    // Bottom sprocket rail
                    HorizontalSprocketRail(
                        label = labelBottom,
                        compact = compact,
                        invert = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(metrics.padBottom)
                    )
                }

                FrameBorderStyle.HALF_FRAME_STRIP -> {
                    VerticalSprocketRail(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(metrics.padStart)
                    )
                    VerticalSprocketRail(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(metrics.padEnd)
                    )
                }

                FrameBorderStyle.MEDIUM_FORMAT_6X6 -> {
                    // Authentic Hasselblad A12 dual V-notches along the left film-gate edge
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val notchX = metrics.padStart.toPx()
                        val y1 = size.height * 0.28f
                        val y2 = size.height * 0.36f
                        val depth = 5f * scale
                        val path = Path().apply {
                            moveTo(notchX, y1 - depth)
                            lineTo(notchX - depth, y1)
                            lineTo(notchX, y1 + depth)
                            moveTo(notchX, y2 - depth)
                            lineTo(notchX - depth, y2)
                            lineTo(notchX, y2 + depth)
                        }
                        drawPath(path, color = Color(0xFF040405))
                    }
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "120 • 6×6",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                color = KodakAmber.copy(alpha = 0.78f)
                            )
                            Text(
                                text = "${film.code} • N°%02d".format(frameNumber),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                color = KodakAmber.copy(alpha = 0.78f)
                            )
                        }
                    }
                }

                FrameBorderStyle.HOLGA_MASK_6X6 -> {
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HOLGA 120N • FIXE ∞",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                color = Color(0xFF8C867B)
                            )
                            // Iconic Holga red film-backing counter peephole
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(LeicaCrimson)
                                )
                                Text(
                                    text = "%02d".format(frameNumber),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                    color = LeicaCrimson
                                )
                            }
                        }
                    }
                }

                FrameBorderStyle.DISPOSABLE_CARD -> {
                    // Retro yellow/red disposable flash camera accent stripe at bottom
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(if (compact) 2.dp else 3.dp)
                            .background(Color(0xFFE8B931))
                    )
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "JETABLE 27 • 32mm FIXE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                color = Color(0xFFE8B931)
                            )
                            Text(
                                text = "${film.code} • %02d/27".format(frameNumber),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FontFamily.Monospace),
                                color = Color(0xFFC8C2B8)
                            )
                        }
                    }
                }

                FrameBorderStyle.POLAROID_WHITE -> {
                    // Classic Polaroid SX-70 thick bottom chin with subtle instant caption
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(metrics.padBottom)
                            .padding(horizontal = (14 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Peloche SX-70",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = if (compact) 8.sp else 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF3E3B35)
                            )
                            if (!compact) {
                                Text(
                                    text = "${film.name} • ISO ${film.iso}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = Color(0xFF7B756A)
                                )
                            }
                        }
                        // Classic tiny Polaroid spectrum bar
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(
                                Color(0xFFE63946),
                                Color(0xFFF4A261),
                                Color(0xFFE9C46A),
                                Color(0xFF2A9D8F),
                                Color(0xFF264653)
                            ).forEach { barCol ->
                                Box(
                                    modifier = Modifier
                                        .size(width = if (compact) 3.dp else 4.dp, height = if (compact) 8.dp else 13.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(barCol)
                                )
                            }
                        }
                    }
                }

                FrameBorderStyle.INSTAX_MINI_WHITE -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(metrics.padBottom)
                            .padding(horizontal = (12 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "INSTAX • ${film.code}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (compact) 7.sp else 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF55524C)
                        )
                        Text(
                            text = "%02d/10".format(frameNumber),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (compact) 7.sp else 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF8A857C)
                        )
                    }
                }

                FrameBorderStyle.COMPACT_LAB_PRINT -> {
                    // Clean minimalist 90s satin print paper border
                    Spacer(Modifier.size(1.dp))
                }

                FrameBorderStyle.WET_PLATE_COLLODION -> {
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "COLLODION 1855 • 4×5",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = Color(0xFFC5A87E)
                            )
                            Text(
                                text = "PLAQUE %02d/04".format(frameNumber),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF9C8B72)
                            )
                        }
                    }
                }

                FrameBorderStyle.DAGUERREOTYPE_CASE -> {
                    // Gilded Victorian brass inner mat border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((5 * scale).dp)
                            .border(
                                width = if (compact) 1.dp else 1.8.dp,
                                color = Color(0xFFD4AF37).copy(alpha = 0.85f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    if (!compact) {
                        Text(
                            text = "DAGUERRÉOTYPE 1842 • PL. %02d".format(frameNumber),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.sp,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.9.sp
                            ),
                            color = Color(0xFFE6C260),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                        )
                    }
                }

                FrameBorderStyle.AUTOCHROME_GLASS -> {
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFEBE1CD))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Autochrome Lumière 1907",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 6.5.sp,
                                        fontStyle = FontStyle.Italic
                                    ),
                                    color = Color(0xFF2F251C)
                                )
                            }
                            Text(
                                text = "N°%02d".format(frameNumber),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFFA89F92)
                            )
                        }
                    }
                }

                FrameBorderStyle.PINHOLE_WOOD_BOX -> {
                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "STÉNOPÉ 0,3mm • f/128",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFFD9B472)
                            )
                            Text(
                                text = "%02d/08".format(frameNumber),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFFAA8C5A)
                            )
                        }
                    }
                }
            }

            // 2. Inner Optical Aperture (EXACT aspect ratio, ZERO stretching!)
            Box(
                modifier = Modifier
                    .padding(
                        start = metrics.padStart,
                        top = metrics.padTop,
                        end = metrics.padEnd,
                        bottom = metrics.padBottom
                    )
                    .size(innerW, innerH)
                    .clip(RoundedCornerShape(metrics.innerCorner))
                    .background(Color.Black)
                    .border(
                        width = if (isPaperCard) 0.8.dp else 0.6.dp,
                        color = if (isPaperCard) Color(0x55000000) else Color(0x44FFFFFF),
                        shape = RoundedCornerShape(metrics.innerCorner)
                    ),
                content = content
            )
        }
    }
}

@Composable
private fun HorizontalSprocketRail(
    label: String,
    compact: Boolean,
    invert: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        if (!invert && !compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 6.5.sp,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = KodakAmber.copy(alpha = 0.80f),
                maxLines = 1
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
        ) {
            val holeW = if (compact) 8f else 15f
            val holeH = size.height * 0.78f
            val gap = if (compact) 8f else 14f
            val topY = (size.height - holeH) / 2f
            var x = 4f
            while (x + holeW < size.width - 2f) {
                drawRoundRect(
                    color = Color(0xFF262831),
                    topLeft = Offset(x, topY),
                    size = Size(holeW, holeH),
                    cornerRadius = CornerRadius(2.5f, 2.5f)
                )
                x += holeW + gap
            }
        }
        if (invert && !compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 6.5.sp,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = BrassAccent.copy(alpha = 0.78f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VerticalSprocketRail(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(vertical = 6.dp)) {
        val holeW = size.width * 0.44f
        val holeH = 14f
        val gap = 14f
        val leftX = (size.width - holeW) / 2f
        var y = 6f
        while (y + holeH < size.height - 4f) {
            drawRoundRect(
                color = Color(0xFF262831),
                topLeft = Offset(leftX, y),
                size = Size(holeW, holeH),
                cornerRadius = CornerRadius(2.5f, 2.5f)
            )
            y += holeH + gap
        }
    }
}
