package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AnodizedSlate
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.WarmEngraving

/**
 * Procedural black pebbled camera-leatherette backdrop texture ("cuir grainé vulcanite").
 * Draws a deep obsidian radial vignette overlaid with deterministic fine leather pebbles
 * for an authentic tactile analog-camera feel with zero bitmap memory overhead.
 */
fun Modifier.blackLeatherTexture(): Modifier = this.drawBehind {
    // Deep matte vulcanite leather base with subtle top-center ambient darkroom falloff
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF16181F),
                Color(0xFF0E0F13),
                MatteLeather
            ),
            center = Offset(size.width * 0.5f, size.height * 0.14f),
            radius = maxOf(size.width, size.height) * 0.95f
        )
    )

    // Fine deterministic pebble grain pattern
    val step = 14f
    val highlight = Color(0xFFFFFFFF).copy(alpha = 0.028f)
    val dimple = Color(0xFF000000).copy(alpha = 0.24f)
    var row = 0
    var y = 4f
    while (y < size.height) {
        val rowOffset = if (row % 2 == 0) 0f else step * 0.5f
        var col = 0
        var x = rowOffset + 3f
        while (x < size.width) {
            val hash = ((row * 73856093) xor (col * 19349663)) and 0xFF
            val jitterX = ((hash and 0x0F) - 7) * 0.32f
            val jitterY = (((hash shr 4) and 0x0F) - 7) * 0.32f
            val r = 1.6f + (hash % 3) * 0.45f
            val cx = x + jitterX
            val cy = y + jitterY
            drawCircle(color = dimple, radius = r, center = Offset(cx, cy + 0.9f))
            drawCircle(color = highlight, radius = r * 0.68f, center = Offset(cx, cy))
            x += step
            col++
        }
        y += step
        row++
    }
}

/**
 * Procedural dark brushed machined metal surface ("métal brossé anodisé").
 * Renders a multi-stop gunmetal sheen, horizontal micro-brushed striations,
 * a 1px top specular bevel, and a grounded bottom shadow lip.
 */
fun Modifier.brushedMetalPlate(
    shape: Shape = RoundedCornerShape(14.dp),
    borderColor: Color = HairlineMetal,
    borderWidth: Dp = 1.dp,
    elevated: Boolean = false
): Modifier = this
    .clip(shape)
    .drawBehind {
        val topTint = if (elevated) Color(0xFF282B36) else Color(0xFF1F222B)
        val midTint = if (elevated) Color(0xFF1C1E26) else Color(0xFF16181F)
        val lowTint = if (elevated) Color(0xFF15171E) else Color(0xFF101116)

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to topTint,
                    0.22f to midTint,
                    0.55f to topTint.copy(alpha = 0.85f),
                    1.0f to lowTint
                )
            )
        )

        // Subtle diagonal metallic specular band
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (elevated) 0.045f else 0.03f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * 0.55f)
            )
        )

        // Fine horizontal brushed-aluminum micro-striations
        var y = 1.5f
        var lineIndex = 0
        while (y < size.height) {
            val isLight = (lineIndex * 31 + 7) % 5 == 0
            val isDark = (lineIndex * 17 + 3) % 4 == 0
            if (isLight) {
                drawLine(
                    color = Color.White.copy(alpha = 0.036f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.8f
                )
            } else if (isDark) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.16f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.9f
                )
            }
            y += 3.2f
            lineIndex++
        }

        // Top machined specular catch-light bevel
        drawRect(
            color = Color.White.copy(alpha = if (elevated) 0.16f else 0.11f),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, 1.4f)
        )
        // Bottom recessed shadow bevel
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(0f, size.height - 1.6f),
            size = Size(size.width, 1.6f)
        )
    }
    .border(borderWidth, borderColor, shape)

/**
 * Recessed dark glass / anodized instrument well used for development recipe readouts,
 * lightbox strips, and precision mechanical counters.
 */
fun Modifier.recessedInstrumentWell(
    shape: Shape = RoundedCornerShape(10.dp),
    borderColor: Color = Color(0xFF282C36)
): Modifier = this
    .clip(shape)
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070709),
                    Color(0xFF0C0D11),
                    Color(0xFF101218)
                )
            )
        )
        // Inner top shadow simulating recessed bezel depth
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                startY = 0f,
                endY = 10f
            ),
            size = Size(size.width, 10f)
        )
        // Subtle bottom lip catch-light
        drawRect(
            color = Color.White.copy(alpha = 0.06f),
            topLeft = Offset(0f, size.height - 1f),
            size = Size(size.width, 1f)
        )
    }
    .border(1.dp, borderColor, shape)

/**
 * Precision slotted gunmetal screw head accent for machined camera/lab plates.
 */
@Composable
fun MachinedScrewHead(
    modifier: Modifier = Modifier,
    size: Dp = 9.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        // Outer recessed socket
        drawCircle(color = Color(0xFF060608), radius = r, center = c)
        // Brushed metallic screw dome
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF4A4E5C), Color(0xFF1C1F27)),
                start = Offset(0f, 0f),
                end = Offset(this.size.width, this.size.height)
            ),
            radius = r * 0.82f,
            center = c
        )
        // Diagonal milled slot
        val d = r * 0.52f
        drawLine(
            color = Color(0xFF08090C),
            start = Offset(c.x - d, c.y - d * 0.65f),
            end = Offset(c.x + d, c.y + d * 0.65f),
            strokeWidth = 1.4f
        )
    }
}

/**
 * Compact analog chemical intensity meter bar used inside the Labo development receipt
 * to visualize exact Contraste, Grain, and Saturation multipliers applied during the bath.
 */
@Composable
fun ChemicalMetricGauge(
    label: String,
    valueText: String,
    fraction: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = WarmEngraving
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = SilverChrome,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AnodizedSlate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.65f), accentColor)
                        )
                    )
            )
        }
    }
}
