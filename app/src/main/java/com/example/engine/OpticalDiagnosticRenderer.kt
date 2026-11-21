package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.domain.model.CameraBodyModel
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.FilmExpirationDate
import com.example.domain.model.FilmStockModel
import com.example.domain.model.HardwareCatalog
import com.example.domain.model.LensModel
import com.example.domain.model.LightLeakMode
import com.example.domain.model.OpticalChainParams
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class DiagnosticChartType(val labelFr: String, val shortLabel: String) {
    SOLID_GRAY("Carte Gris Neutre 18 %", "GRIS 18 %"),
    COLOR_CHECKER("Mire Couleur & Résolution", "MIRE COULEUR")
}

enum class DiagnosticShaderTarget(val labelFr: String, val shortLabel: String, val descriptionFr: String) {
    LIGHT_LEAKS(
        labelFr = "Fuites de lumière",
        shortLabel = "LIGHT LEAKS",
        descriptionFr = "Balayage d'intensité 0 % → 100 % sur les archétypes procéduraux (double passe pré/post courbe)"
    ),
    PLASTIC_SCRATCHES(
        labelFr = "Rayures plastique",
        shortLabel = "RAYURES",
        descriptionFr = "Micro-tourbillons de nettoyage, griffures acryliques et stries de couloir film (0 % → 100 %)"
    ),
    TILT_SHIFT(
        labelFr = "Bascule Tilt-Shift",
        shortLabel = "TILT-SHIFT",
        descriptionFr = "Bokeh circulaire 16 points haut/bas et bande nette centrale (0 % → 100 %)"
    )
}

data class DiagnosticRenderTile(
    val intensityPercent: Int,
    val label: String,
    val bitmap: Bitmap,
    val meanDeltaPct: Float,
    val peakDeltaPct: Float,
    val edgeSharpnessDropPct: Float,
    val centerToEdgeRatio: Float
)

/**
 * Generates standardized synthetic calibration charts (18% neutral gray card & high-contrast
 * ColorChecker + spatial frequency chart) and renders intensity-sweep diagnostics through the
 * real OffscreenGlProcessor optical pipeline (GPU GLSL on device, full-parity CPU fallback on JVM).
 */
object OpticalDiagnosticRenderer {

    val INTENSITY_STEPS = listOf(0f, 0.25f, 0.50f, 0.75f, 1.0f)

