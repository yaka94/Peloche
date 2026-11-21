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
 * adapting silhouette for Classic SLR, Rangefinder, Half-Frame, Square TLR 6x6, XPan,
 * Polaroid SX-70 (iconic tan folding wedge + rainbow stripe), Instax Mini, and Toy/Disposable.
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
        val isPolaroid = body.id.contains("polaroid")
        val isInstax = body.id.contains("instax")
        val isPinholeWood = body.id == "body_pinhole_1890"
        val isHistoricalBellows = body.isHistoricalLargeFormat && !isPinholeWood
        val isSquareTlr = body.aspectRatioSpec == AspectRatioSpec.SQUARE_1_1 && !isPolaroid
        val isPanoramic = body.aspectRatioSpec == AspectRatioSpec.PANORAMIC_XPAN

        // Soft ground contact shadow under every camera body
        drawOval(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(w * 0.08f, h * 0.87f),
            size = Size(w * 0.84f, h * 0.12f)
        )

        if (isHistoricalBellows) {
            // 19th-century mahogany wooden bellows large-format view camera with brass rails & Petzval lens!
            val baseY = h * 0.82f
            // Polished brass folding baseboard rail
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(topPlate, Color(0xFFDFBE76), topPlate)),
                topLeft = Offset(w * 0.08f, baseY),
                size = Size(w * 0.84f, h * 0.09f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Rear mahogany wooden plate-holder frame
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(leather.copy(alpha = 0.95f), Color(0xFF28160C))),
                topLeft = Offset(w * 0.10f, h * 0.12f),
                size = Size(w * 0.24f, h * 0.72f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = topPlate.copy(alpha = 0.72f),
                topLeft = Offset(w * 0.10f, h * 0.12f),
                size = Size(w * 0.24f, h * 0.72f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.4f)
            )
            // Accordion leather bellows pleats tapering from rear frame to front lens board
            val pleats = 5
            val bStartX = w * 0.34f
            val bEndX = w * 0.68f
            val stepX = (bEndX - bStartX) / pleats
            for (i in 0 until pleats) {
                val t0 = i.toFloat() / pleats
                val t1 = (i + 1f) / pleats
                val x0 = bStartX + i * stepX
                val x1 = x0 + stepX
                val top0 = h * (0.16f + t0 * 0.10f)
                val top1 = h * (0.16f + t1 * 0.10f)
                val bot0 = h * (0.80f - t0 * 0.08f)
                val bot1 = h * (0.80f - t1 * 0.08f)
                val shade = if (i % 2 == 0) Color(0xFF2D1B14) else Color(0xFF180F0C)
                val pleatPath = Path().apply {
                    moveTo(x0, top0)
                    lineTo(x1, top1)
                    lineTo(x1, bot1)
                    lineTo(x0, bot0)
                    close()
                }
                drawPath(pleatPath, color = shade)
                drawLine(Color(0xFF5A3E30), Offset(x0, top0), Offset(x0, bot0), strokeWidth = 1.0f)
            }
            // Front mahogany lens standard with brass corner brackets
            drawRoundRect(
                color = leather,
                topLeft = Offset(w * 0.68f, h * 0.24f),
                size = Size(w * 0.10f, h * 0.58f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Gleaming brass Petzval / Chevalier lens barrel protruding right
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFEBD08B), topPlate, Color(0xFF8C6B2A))),
                topLeft = Offset(w * 0.77f, h * 0.36f),
                size = Size(w * 0.15f, h * 0.30f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Brass lens hood lip & sapphire glass glint
            drawRect(
                color = Color(0xFF1B2732),
                topLeft = Offset(w * 0.90f, h * 0.39f),
                size = Size(w * 0.03f, h * 0.24f)
            )
            // Signature accent badge on rear frame
            drawCircle(accent, radius = 3.2f, center = Offset(w * 0.22f, h * 0.26f))
            return@Canvas
        }

        if (isPinholeWood) {
            // 1890 Handcrafted walnut pinhole box camera with brass corner clasps & tiny centered brass aperture
            val bLeft = w * 0.15f
            val bWidth = w * 0.70f
            val bTop = h * 0.14f
            val bHeight = h * 0.74f
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF593821), leather, Color(0xFF2B190D))),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth, bHeight),
                cornerRadius = CornerRadius(5f, 5f)
            )
            // Subtle walnut woodgrain lines
            for (g in 1..4) {
                val gy = bTop + bHeight * (g / 5f)
                drawLine(
                    color = Color(0xFF24140A).copy(alpha = 0.45f),
                    start = Offset(bLeft + 3f, gy),
                    end = Offset(bLeft + bWidth - 3f, gy),
                    strokeWidth = 1.1f
                )
            }
            // Brass corner reinforcement clasps
            val corner = 7f
            drawRect(topPlate, Offset(bLeft, bTop), Size(corner, corner))
            drawRect(topPlate, Offset(bLeft + bWidth - corner, bTop), Size(corner, corner))
            drawRect(topPlate, Offset(bLeft, bTop + bHeight - corner), Size(corner, corner))
            drawRect(topPlate, Offset(bLeft + bWidth - corner, bTop + bHeight - corner), Size(corner, corner))
            // Brass wooden shutter sliding plate + microscopic 0.3mm pinhole
            val c = Offset(bLeft + bWidth * 0.5f, bTop + bHeight * 0.52f)
            drawCircle(topPlate, radius = bHeight * 0.22f, center = c)
            drawCircle(Color(0xFF23180E), radius = bHeight * 0.14f, center = c)
            drawCircle(Color(0xFF050506), radius = 2.6f, center = c)
            drawRoundRect(
                color = topPlate.copy(alpha = 0.65f),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth, bHeight),
                cornerRadius = CornerRadius(5f, 5f),
                style = Stroke(width = 1.4f)
            )
            return@Canvas
        }

        if (isPolaroid) {
            // Iconic Polaroid SX-70 / 600 chassis with rainbow spectrum stripe & top flash bar
            val bLeft = w * 0.14f
            val bWidth = w * 0.72f
            val bTop = h * 0.12f
            val bHeight = h * 0.78f

            // Upper angled viewing hood
            val hood = Path().apply {
                moveTo(bLeft + bWidth * 0.12f, bTop)
                lineTo(bLeft + bWidth * 0.88f, bTop)
                lineTo(bLeft + bWidth, bTop + bHeight * 0.36f)
                lineTo(bLeft, bTop + bHeight * 0.36f)
                close()
            }
            drawPath(
                hood,
                brush = Brush.verticalGradient(listOf(topPlate, topPlate.copy(alpha = 0.78f)))
            )
            // Main body box
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(leather.copy(alpha = 0.95f), leather)),
                topLeft = Offset(bLeft, bTop + bHeight * 0.28f),
                size = Size(bWidth, bHeight * 0.72f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            // Iconic Polaroid rainbow spectrum vertical stripe
            val stripeColors = listOf(
                Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
                Color(0xFF43A047), Color(0xFF1E88E5)
            )
            val stripeW = (bWidth * 0.12f) / stripeColors.size
            val stripeStartX = bLeft + bWidth * 0.44f
            stripeColors.forEachIndexed { idx, col ->
                drawRect(
                    color = col,
                    topLeft = Offset(stripeStartX + idx * stripeW, bTop + bHeight * 0.62f),
                    size = Size(stripeW + 0.5f, bHeight * 0.32f)
                )
            }
            // Front film ejection slot
            drawRect(
                color = Color(0xFF0A0A0C),
                topLeft = Offset(bLeft + bWidth * 0.10f, bTop + bHeight * 0.86f),
                size = Size(bWidth * 0.80f, bHeight * 0.08f)
            )
            // Center-front Polaroid taking lens
            val c = Offset(bLeft + bWidth * 0.50f, bTop + bHeight * 0.44f)
            val lr = bWidth * 0.20f
            drawCircle(SilverChrome, radius = lr, center = c)
            drawCircle(Color(0xFF141312), radius = lr * 0.80f, center = c)
            drawCircle(Color(0xFF1B2B38), radius = lr * 0.56f, center = c)
            drawCircle(Color.White.copy(alpha = 0.40f), radius = lr * 0.17f, center = Offset(c.x - lr * 0.22f, c.y - lr * 0.22f))
            // Red shutter button
            drawCircle(LeicaCrimson, radius = 3.8f, center = Offset(bLeft + bWidth * 0.82f, bTop + bHeight * 0.52f))
            return@Canvas
        }

        if (isInstax) {
            // Retro Instax Mini 90 Neo Classic (two-tone silver & dark leather vertical compact)
            val bLeft = w * 0.20f
            val bWidth = w * 0.60f
            val bTop = h * 0.08f
            val bHeight = h * 0.84f

            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(leather, leather.copy(alpha = 0.84f), leather)),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth, bHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Left brushed silver chrome band (Instax Neo Classic signature)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(topPlate, topPlate.copy(alpha = 0.72f))),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth * 0.32f, bHeight),
                cornerRadius = CornerRadius(7f, 7f)
            )
            // Built-in rectangular flash window top-left
            drawRoundRect(
                color = Color(0xFFE6EDF5),
                topLeft = Offset(bLeft + bWidth * 0.08f, bTop + bHeight * 0.14f),
                size = Size(bWidth * 0.16f, bHeight * 0.22f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            // Optical viewfinder window top-right
            drawRoundRect(
                color = Color(0xFF15222E),
                topLeft = Offset(bLeft + bWidth * 0.72f, bTop + bHeight * 0.12f),
                size = Size(bWidth * 0.16f, bHeight * 0.16f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            // Protruding 60mm Instax lens barrel
            val c = Offset(bLeft + bWidth * 0.58f, bTop + bHeight * 0.56f)
            val lr = bWidth * 0.30f
            drawCircle(SilverChrome, radius = lr, center = c)
            drawCircle(Color(0xFF18191D), radius = lr * 0.82f, center = c)
            drawCircle(Color(0xFF1A2A36), radius = lr * 0.55f, center = c)
            drawCircle(Color.White.copy(alpha = 0.38f), radius = lr * 0.16f, center = Offset(c.x - lr * 0.22f, c.y - lr * 0.20f))
            drawRoundRect(
                color = HairlineMetal.copy(alpha = 0.5f),
                topLeft = Offset(bLeft, bTop),
                size = Size(bWidth, bHeight),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 1.4f)
            )
            return@Canvas
        }

        val bodyLeft = if (isSquareTlr) w * 0.20f else if (isPanoramic) w * 0.03f else w * 0.08f
        val bodyWidth = if (isSquareTlr) w * 0.60f else if (isPanoramic) w * 0.94f else w * 0.84f
        val bodyTop = if (isSquareTlr) h * 0.08f else h * 0.22f
        val bodyHeight = h - bodyTop - h * 0.08f

        // Shutter button & rewind crank on top plate
        drawRoundRect(
            color = SilverChrome.copy(alpha = 0.90f),
            topLeft = Offset(bodyLeft + bodyWidth * 0.74f, bodyTop - h * 0.10f),
            size = Size(bodyWidth * 0.14f, h * 0.11f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = topPlate.copy(alpha = 0.85f),
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
            drawPath(
                prism,
                brush = Brush.verticalGradient(listOf(SilverChrome.copy(alpha = 0.85f), topPlate))
            )
        }

        // Main leatherette body chassis with subtle 3D side shading
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(leather.copy(alpha = 0.85f), leather, Color(0xFF0A0A0D)),
                startX = bodyLeft,
                endX = bodyLeft + bodyWidth
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Top brushed metallic plate with realistic sheen
        val plateH = if (isSquareTlr) bodyHeight * 0.20f else bodyHeight * 0.31f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(topPlate, topPlate.copy(alpha = 0.78f), topPlate.copy(alpha = 0.58f)),
                startY = bodyTop,
                endY = bodyTop + plateH
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, plateH),
            cornerRadius = CornerRadius(5f, 5f)
        )

        // Crisp machined edge bevel
        drawRoundRect(
            color = HairlineMetal.copy(alpha = 0.55f),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 1.4f)
        )

        // Optical viewfinder window on top plate with sapphire reflection
        drawRoundRect(
            color = Color(0xFF182633),
            topLeft = Offset(bodyLeft + bodyWidth * 0.14f, bodyTop + plateH * 0.24f),
            size = Size(bodyWidth * 0.16f, plateH * 0.52f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(bodyLeft + bodyWidth * 0.16f, bodyTop + plateH * 0.32f),
            end = Offset(bodyLeft + bodyWidth * 0.24f, bodyTop + plateH * 0.32f),
            strokeWidth = 1f
        )

        // Lens mount / Twin lens circles
        val centerX = bodyLeft + bodyWidth * 0.52f
        if (isSquareTlr) {
            val r = bodyWidth * 0.21f
            drawCircle(SilverChrome.copy(alpha = 0.8f), radius = r, center = Offset(centerX, bodyTop + bodyHeight * 0.40f))
            drawCircle(Color(0xFF101920), radius = r * 0.72f, center = Offset(centerX, bodyTop + bodyHeight * 0.40f))
            drawCircle(SilverChrome.copy(alpha = 0.85f), radius = r * 1.06f, center = Offset(centerX, bodyTop + bodyHeight * 0.74f))
            drawCircle(Color(0xFF101920), radius = r * 0.74f, center = Offset(centerX, bodyTop + bodyHeight * 0.74f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.16f, center = Offset(centerX - r * 0.2f, bodyTop + bodyHeight * 0.70f))
        } else {
            val mountR = bodyHeight * 0.37f
            val mountCenter = Offset(centerX, bodyTop + bodyHeight * 0.58f)
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(SilverChrome, Color(0xFF6B6F78), SilverChrome.copy(alpha = 0.8f)),
                    start = Offset(mountCenter.x - mountR, mountCenter.y - mountR),
                    end = Offset(mountCenter.x + mountR, mountCenter.y + mountR)
                ),
                radius = mountR,
                center = mountCenter
            )
            drawCircle(Color(0xFF141312), radius = mountR * 0.80f, center = mountCenter)
            drawCircle(Color(0xFF1E2D38), radius = mountR * 0.55f, center = mountCenter)
            drawCircle(
                Color.White.copy(alpha = 0.38f),
                radius = mountR * 0.17f,
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
