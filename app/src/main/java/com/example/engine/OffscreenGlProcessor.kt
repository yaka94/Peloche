package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.domain.model.OpticalChainParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Full-resolution background renderer executing the EXACT same GLSL optical chain
 * via offscreen EGL PBuffer (OpenGL ES 3.0) with automatic CPU fallback for any headless GPU,
 * plus retro quartz LED date stamp burning and Half-Frame diptych stitching.
 */
object OffscreenGlProcessor {

    private const val MAX_RENDER_EDGE = 1800

    /**
     * Crops source bitmap to the body's target aspect ratio, runs the full optical + film +
     * expiration + lab development shader, and optionally burns the retro orange date stamp.
     */
    fun processFrame(
        source: Bitmap,
        params: OpticalChainParams,
        dateStampText: String?,
        overlayBitmap: Bitmap? = null,
        timeSeed: Float = Random.nextFloat() * 100f
    ): Bitmap {
        val cropped = cropToBodyAspect(source, params.body.aspectRatioSpec.aspectRatio, MAX_RENDER_EDGE)
        val rendered = runOffscreenGl(cropped, params, overlayBitmap, timeSeed) ?: runCpuFallback(cropped, params)
        if (cropped !== source && cropped !== rendered) {
            cropped.recycle()
        }
        if (!dateStampText.isNullOrBlank()) {
            burnRetroDateStamp(rendered, dateStampText)
        }
        return rendered
    }

    /**
     * Stitches two Half-Frame (3:4) frames side-by-side onto a classic 3:2 frame with a dark
     * analog film frame divider bar ("Diptyque Demi-Format").
     */
    fun stitchHalfFrameDiptych(left: Bitmap, right: Bitmap): Bitmap {
        val frameH = max(left.height, right.height)
        val frameW = (frameH * 0.75f).roundToInt()
        val gap = (frameW * 0.045f).roundToInt().coerceAtLeast(10)
        val outW = frameW * 2 + gap
        val result = Bitmap.createBitmap(outW, frameH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(12, 11, 10))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val leftScaled = Bitmap.createScaledBitmap(left, frameW, frameH, true)
        val rightScaled = Bitmap.createScaledBitmap(right, frameW, frameH, true)
        canvas.drawBitmap(leftScaled, 0f, 0f, paint)
        canvas.drawBitmap(rightScaled, (frameW + gap).toFloat(), 0f, paint)
        if (leftScaled !== left) leftScaled.recycle()
        if (rightScaled !== right) rightScaled.recycle()
        return result
    }

    private fun cropToBodyAspect(src: Bitmap, targetAspect: Float, maxEdge: Int): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val srcAspect = srcW.toFloat() / srcH.toFloat()

