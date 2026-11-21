package com.example

import android.graphics.Bitmap
import com.example.engine.DiagnosticChartType
import com.example.engine.DiagnosticShaderTarget
import com.example.engine.OpticalDiagnosticRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Generates visual PNG contact strips for Solid 18% Gray and High-Contrast Color/Checker charts
 * across intensity sweeps (0%, 25%, 50%, 75%, 100%) for Light Leaks, Plastic Scratches, and Tilt-Shift
 * so every shader can be visually inspected AND quantitatively verified.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpticalDiagnosticRenderTest {

    private fun savePng(bmp: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    @Test
    fun `render visual diagnostic contact sheets on solid gray and high contrast color charts`() {
        val outDir = File("build/diagnostic_renders").apply { mkdirs() }

        val configs = listOf(
            Triple(DiagnosticChartType.SOLID_GRAY, DiagnosticShaderTarget.LIGHT_LEAKS, "diag_light_leaks_gray.png"),
            Triple(DiagnosticChartType.COLOR_CHECKER, DiagnosticShaderTarget.LIGHT_LEAKS, "diag_light_leaks_color.png"),
            Triple(DiagnosticChartType.SOLID_GRAY, DiagnosticShaderTarget.PLASTIC_SCRATCHES, "diag_scratches_gray.png"),
            Triple(DiagnosticChartType.COLOR_CHECKER, DiagnosticShaderTarget.PLASTIC_SCRATCHES, "diag_scratches_color.png"),
            Triple(DiagnosticChartType.COLOR_CHECKER, DiagnosticShaderTarget.TILT_SHIFT, "diag_tilt_shift_color.png"),
            Triple(DiagnosticChartType.SOLID_GRAY, DiagnosticShaderTarget.TILT_SHIFT, "diag_tilt_shift_gray.png")
        )

        for ((chart, target, fileName) in configs) {
            val tiles = OpticalDiagnosticRenderer.renderIntensitySweep(
                chartType = chart,
                target = target,
                seed = 0.064f, // Archetype #0 for crisp edge burn + rich diagonal acrylic gouges
                tileWidth = 300,
                tileHeight = 200
            )
            val strip = OpticalDiagnosticRenderer.composeContactStrip(
                title = "DIAGNOSTIC OPTIQUE • ${target.labelFr.uppercase()} • ${chart.labelFr.uppercase()}",
                subtitle = "Balayage intensité 0 % → 25 % → 50 % → 75 % → 100 % | graine=0.064 | optique neutre isolée",
                tiles = tiles
            )
            val outFile = File(outDir, fileName)
            savePng(strip, outFile)

            val summary = tiles.joinToString(" | ") { t ->
                "${t.intensityPercent}%:Δmoy=${"%.1f".format(t.meanDeltaPct)}%,Δpic=${"%.1f".format(t.peakDeltaPct)}%,flou=${"%.0f".format(t.edgeSharpnessDropPct)}%"
            }
            println("DIAG [$fileName] -> $summary")

            // Monotonic visibility assertions on non-trivial targets
            val t25 = tiles[1]
            val t50 = tiles[2]
            val t100 = tiles[4]
            when (target) {
                DiagnosticShaderTarget.LIGHT_LEAKS -> {
                    assertTrue("$fileName: 25% leak must be visible", t25.peakDeltaPct >= 10f)
                    assertTrue("$fileName: 100% leak must exceed 50%", t100.meanDeltaPct > t50.meanDeltaPct)
                }
                DiagnosticShaderTarget.PLASTIC_SCRATCHES -> {
                    assertTrue("$fileName: 50% scratches must be visible", t50.peakDeltaPct >= 8f)
                    assertTrue("$fileName: 100% scratches must exceed 50%", t100.peakDeltaPct > t50.peakDeltaPct)
                }
                DiagnosticShaderTarget.TILT_SHIFT -> {
                    if (chart == DiagnosticChartType.COLOR_CHECKER) {
                        assertTrue("$fileName: 50% tilt-shift must visibly blur edges", t50.edgeSharpnessDropPct >= 28f)
                        assertTrue("$fileName: 100% tilt-shift must exceed 25% blur", t100.edgeSharpnessDropPct > t25.edgeSharpnessDropPct)
                    }
                }
            }

            strip.recycle()
            tiles.forEach { it.bitmap.recycle() }
        }
    }
}