    /**
     * Generates a clean, neutral 18%-reflectance perceptual mid-gray card (RGB 118, 118, 118)
     * ideal for exposing Light Leaks, chromatic tint, scratches, and vignetting without scene noise.
     */
    fun createSolidGrayChart(width: Int = 480, height: Int = 320): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(118, 118, 118))
        return bmp
    }

    /**
     * Generates a high-contrast optical diagnostic chart combining:
     * - Top & bottom crisp black/white spatial-frequency grids (to expose Tilt-Shift bokeh falloff)
     * - Center high-contrast checkerboard + precision crosshairs (focus plane sharpness reference)
     * - Macbeth-inspired 6x2 saturated color & grayscale patches (to expose Light Leak / Scratch blending)
     */
    fun createHighContrastColorChart(width: Int = 480, height: Int = 320): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(24, 25, 28))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barStep = max(6, width / 40)

        // Top & bottom crisp vertical/horizontal resolution bars (0..22% and 78..100% height)
        val topBandH = (height * 0.23f).toInt()
        val botBandY = (height * 0.77f).toInt()
        for (y in 0 until height) {
            if (y >= topBandH && y < botBandY) continue
            for (x in 0 until width step barStep) {
                val cell = ((x / barStep) + (y / barStep)) % 2 == 0
                paint.color = if (cell) Color.rgb(242, 242, 240) else Color.rgb(18, 19, 22)
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    min(width, x + barStep).toFloat(),
                    min(height, y + barStep).toFloat(),
                    paint
                )
            }
        }

        // Middle band (23%..77% height): 6x2 high-contrast Macbeth color + grayscale grid
        val patchColors = intArrayOf(
            Color.rgb(220, 44, 52),   // Crimson Red
            Color.rgb(242, 138, 32),  // Vivid Orange
            Color.rgb(244, 212, 48),  // Chrome Yellow
            Color.rgb(42, 178, 86),   // Foliage Green
            Color.rgb(34, 168, 214),  // Cyan Sky
            Color.rgb(54, 78, 206),   // Cobalt Blue
            Color.rgb(168, 56, 186),  // Magenta
            Color.rgb(248, 248, 248), // Pure White
            Color.rgb(184, 184, 184), // Light Gray
            Color.rgb(118, 118, 118), // Mid Gray 18%
            Color.rgb(56, 56, 56),    // Dark Gray
            Color.rgb(14, 14, 16)     // Deep Black
        )

        val marginX = width * 0.04f
        val midTop = topBandH + height * 0.03f
        val midBot = botBandY - height * 0.03f
        val cols = 6
        val rows = 2
        val gap = max(4f, width * 0.012f)
        val cellW = (width - marginX * 2f - gap * (cols - 1)) / cols
        val cellH = (midBot - midTop - gap * (rows - 1)) / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                paint.color = patchColors[r * cols + c]
                val left = marginX + c * (cellW + gap)
                val top = midTop + r * (cellH + gap)
                canvas.drawRect(left, top, left + cellW, top + cellH, paint)
            }
        }

        // Crisp center focus-plane target strip (horizontal black/white micro-ticks along y = 50%)
        val midY = height * 0.5f
        val tickH = max(5f, height * 0.025f)
        for (x in 0 until width step barStep) {
            paint.color = if ((x / barStep) % 2 == 0) Color.WHITE else Color.BLACK
            canvas.drawRect(
                x.toFloat(),
                midY - tickH,
                min(width, x + barStep).toFloat(),
                midY + tickH,
                paint
            )
        }

        return bmp
    }

    /**
     * Neutral scientific optical baseline (zero vignette, zero distortion, zero grain, unity contrast)
     * so each shader under test (Light Leak, Plastic Scratches, Tilt-Shift) is isolated cleanly.
     */
    private fun createNeutralLens(): LensModel = HardwareCatalog.lensById("lens_40_pancake").copy(
        id = "diag_neutral_lens",
        name = "Optique Neutre",
        cropZoomFactor = 1.0f,
        distortionK = 0f,
        vignetteStrength = 0f,
        chromaticAberration = 0f,
        opticalSoftness = 0f,
        halationStrength = 0f,
        flareStrength = 0f,
        tiltShiftAmount = 0f,
        plasticScratchIntensity = 0f
    )

    private fun createNeutralBody(): CameraBodyModel = HardwareCatalog.bodyById("body_pro_slr").copy(
        id = "diag_neutral_body",
        name = "Banc Neutre",
        contrast = 1.0f,
        warmth = 0f,
        saturation = 1.0f,
        shadowR = 0f, shadowG = 0f, shadowB = 0f,
        highlightR = 0f, highlightG = 0f, highlightB = 0f,
        lightLeakIntensity = 0f,
        globalSoftness = 0f,
        plasticScratchIntensity = 0f
    )

    private fun createNeutralFilm(): FilmStockModel = HardwareCatalog.filmById("film_portra_400").copy(
        id = "diag_neutral_film",
        name = "Émulsion Neutre",
        isMonochrome = false,
        redGain = 1.0f, greenGain = 1.0f, blueGain = 1.0f,
        contrast = 1.0f, saturation = 1.0f,
        toeLift = 0f, shoulderCompress = 0f,
        shadowTintR = 0f, shadowTintG = 0f, shadowTintB = 0f,
        highlightTintR = 0f, highlightTintG = 0f, highlightTintB = 0f,
        grainIntensity = 0f,
        halationBoost = 0f,
        hueShift = 0f, sepiaAmount = 0f, cyanotypeAmount = 0f, infraredAmount = 0f
    )

    /**
     * Renders an intensity sweep (0%, 25%, 50%, 75%, 100%) for a single isolated shader target
     * on the selected calibration chart, returning rendered bitmaps + quantitative telemetry.
     */
    fun renderIntensitySweep(
        chartType: DiagnosticChartType,
        target: DiagnosticShaderTarget,
        seed: Float = 0.24f,
        tileWidth: Int = 360,
        tileHeight: Int = 240,
        intensities: List<Float> = INTENSITY_STEPS
    ): List<DiagnosticRenderTile> {
        val source = when (chartType) {
            DiagnosticChartType.SOLID_GRAY -> createSolidGrayChart(tileWidth, tileHeight)
            DiagnosticChartType.COLOR_CHECKER -> createHighContrastColorChart(tileWidth, tileHeight)
        }
        val neutralLens = createNeutralLens()
        val neutralBody = createNeutralBody()
        val neutralFilm = createNeutralFilm()

        val tiles = mutableListOf<DiagnosticRenderTile>()
        var baselineBmp: Bitmap? = null
        var baselineEdgeSharp = 1.0

        intensities.forEach { level ->
            val lens = when (target) {
                DiagnosticShaderTarget.TILT_SHIFT -> neutralLens.copy(tiltShiftAmount = level)
                DiagnosticShaderTarget.PLASTIC_SCRATCHES -> neutralLens.copy(plasticScratchIntensity = level)
                DiagnosticShaderTarget.LIGHT_LEAKS -> neutralLens
            }
            val body = when (target) {
                DiagnosticShaderTarget.LIGHT_LEAKS -> neutralBody.copy(lightLeakIntensity = level)
                DiagnosticShaderTarget.PLASTIC_SCRATCHES -> neutralBody.copy(plasticScratchIntensity = level)
                DiagnosticShaderTarget.TILT_SHIFT -> neutralBody
            }
            val params = OpticalChainParams(
                lens = lens,
                body = body,
                film = neutralFilm,
                expirationDate = FilmExpirationDate.freshDefault(),
                development = DevelopmentMethod.STANDARD_C41,
                lightLeakSeed = seed,
                lightLeakMode = if (target == DiagnosticShaderTarget.LIGHT_LEAKS) LightLeakMode.AUTO else LightLeakMode.OFF
            )
            val rendered = OffscreenGlProcessor.processFrame(
                source = source,
                params = params,
                dateStampText = null,
                overlayBitmap = null,
                timeSeed = 2.4f,
                includeFrameBorder = false,
                frameNumber = 1
            )

            val edgeSharp = (measureBandSharpness(rendered, 0.03f, 0.17f) +
                measureBandSharpness(rendered, 0.83f, 0.97f)) / 2.0
            val centerSharp = measureBandSharpness(rendered, 0.44f, 0.56f)

            if (baselineBmp == null) {
                baselineBmp = rendered
                baselineEdgeSharp = max(edgeSharp, 1e-5)
            }

            val (meanDiff, peakDiff) = compareRgbDelta(baselineBmp!!, rendered)
            val edgeDrop = if (baselineEdgeSharp > 1e-4) {
                ((1.0 - edgeSharp / baselineEdgeSharp) * 100.0).toFloat()
            } else {
                0f
            }
            val ratio = if (edgeSharp > 1e-5) (centerSharp / edgeSharp).toFloat() else 1f

            tiles.add(
                DiagnosticRenderTile(
                    intensityPercent = (level * 100f).toInt(),
                    label = "${(level * 100f).toInt()} %",
                    bitmap = rendered,
                    meanDeltaPct = (meanDiff * 100.0).toFloat(),
                    peakDeltaPct = (peakDiff * 100.0).toFloat(),
                    edgeSharpnessDropPct = edgeDrop,
                    centerToEdgeRatio = ratio
                )
            )
        }
        source.recycle()
        return tiles
    }

    /**
     * Composes a labelled horizontal contact-sheet bitmap from a list of diagnostic tiles
     * for visual verification and export.
     */
    fun composeContactStrip(
        title: String,
        subtitle: String,
        tiles: List<DiagnosticRenderTile>
    ): Bitmap {
        require(tiles.isNotEmpty()) { "Tiles list must not be empty" }
        val tw = tiles.first().bitmap.width
        val th = tiles.first().bitmap.height
        val pad = 18
        val headerH = 66
        val footerH = 44
        val totalW = pad + tiles.size * (tw + pad)
        val totalH = headerH + th + footerH + pad

        val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(16, 17, 22))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 238, 226)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 22f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(212, 162, 83)
            typeface = Typeface.MONOSPACE
            textSize = 14f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(235, 232, 224)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 14f
        }
        val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(162, 168, 182)
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.rgb(66, 70, 82)
        }

        canvas.drawText(title, pad.toFloat(), 30f, titlePaint)
        canvas.drawText(subtitle, pad.toFloat(), 52f, subPaint)

        tiles.forEachIndexed { idx, tile ->
            val left = pad + idx * (tw + pad)
            val top = headerH
            canvas.drawBitmap(tile.bitmap, left.toFloat(), top.toFloat(), null)
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                (left + tw).toFloat(),
                (top + th).toFloat(),
                borderPaint
            )
            canvas.drawText(
                "INTENSITÉ ${tile.label}",
                left.toFloat(),
                (top + th + 20).toFloat(),
                labelPaint
            )
            val metricStr = "Δmoy %.1f%% | Δpic %.1f%% | Flou bords %.0f%%".format(
                tile.meanDeltaPct,
                tile.peakDeltaPct,
                tile.edgeSharpnessDropPct
            )
            canvas.drawText(
                metricStr,
                left.toFloat(),
                (top + th + 37).toFloat(),
                metricPaint
            )
        }
        return out
    }

    private fun measureBandSharpness(bmp: Bitmap, y0Frac: Float, y1Frac: Float): Double {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val y0 = (h * y0Frac).toInt().coerceIn(1, h - 2)
        val y1 = (h * y1Frac).toInt().coerceIn(y0 + 1, h - 1)
        var sum = 0.0
        var count = 0
        for (y in y0 until y1) {
            for (x in 1 until (w - 1)) {
                val l = luma(px[y * w + x - 1])
                val r = luma(px[y * w + x + 1])
                val u = luma(px[(y - 1) * w + x])
                val d = luma(px[(y + 1) * w + x])
                sum += abs(r - l) + abs(d - u)
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun luma(argb: Int): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    private fun compareRgbDelta(base: Bitmap, test: Bitmap): Pair<Double, Double> {
        val w = min(base.width, test.width)
        val h = min(base.height, test.height)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        base.getPixels(a, 0, w, 0, 0, w, h)
        test.getPixels(b, 0, w, 0, 0, w, h)
        var sum = 0.0
        var peak = 0.0
        for (i in a.indices) {
            val dr = abs(((b[i] ushr 16) and 0xFF) - ((a[i] ushr 16) and 0xFF)) / 255.0
            val dg = abs(((b[i] ushr 8) and 0xFF) - ((a[i] ushr 8) and 0xFF)) / 255.0
            val db = abs((b[i] and 0xFF) - (a[i] and 0xFF)) / 255.0
            val d = (dr + dg + db) / 3.0
            val m = max(dr, max(dg, db))
            sum += d
            if (m > peak) peak = m
        }
        return (sum / a.size) to peak
    }
}