        val cropW: Int
        val cropH: Int
        if (srcAspect > targetAspect) {
            cropH = srcH
            cropW = (srcH * targetAspect).roundToInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetAspect).roundToInt().coerceIn(1, srcH)
        }
        val x = ((srcW - cropW) / 2).coerceAtLeast(0)
        val y = ((srcH - cropH) / 2).coerceAtLeast(0)

        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        val longest = max(cropped.width, cropped.height)
        if (longest <= maxEdge) return cropped

        val scale = maxEdge.toFloat() / longest.toFloat()
        val outW = (cropped.width * scale).roundToInt().coerceAtLeast(1)
        val outH = (cropped.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun runOffscreenGl(
        input: Bitmap,
        params: OpticalChainParams,
        overlayBitmap: Bitmap?,
        timeSeed: Float
    ): Bitmap? {
        val width = input.width
        val height = input.height
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var program = 0
        val texIds = IntArray(2)

        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                return null
            }
            val config = configs[0] ?: return null

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return null

            val surfAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            surface = EGL14.eglCreatePbufferSurface(display, config, surfAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return null

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null

            program = OpticalShaderSource.compileProgram(
                OpticalShaderSource.VERTEX_SHADER,
                OpticalShaderSource.FRAGMENT_SHADER_2D
            )

            GLES20.glGenTextures(2, texIds, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, input, 0)

            if (overlayBitmap != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[1])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)
            }

            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)

            OpticalShaderSource.bindOpticalUniforms(
                program = program,
                params = params,
                surfaceWidth = width,
                surfaceHeight = height,
                cropScaleX = 1f,
                cropScaleY = 1f,
                timeSeed = timeSeed
            )

            val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, identity, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[1])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uOverlayTex"), 1)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHasOverlay"), if (overlayBitmap != null) 1.0f else 0.0f)

            val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            val vBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(verts); position(0)
            }
            val tBuf = ByteBuffer.allocateDirect(uvs.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(uvs); position(0)
            }

            val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
            val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vBuf)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tBuf)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()

            val pixelBuf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf)
            pixelBuf.rewind()

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output.copyPixelsFromBuffer(pixelBuf)
            output
        } catch (_: Throwable) {
            null
        } finally {
            if (texIds[0] != 0) GLES20.glDeleteTextures(2, texIds, 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
            }
        }
    }

    /**
     * Exact mathematical CPU fallback so full-res lab development still produces authentic
     * film + expiration + dev results even on software environments without EGL PBuffer.
     */
    private fun runCpuFallback(input: Bitmap, params: OpticalChainParams): Bitmap {
        val w = input.width
        val h = input.height
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)

        val lens = params.lens
        val body = params.body
        val film = params.film
        val exp = params.expirationEffect
        val dev = params.development
        val totalEvGain = 2.0.pow((params.evCompensation + exp.exposureShiftEv + dev.exposureDelta).toDouble()).toFloat()
        val contrast = body.contrast * film.contrast * exp.contrastFactor * dev.contrastMul
        val sat = body.saturation * film.saturation * exp.saturationFactor * dev.saturationMul
        val grainStrength = (film.grainIntensity * exp.grainMultiplier * dev.grainMul).coerceAtMost(0.9f) * 0.25f
        val rng = Random((params.lightLeakSeed * 10000).toInt())

        for (y in 0 until h) {
            val ny = (y.toFloat() / h) - 0.5f
            for (x in 0 until w) {
                val nx = (x.toFloat() / w) - 0.5f
                val r2 = nx * nx + ny * ny
                val vig = (1f - lens.vignetteStrength * (r2 * 1.6f)).coerceIn(0.22f, 1f)

                val px = pixels[y * w + x]
                var r = ((px shr 16) and 0xFF) / 255f * vig * totalEvGain
                var g = ((px shr 8) and 0xFF) / 255f * vig * totalEvGain
                var b = (px and 0xFF) / 255f * vig * totalEvGain

                r *= (1f + body.warmth * 0.5f) * film.redGain
                g *= film.greenGain
                b *= (1f - body.warmth * 0.5f) * film.blueGain

                if (film.isMonochrome) {
                    val bw = r * 0.32f + g * 0.56f + b * 0.12f
                    r = bw; g = bw; b = bw
                }

                r = (r - 0.5f) * contrast + 0.5f + film.toeLift + exp.fogLift + exp.castR
                g = (g - 0.5f) * contrast + 0.5f + film.toeLift + exp.fogLift + exp.castG
                b = (b - 0.5f) * contrast + 0.5f + film.toeLift + exp.fogLift + exp.castB

                val luma = r * 0.299f + g * 0.587f + b * 0.114f
                r = luma + (r - luma) * sat
                g = luma + (g - luma) * sat
                b = luma + (b - luma) * sat

                if (dev.crossProcessStrength > 0.1f) {
                    g += 0.05f
                    b += (0.5f - luma) * 0.12f
                }
                val sepia = max(film.sepiaAmount, dev.sepiaBathStrength)
                if (sepia > 0.05f) {
                    val l = r * 0.299f + g * 0.587f + b * 0.114f
                    r = r * (1f - sepia) + (l * 1.12f + 0.04f) * sepia
                    g = g * (1f - sepia) + (l * 0.92f + 0.01f) * sepia
                    b = b * (1f - sepia) + (l * 0.66f) * sepia
                }
                val cyano = max(film.cyanotypeAmount, dev.cyanotypeBathStrength)
                if (cyano > 0.05f) {
                    val l = (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f)
                    r = r * (1f - cyano) + (0.05f + l * 0.83f) * cyano
                    g = g * (1f - cyano) + (0.16f + l * 0.77f) * cyano
                    b = b * (1f - cyano) + (0.34f + l * 0.61f) * cyano
                }

                val n = (rng.nextFloat() - 0.5f) * grainStrength
                val ir = ((r + n).coerceIn(0f, 1f) * 255f).roundToInt()
                val ig = ((g + n).coerceIn(0f, 1f) * 255f).roundToInt()
                val ib = ((b + n).coerceIn(0f, 1f) * 255f).roundToInt()
                pixels[y * w + x] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Burns the selected authentic analog date-back imprint style onto the developed frame.
     */
    private fun burnRetroDateStamp(bitmap: Bitmap, rawDateStamp: String) {
        val (style, dateText) = com.example.domain.model.DateStampStyle.decodeStamp(rawDateStamp) ?: return
        val canvas = Canvas(bitmap)
        val minDim = min(bitmap.width, bitmap.height).toFloat()

        val scaleMul = when (style) {
            com.example.domain.model.DateStampStyle.TIMECODE_CINE -> 0.028f
            com.example.domain.model.DateStampStyle.CLASSIC_WHITE -> 0.027f
            else -> 0.036f
        }
        val textSize = (minDim * scaleMul).coerceIn(16f, 62f)
        val tracking = when (style) {
            com.example.domain.model.DateStampStyle.CLASSIC_WHITE -> 0.09f
            com.example.domain.model.DateStampStyle.TIMECODE_CINE -> 0.11f
            else -> 0.16f
        }
        val tf = when (style) {
            com.example.domain.model.DateStampStyle.CLASSIC_WHITE -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            else -> Typeface.MONOSPACE
        }

        val coreColor = style.coreColorHex.toInt()
        val glowColor = style.glowColorHex.toInt()

        val marginX = bitmap.width * 0.052f
        val marginY = bitmap.height * 0.062f
        val x = bitmap.width - marginX
        val y = bitmap.height - marginY

        if (style != com.example.domain.model.DateStampStyle.CLASSIC_WHITE) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glowColor
                this.textSize = textSize
                typeface = tf
                letterSpacing = tracking
                textAlign = Paint.Align.RIGHT
                setShadowLayer(textSize * 0.38f, 0f, 0f, glowColor)
            }
            canvas.drawText(dateText, x, y, glowPaint)
        } else {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 0, 0, 0)
                this.textSize = textSize
                typeface = tf
                letterSpacing = tracking
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(dateText, x + 2f, y + 2f, shadowPaint)
        }

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coreColor
            this.textSize = textSize
            typeface = tf
            letterSpacing = tracking
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateText, x, y, corePaint)
    }
}
