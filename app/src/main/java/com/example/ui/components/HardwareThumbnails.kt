package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AspectRatioSpec
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmStockModel
import com.example.domain.model.LensModel
import com.example.ui.theme.BrushedGunmetal
import com.example.ui.theme.HairlineMetal
import com.example.ui.theme.KodakAmber
import com.example.ui.theme.LeicaCrimson
import com.example.ui.theme.MatteLeather
import com.example.ui.theme.SilverChrome

/**
 * Tactile anodized dark carbon-metal surface gradient matching Peloche's obsidian charter.
 */
fun brushedMetalBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1C1E24),
        BrushedGunmetal,
        Color(0xFF0E0F12)
    )
)

fun leatheretteBrush(): Brush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF171920),
        MatteLeather
    )
)

/**
 * Custom photorealistic-style Canvas miniature of a Camera Body (Boîtier),
 * adapting silhouette for Classic SLR, Rangefinder, Half-Frame, Square TLR 6x6, XPan, and Toy/Disposable.
 */
@Composable
fun CameraBodyThumbnail(
    body: CameraBodyModel,
    modifier: Modifier = Modifier.size(width = 68.dp, height = 48.dp)
) {
    val topPlate = Color(body.topPlateHex)
    val leather = Color(body.leatherHex)
    val accent = Color(body.accentHex)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val isSquareTlr = body.aspectRatioSpec == AspectRatioSpec.SQUARE_1_1
        val isPanoramic = body.aspectRatioSpec == AspectRatioSpec.PANORAMIC_XPAN

        val bodyLeft = if (isSquareTlr) w * 0.20f else if (isPanoramic) w * 0.03f else w * 0.08f
        val bodyWidth = if (isSquareTlr) w * 0.60f else if (isPanoramic) w * 0.94f else w * 0.84f
        val bodyTop = if (isSquareTlr) h * 0.08f else h * 0.22f
        val bodyHeight = h - bodyTop - h * 0.06f

        // Shutter button & rewind crank on top plate
        drawRoundRect(
            color = SilverChrome.copy(alpha = 0.85f),
            topLeft = Offset(bodyLeft + bodyWidth * 0.74f, bodyTop - h * 0.10f),
            size = Size(bodyWidth * 0.14f, h * 0.11f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = topPlate.copy(alpha = 0.8f),
            topLeft = Offset(bodyLeft + bodyWidth * 0.08f, bodyTop - h * 0.08f),
            size = Size(bodyWidth * 0.18f, h * 0.09f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Pentaprism bump for SLR / Classic S
        if (body.id.contains("slr") || body.id.contains("classic")) {
            val prism = Path().apply {
                moveTo(bodyLeft + bodyWidth * 0.30f, bodyTop)
                lineTo(bodyLeft + bodyWidth * 0.40f, bodyTop - h * 0.16f)
                lineTo(bodyLeft + bodyWidth * 0.60f, bodyTop - h * 0.16f)
                lineTo(bodyLeft + bodyWidth * 0.70f, bodyTop)
                close()
            }
            drawPath(prism, color = topPlate)
        }

        // Main leatherette body chassis
        drawRoundRect(
            color = leather,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Top brushed metallic plate
        val plateH = if (isSquareTlr) bodyHeight * 0.20f else bodyHeight * 0.30f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(topPlate, topPlate.copy(alpha = 0.72f)),
                startY = bodyTop,
                endY = bodyTop + plateH
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, plateH),
            cornerRadius = CornerRadius(5f, 5f)
        )

        // Subtle leatherette grip dots
        drawRoundRect(
            color = HairlineMetal.copy(alpha = 0.45f),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 1.5f)
        )

        // Optical viewfinder window on top plate
        drawRoundRect(
            color = Color(0xFF1A252E),
            topLeft = Offset(bodyLeft + bodyWidth * 0.14f, bodyTop + plateH * 0.25f),
            size = Size(bodyWidth * 0.16f, plateH * 0.52f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Lens mount / Twin lens circles
        val centerX = bodyLeft + bodyWidth * 0.52f
        if (isSquareTlr) {
            val r = bodyWidth * 0.20f
            // Upper viewing lens & lower taking lens
            drawCircle(Color(0xFF33312E), radius = r, center = Offset(centerX, bodyTop + bodyHeight * 0.40f))
            drawCircle(Color(0xFF101920), radius = r * 0.70f, center = Offset(centerX, bodyTop + bodyHeight * 0.40f))
            drawCircle(Color(0xFF33312E), radius = r * 1.05f, center = Offset(centerX, bodyTop + bodyHeight * 0.74f))
            drawCircle(Color(0xFF101920), radius = r * 0.72f, center = Offset(centerX, bodyTop + bodyHeight * 0.74f))
        } else {
            val mountR = bodyHeight * 0.36f
            val mountCenter = Offset(centerX, bodyTop + bodyHeight * 0.58f)
            drawCircle(SilverChrome.copy(alpha = 0.75f), radius = mountR, center = mountCenter)
            drawCircle(Color(0xFF141312), radius = mountR * 0.80f, center = mountCenter)
            drawCircle(Color(0xFF1E2D38), radius = mountR * 0.54f, center = mountCenter)
            drawCircle(
                Color.White.copy(alpha = 0.32f),
                radius = mountR * 0.16f,
                center = Offset(mountCenter.x - mountR * 0.20f, mountCenter.y - mountR * 0.20f)
            )
        }

        // Signature brand red/brass dot accent
        drawCircle(
            color = accent,
            radius = 3.2f,
            center = Offset(bodyLeft + bodyWidth * 0.84f, bodyTop + plateH * 0.5f)
        )
    }
}

/**
 * Custom Canvas front-element miniature of an interchangeable Lens (Objectif)
 * with knurled ring, multi-coated optical glass reflection, and focal ring accent.
 */
@Composable
fun LensThumbnail(
    lens: LensModel,
    modifier: Modifier = Modifier.size(48.dp)
) {
    val barrel = Color(lens.barrelColorHex)
    val ringAccent = Color(lens.ringAccentHex)
    val coating = Color(lens.coatingTintHex)

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2f

        // Outer knurled metal barrel
        drawCircle(
            brush = Brush.radialGradient(
                listOf(barrel.copy(alpha = 0.95f), Color(0xFF0C0B0A)),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )

        // Precision machined knurling notches around outer focus ring
        val notchCount = 24
        for (i in 0 until notchCount) {
            val angle = (i.toDouble() / notchCount) * Math.PI * 2.0
            val cosA = kotlin.math.cos(angle).toFloat()
            val sinA = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = SilverChrome.copy(alpha = if (i % 2 == 0) 0.34f else 0.14f),
                start = Offset(c.x + cosA * r * 0.88f, c.y + sinA * r * 0.88f),
                end = Offset(c.x + cosA * r * 0.99f, c.y + sinA * r * 0.99f),
                strokeWidth = 1.2f
            )
        }

        // Aperture ring engraved accent ring + red mount index dot
        drawCircle(
            color = ringAccent.copy(alpha = 0.90f),
            radius = r * 0.81f,
            center = c,
            style = Stroke(width = 1.6f)
        )
        drawCircle(
            color = LeicaCrimson,
            radius = 2.0f,
            center = Offset(c.x, c.y - r * 0.81f)
        )

        // Inner baffled matte optical tube
        drawCircle(color = Color(0xFF0D0D0D), radius = r * 0.74f, center = c)

        // Multi-coated optical glass dome
        val glassR = if (lens.focalLengthMm <= 28) r * 0.63f else if (lens.focalLengthMm >= 85) r * 0.56f else r * 0.59f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coating.copy(alpha = 0.60f),
                    Color(0xFF0A141E),
                    Color(0xFF050608)
                ),
                center = Offset(c.x - glassR * 0.25f, c.y - glassR * 0.25f),
                radius = glassR * 1.45f
            ),
            radius = glassR,
            center = c
        )

        // 8-blade mechanical aperture diaphragm iris
        val blades = 8
        val irisOuter = glassR * 0.68f
        val irisInner = if (lens.maxAperture.contains("1.4") || lens.maxAperture.contains("1.8")) glassR * 0.42f else glassR * 0.28f
        for (i in 0 until blades) {
            val a1 = (i.toDouble() / blades) * Math.PI * 2.0
            val a2 = ((i + 1.35) / blades) * Math.PI * 2.0
            drawLine(
                color = SilverChrome.copy(alpha = 0.24f),
                start = Offset(c.x + kotlin.math.cos(a1).toFloat() * irisInner, c.y + kotlin.math.sin(a1).toFloat() * irisInner),
                end = Offset(c.x + kotlin.math.cos(a2).toFloat() * irisOuter, c.y + kotlin.math.sin(a2).toFloat() * irisOuter),
                strokeWidth = 1.0f
            )
        }
        drawCircle(color = Color(0xFF040507), radius = irisInner, center = c)

        // Curved specular optical coating glints
        drawArc(
            color = Color.White.copy(alpha = 0.45f),
            startAngle = 205f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(c.x - glassR * 0.78f, c.y - glassR * 0.78f),
            size = Size(glassR * 1.56f, glassR * 1.56f),
            style = Stroke(width = 1.6f)
        )
        drawCircle(
            color = coating.copy(alpha = 0.78f),
            radius = glassR * 0.14f,
            center = Offset(c.x + glassR * 0.30f, c.y + glassR * 0.28f)
        )
    }
}

/**
 * Custom Canvas 35mm/120 Film Canister (Cartouche de pellicule) with metallic end caps,
 * protruding dark film leader tongue, color-coded label stripe, and expired warning dot.
 */
@Composable
fun FilmCanisterThumbnail(
    film: FilmStockModel,
    expirationDate: FilmExpirationDate? = null,
    modifier: Modifier = Modifier.size(width = 46.dp, height = 54.dp)
) {
    val bodyCol = Color(film.canisterColorHex)
    val stripeCol = Color(film.stripeColorHex)
    val isExpired = expirationDate?.isExpired == true

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val canLeft = w * 0.12f
        val canW = w * 0.58f
        val canTop = h * 0.18f
        val canH = h * 0.72f

        // Protruding 35mm film leader tongue on the right
        val leaderPath = Path().apply {
            moveTo(canLeft + canW - 1f, canTop + canH * 0.18f)
            lineTo(w * 0.94f, canTop + canH * 0.18f)
            lineTo(w * 0.94f, canTop + canH * 0.56f)
            lineTo(w * 0.80f, canTop + canH * 0.76f)
            lineTo(canLeft + canW - 1f, canTop + canH * 0.76f)
            close()
        }
        drawPath(leaderPath, color = Color(0xFF473326))
        // Sprocket holes on leader
        for (i in 0..1) {
            drawRoundRect(
                color = MatteLeather,
                topLeft = Offset(canLeft + canW + 3f + i * 7f, canTop + canH * 0.24f),
                size = Size(4f, 4f),
                cornerRadius = CornerRadius(1f, 1f)
            )
        }

        // Top spindle spool nub
        drawRoundRect(
            color = Color(0xFF2E2C29),
            topLeft = Offset(canLeft + canW * 0.28f, h * 0.04f),
            size = Size(canW * 0.44f, h * 0.14f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Metallic top & bottom caps
        drawRoundRect(
            color = Color(0xFF201F1D),
            topLeft = Offset(canLeft - 2f, canTop - 3f),
            size = Size(canW + 4f, 7f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = Color(0xFF201F1D),
            topLeft = Offset(canLeft - 2f, canTop + canH - 4f),
            size = Size(canW + 4f, 7f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Main cylindrical canister label with horizontal 3D specular gradient
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    bodyCol.copy(alpha = 0.78f),
                    bodyCol,
                    bodyCol.copy(alpha = 0.65f)
                ),
                startX = canLeft,
                endX = canLeft + canW
            ),
            topLeft = Offset(canLeft, canTop + 3f),
            size = Size(canW, canH - 6f)
        )

        // Graphic accent band
        drawRect(
            color = stripeCol,
            topLeft = Offset(canLeft, canTop + canH * 0.56f),
            size = Size(canW, canH * 0.22f)
        )

        // Subtle DX silver checkered contact strip
        drawRect(
            color = SilverChrome.copy(alpha = 0.65f),
            topLeft = Offset(canLeft + 3f, canTop + 7f),
            size = Size(canW * 0.22f, canH * 0.34f)
        )

        // Small warning amber/crimson dot if expired
        if (isExpired) {
            drawCircle(
                color = if ((expirationDate?.yearsExpired ?: 0f) >= 5f) LeicaCrimson else KodakAmber,
                radius = 5f,
                center = Offset(w * 0.84f, h * 0.16f)
            )
            drawCircle(
                color = Color.Black,
                radius = 5f,
                center = Offset(w * 0.84f, h * 0.16f),
                style = Stroke(width = 1.2f)
            )
        }
    }
}

@Composable
fun MechanicalDivider(height: Dp = 1.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.horizontalGradient(
                listOf(Color.Transparent, HairlineMetal, Color.Transparent)
            )
        ).size(width = Dp.Unspecified, height = height)
    )
}
