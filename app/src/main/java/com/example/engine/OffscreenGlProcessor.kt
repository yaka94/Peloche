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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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
        timeSeed: Float = Random.nextFloat() * 100f,
        includeFrameBorder: Boolean = true,
        frameNumber: Int = 1
    ): Bitmap {
        val stillParams = params.copy(invertedGroundGlass = false, videoFilterMode = 0)
        val cropped = cropToBodyAspect(source, stillParams.body.aspectRatioSpec.aspectRatio, MAX_RENDER_EDGE)
        val rendered = runOffscreenGl(cropped, stillParams, overlayBitmap, timeSeed)
            ?: runCpuFallback(cropped, stillParams, timeSeed)
        if (cropped !== source && cropped !== rendered) {
            cropped.recycle()
        }
        if (!dateStampText.isNullOrBlank()) {
            burnRetroDateStamp(rendered, dateStampText)
        }
        if (includeFrameBorder) {
            val framed = applyCameraFrameBorder(rendered, params.body, params.film, frameNumber)
            if (framed !== rendered) {
                rendered.recycle()
            }
            return framed
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
        var display: EGLDisplay? = null
        var context: EGLContext? = null
        var surface: EGLSurface? = null
        var program = 0
        val texIds = IntArray(2)

        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) return null
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
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return null

            val surfAttribs = intArrayOf(
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
            )
            surface = EGL14.eglCreatePbufferSurface(display, config, surfAttribs, 0)
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null

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

            // Our fragment shader unconditionally writes alpha = 1.0 (0xFF) for every pixel.
            // If readback returns transparent zeros (e.g. headless/stub EGL context), fall back to CPU.
            val centerOffset = ((height / 2) * width + (width / 2)) * 4
            val centerAlpha = if (centerOffset + 3 < pixelBuf.capacity()) {
                pixelBuf.get(centerOffset + 3).toInt() and 0xFF
            } else {
                0
            }
            if (centerAlpha < 128) return null

            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            output.copyPixelsFromBuffer(pixelBuf)
            output
        } catch (_: Throwable) {
            null
        } finally {
            runCatching {
                if (texIds[0] != 0) GLES20.glDeleteTextures(2, texIds, 0)
                if (program != 0) GLES20.glDeleteProgram(program)
                val d = display
                if (d != null && d != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    val s = surface
                    if (s != null && s != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(d, s)
                    val c = context
                    if (c != null && c != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(d, c)
                    EGL14.eglReleaseThread()
                }
            }
        }
    }

    /**
     * Full-parity mathematical CPU implementation of the 3-stage optical chain matching OpticalShaderSource:
     * guarantees identical Tilt-Shift 16-tap bokeh, 8-archetype procedural Light Leaks, Halation, Flares,
     * Plastic Scratches, Distortion, Chromatic Aberration, Film Emulsion, Expiration & Lab Dev even on
     * software environments without EGL PBuffer.
     */
    internal fun runCpuFallback(
        input: Bitmap,
        params: OpticalChainParams,
        timeSeed: Float = 1.5f
    ): Bitmap {
        val w = input.width.coerceAtLeast(1)
        val h = input.height.coerceAtLeast(1)
        val srcPixels = IntArray(w * h)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        val lens = params.lens
        val body = params.body
        val film = params.film
        val exp = params.expirationEffect
        val dev = params.development

        val invW = 1f / w.toFloat()
        val invH = 1f / h.toFloat()
        val maxTexel = max(invW, invH)
        val uvNormX = invW / maxTexel
        val uvNormY = invH / maxTexel
        val zoom = max(1.0f, lens.cropZoomFactor * params.zoomMultiplier)
        val blurScaleX = uvNormX / zoom
        val blurScaleY = uvNormY / zoom

        val optSoft = (lens.opticalSoftness + body.globalSoftness).coerceAtMost(0.9f)
        val halation = (lens.halationStrength + film.halationBoost).coerceAtMost(1.25f)
        val flareStr = lens.flareStrength
        val tiltShift = lens.tiltShiftAmount
        val plasticScratches = max(body.plasticScratchIntensity, lens.plasticScratchIntensity).coerceIn(0f, 1f)
        val leakIntensity = params.effectiveLightLeakIntensity
        val seed = params.lightLeakSeed
        val totalEvGain = 2.0.pow((params.evCompensation + exp.exposureShiftEv + dev.exposureDelta).toDouble()).toFloat()
        val grainStrength = (film.grainIntensity * exp.grainMultiplier * dev.grainMul).coerceAtMost(0.95f)
        val sepia = max(film.sepiaAmount, dev.sepiaBathStrength).coerceIn(0f, 1f)
        val cyano = max(film.cyanotypeAmount, dev.cyanotypeBathStrength).coerceIn(0f, 1f)

        val tSlow = timeSeed
        val s1 = fract(seed * 13.73f + 0.17f)
        val s2 = fract(seed * 29.31f + 0.61f)
        val s3 = fract(seed * 53.19f + 0.37f)
        val archetype = floor(s1 * 8f)
        val expLeakFallback = if (exp.fogLift > 0.025f) min(exp.fogLift * 4.6f, 0.62f) else 0f
        val effectiveLeak = max(leakIntensity, expLeakFallback)
        val baseShotStrength = effectiveLeak * mix(0.84f, 1.24f, s2)
        val sweepDrift = sin(tSlow * 0.52f + s3 * 6.283f) * 0.048f

        val pS1 = fract(seed * 17.31f + 0.29f)
        val pS2 = fract(seed * 37.93f + 0.53f)
        val pS3 = fract(seed * 71.17f + 0.11f)
        val flareMode = floor(fract(seed * 9.17f + 0.23f) * 4f)
        val axisAngle = fract(seed * 5.71f) * 6.28318f + sin(tSlow * 0.38f + seed * 6.283f) * 0.17f
        val lightOriginX = 0.5f + cos(axisAngle) * 0.44f
        val lightOriginY = 0.5f + sin(axisAngle) * 0.42f
        val flareBreath = 0.86f + 0.16f * sin(tSlow * 0.74f + seed * 11.3f)

        fun sampleBilinear(u: Float, v: Float, out: FloatArray) {
            val cu = u.coerceIn(0.002f, 0.998f) * (w - 1)
            val cv = v.coerceIn(0.002f, 0.998f) * (h - 1)
            val x0 = cu.toInt().coerceIn(0, w - 1)
            val y0 = cv.toInt().coerceIn(0, h - 1)
            val x1 = (x0 + 1).coerceAtMost(w - 1)
            val y1 = (y0 + 1).coerceAtMost(h - 1)
            val fx = cu - x0
            val fy = cv - y0
            val c00 = srcPixels[y0 * w + x0]
            val c10 = srcPixels[y0 * w + x1]
            val c01 = srcPixels[y1 * w + x0]
            val c11 = srcPixels[y1 * w + x1]
            val w00 = (1f - fx) * (1f - fy)
            val w10 = fx * (1f - fy)
            val w01 = (1f - fx) * fy
            val w11 = fx * fy
            val inv255 = 1f / 255f
            out[0] = (((c00 ushr 16) and 0xFF) * w00 + ((c10 ushr 16) and 0xFF) * w10 +
                ((c01 ushr 16) and 0xFF) * w01 + ((c11 ushr 16) and 0xFF) * w11) * inv255
            out[1] = (((c00 ushr 8) and 0xFF) * w00 + ((c10 ushr 8) and 0xFF) * w10 +
                ((c01 ushr 8) and 0xFF) * w01 + ((c11 ushr 8) and 0xFF) * w11) * inv255
            out[2] = ((c00 and 0xFF) * w00 + (c10 and 0xFF) * w10 +
                (c01 and 0xFF) * w01 + (c11 and 0xFF) * w11) * inv255
        }

        val innerDx = floatArrayOf(1f, -1f, 0f, 0f, 0.707f, -0.707f, 0.707f, -0.707f)
        val innerDy = floatArrayOf(0f, 0f, 1f, -1f, 0.707f, 0.707f, -0.707f, -0.707f)
        val outerDx = floatArrayOf(0.924f, -0.924f, 0.924f, -0.924f, 0.383f, -0.383f, 0.383f, -0.383f)
        val outerDy = floatArrayOf(0.383f, 0.383f, -0.383f, -0.383f, 0.924f, 0.924f, -0.924f, -0.924f)
        val tmp = FloatArray(3)
        val tmpR = FloatArray(3)
        val tmpB = FloatArray(3)
        val rng = Random((seed * 10000).toInt())

        for (y in 0 until h) {
            val screenV = (y + 0.5f) * invH
            val fyC = screenV - 0.5f

            for (x in 0 until w) {
                val screenU = (x + 0.5f) * invW
                val fxC = screenU - 0.5f
                val tiltCoord = if (params.tiltShiftVertical) screenU else screenV
                val tiltBand = abs(tiltCoord - params.tiltShiftPosition) * 2f
                val tiltBlur = tiltShift * smoothstep(0.14f, 0.74f, tiltBand)
                val bokehR = optSoft * 0.0052f + tiltBlur * 0.028f
                val r2 = fxC * fxC + fyC * fyC
                val distFactor = 1f + lens.distortionK * r2
                val baseU = (fxC * distFactor) / zoom + 0.5f
                val baseV = (fyC * distFactor) / zoom + 0.5f

                if (baseU < 0f || baseU > 1f || baseV < 0f || baseV > 1f) {
                    outPixels[y * w + x] = Color.rgb(8, 8, 8)
                    continue
                }

                // Chromatic aberration
                val radialDist = sqrt(r2)
                val caX = (fxC / zoom) * lens.chromaticAberration * radialDist
                val caY = (fyC / zoom) * lens.chromaticAberration * radialDist
                sampleBilinear(baseU, baseV, tmp)
                var r: Float
                var g = tmp[1]
                var b: Float
                if (lens.chromaticAberration > 0.0005f) {
                    sampleBilinear(baseU + caX, baseV + caY, tmpR)
                    sampleBilinear(baseU - caX, baseV - caY, tmpB)
                    r = tmpR[0]
                    b = tmpB[2]
                } else {
                    r = tmp[0]
                    b = tmp[2]
                }

                // 16-tap Dual-Ring Bokeh (Tilt-Shift + Optical Softness)
                if (bokehR > 0.0005f) {
                    val rInX = blurScaleX * (bokehR * 0.46f)
                    val rInY = blurScaleY * (bokehR * 0.46f)
                    val rOutX = blurScaleX * bokehR
                    val rOutY = blurScaleY * bokehR
                    val hlBoost = 0.75f * tiltBlur
                    var accR = r * 0.50f
                    var accG = g * 0.50f
                    var accB = b * 0.50f
                    var wSum = 0.50f
                    for (i in 0 until 8) {
                        sampleBilinear(baseU + rInX * innerDx[i], baseV + rInY * innerDy[i], tmp)
                        val wi = 1f + hlBoost * smoothstep(0.62f, 1f, max(tmp[0], max(tmp[1], tmp[2])))
                        accR += tmp[0] * wi; accG += tmp[1] * wi; accB += tmp[2] * wi; wSum += wi

                        sampleBilinear(baseU + rOutX * outerDx[i], baseV + rOutY * outerDy[i], tmp)
                        val wo = 1f + hlBoost * smoothstep(0.62f, 1f, max(tmp[0], max(tmp[1], tmp[2])))
                        accR += tmp[0] * wo; accG += tmp[1] * wo; accB += tmp[2] * wo; wSum += wo
                    }
                    val invWsum = 1f / max(wSum, 1f)
                    val tsMix = smoothstep(0.02f, 0.28f, tiltBlur)
                    val softMix = (optSoft * 0.72f).coerceIn(0f, 0.78f)
                    val m = max(tsMix, softMix)
                    r = mix(r, accR * invWsum, m)
                    g = mix(g, accG * invWsum, m)
                    b = mix(b, accB * invWsum, m)
                }

                // Stage 1a: Petzval / Helios Tangential Swirly Bokeh
                if (lens.swirlyBokehAmount > 0.04f) {
                    val swMask = smoothstep(0.16f, 0.64f, radialDist) * lens.swirlyBokehAmount
                    if (swMask > 0.01f) {
                        val invRd = 1f / max(radialDist, 0.001f)
                        val tanX = -fyC * invRd
                        val tanY = fxC * invRd
                        val tStepX = tanX * blurScaleX * (0.024f * swMask)
                        val tStepY = tanY * blurScaleY * (0.024f * swMask)
                        var swR = r * 0.75f; var swG = g * 0.75f; var swB = b * 0.75f; var swW = 0.75f
                        val taps = floatArrayOf(0.45f, -0.45f, 0.92f, -0.92f, 1.42f, -1.42f)
                        for (k in taps) {
                            sampleBilinear(baseU + tStepX * k, baseV + tStepY * k, tmp)
                            val wk = 1f + swMask * smoothstep(0.62f, 1f, max(tmp[0], max(tmp[1], tmp[2])))
                            swR += tmp[0] * wk; swG += tmp[1] * wk; swB += tmp[2] * wk; swW += wk
                        }
                        val swMix = (swMask * 0.94f).coerceIn(0f, 0.92f)
                        r = mix(r, swR / max(swW, 1f), swMix)
                        g = mix(g, swG / max(swW, 1f), swMix)
                        b = mix(b, swB / max(swW, 1f), swMix)
                    }
                }

                // Dedicated tight unsharp for Rodinal acutance & Tilt-Shift crisp center plane
                if (dev.acutanceBoost > 0.02f || tiltShift > 0.05f) {
                    val sx = blurScaleX * 0.0020f
                    val sy = blurScaleY * 0.0020f
                    var sR = 0f; var sG = 0f; var sB = 0f
                    sampleBilinear(baseU + sx, baseV, tmp); sR += tmp[0]; sG += tmp[1]; sB += tmp[2]
                    sampleBilinear(baseU - sx, baseV, tmp); sR += tmp[0]; sG += tmp[1]; sB += tmp[2]
                    sampleBilinear(baseU, baseV + sy, tmp); sR += tmp[0]; sG += tmp[1]; sB += tmp[2]
                    sampleBilinear(baseU, baseV - sy, tmp); sR += tmp[0]; sG += tmp[1]; sB += tmp[2]
                    sR *= 0.25f; sG *= 0.25f; sB *= 0.25f
                    if (dev.acutanceBoost > 0.02f) {
                        val k = dev.acutanceBoost * 0.68f
                        r += (r - sR) * k; g += (g - sG) * k; b += (b - sB) * k
                    }
                    if (tiltShift > 0.05f) {
                        val focusPlane = 1f - smoothstep(0.08f, 0.42f, tiltBand)
                        val k = tiltShift * focusPlane * 0.30f
                        r += (r - sR) * k; g += (g - sG) * k; b += (b - sB) * k
                        val lTs = r * 0.299f + g * 0.587f + b * 0.114f
                        val satBoost = 1f + 0.16f * tiltShift
                        r = mix(lTs, r, satBoost); g = mix(lTs, g, satBoost); b = mix(lTs, b, satBoost)
                    }
                }

                // Specular Halation
                if (halation > 0.02f) {
                    val rInX = blurScaleX * 0.0085f
                    val rInY = blurScaleY * 0.0085f
                    val rOutX = blurScaleX * 0.022f
                    val rOutY = blurScaleY * 0.022f
                    var iR = 0f; var iG = 0f; var iB = 0f
                    sampleBilinear(baseU + rInX, baseV + rInY, tmp); iR += tmp[0]; iG += tmp[1]; iB += tmp[2]
                    sampleBilinear(baseU - rInX, baseV + rInY, tmp); iR += tmp[0]; iG += tmp[1]; iB += tmp[2]
                    sampleBilinear(baseU + rInX, baseV - rInY, tmp); iR += tmp[0]; iG += tmp[1]; iB += tmp[2]
                    sampleBilinear(baseU - rInX, baseV - rInY, tmp); iR += tmp[0]; iG += tmp[1]; iB += tmp[2]
                    iR *= 0.25f; iG *= 0.25f; iB *= 0.25f
                    var oR = 0f; var oG = 0f; var oB = 0f
                    sampleBilinear(baseU + rOutX, baseV, tmp); oR += tmp[0]; oG += tmp[1]; oB += tmp[2]
                    sampleBilinear(baseU - rOutX, baseV, tmp); oR += tmp[0]; oG += tmp[1]; oB += tmp[2]
                    sampleBilinear(baseU, baseV + rOutY, tmp); oR += tmp[0]; oG += tmp[1]; oB += tmp[2]
                    sampleBilinear(baseU, baseV - rOutY, tmp); oR += tmp[0]; oG += tmp[1]; oB += tmp[2]
                    oR *= 0.25f; oG *= 0.25f; oB *= 0.25f
                    val ib = smoothstep(0.58f, 0.96f, max(iR, max(iG, iB)))
                    val ob = smoothstep(0.54f, 0.96f, max(oR, max(oG, oB)))
                    val hR = (iR * ib * 0.34f + oR * ob * 0.26f + (ib * 0.32f + ob * 0.26f)) * halation
                    val hG = (iG * ib * 0.34f + oG * ob * 0.26f + 0.38f * (ib * 0.32f + ob * 0.26f)) * halation
                    val hB = (iB * ib * 0.34f + oB * ob * 0.26f + 0.14f * (ib * 0.32f + ob * 0.26f)) * halation
                    r = screenBlend1(r, hR); g = screenBlend1(g, hG); b = screenBlend1(b, hB)
                }

                // Procedural Lens Flares
                if (flareStr > 0.02f) {
                    sampleBilinear(1f - baseU, 1f - baseV, tmp)
                    val oppBright = smoothstep(0.64f, 0.98f, max(tmp[0], max(tmp[1], tmp[2])))
                    val isWarm = flareMode >= 1.5f
                    r += (if (isWarm) 1f else 0.40f) * oppBright * flareStr * 0.42f * flareBreath
                    g += (if (isWarm) 0.62f else 0.78f) * oppBright * flareStr * 0.42f * flareBreath
                    b += (if (isWarm) 0.32f else 1f) * oppBright * flareStr * 0.42f * flareBreath

                    val dx = screenU - lightOriginX
                    val dy = screenV - lightOriginY
                    val dist = hypot(dx, dy)
                    val fR: Float; val fG: Float; val fB: Float
                    if (flareMode < 1.5f) {
                        val yPos = 0.20f + fract(seed * 11.3f) * 0.60f
                        val streak = exp(-abs(screenV - yPos) * 22f)
                        val xCore = fract(seed * 4.3f)
                        val core = exp(-hypot((screenU - xCore) * 1.5f, (screenV - yPos) * 4.8f) * 3.4f)
                        fR = 0.26f * streak * 0.48f + 1.0f * core * 0.58f
                        fG = 0.68f * streak * 0.48f + 0.84f * core * 0.58f
                        fB = 1.0f * streak * 0.48f + 0.56f * core * 0.58f
                    } else {
                        val veil = exp(-dist * dist * 2.4f) * 0.56f
                        val arcR = exp(-sq((dist - 0.55f) * 16f)) * 0.50f
                        val arcG = exp(-sq((dist - 0.58f) * 16f)) * 0.40f
                        val arcB = exp(-sq((dist - 0.61f) * 16f)) * 0.54f
                        fR = veil + arcR; fG = veil * 0.72f + arcG; fB = veil * 0.38f + arcB
                    }
                    val fScale = flareStr * 1.02f * flareBreath
                    r = screenBlend1(r, fR * fScale); g = screenBlend1(g, fG * fScale); b = screenBlend1(b, fB * fScale)
                }

                // Plastic Lens Scratches, Wiping Swirls, Pocket Scuff & Film-Gate Rail (full GLSL parity)
                if (plasticScratches > 0.02f) {
                    val lumaP = r * 0.299f + g * 0.587f + b * 0.114f
                    val lightCatch = 0.42f + 0.58f * smoothstep(0.14f, 0.82f, lumaP)
                    val sdx = screenU - (0.5f + (pS1 - 0.5f) * 0.22f)
                    val sdy = screenV - (0.5f + (pS2 - 0.5f) * 0.22f)
                    val sr = hypot(sdx, sdy)
                    val sAng = atan2(sdy, sdx + 1e-5f)
                    val swirlMask = smoothstep(0.14f, 0.42f, sr) * smoothstep(0.78f, 0.44f, sr)
                    val arcWin = smoothstep(0.20f, 0.82f, sin(sAng * 2f + pS3 * 6.283f))
                    val fineSwirls = max(0.5f + 0.5f * sin(sr * 135f + sAng + pS2 * 12f), 0f)
                        .toDouble().pow(12.0).toFloat() * swirlMask * arcWin
                    val swirl = (exp(-sq((sr - (0.29f + pS1 * 0.09f)) * 90f)) * arcWin * 0.62f +
                        exp(-sq((sr - (0.41f + pS2 * 0.08f)) * 78f)) * smoothstep(0.25f, 0.85f, cos(sAng * 3f - pS1 * 6.283f)) * 0.52f +
                        fineSwirls * 0.48f)

                    // Scratch 1 (primary curved diagonal gouge)
                    val a1 = pS1 * PI.toFloat() + 0.42f
                    val ox1 = screenU - (0.18f + pS1 * 0.64f)
                    val oy1 = screenV - (0.16f + pS2 * 0.68f)
                    val sc1x = cos(a1) * ox1 - sin(a1) * oy1
                    val sc1y = sin(a1) * ox1 + cos(a1) * oy1
                    val sc1Curve = sc1y - sc1x * sc1x * (0.45f + pS3 * 0.75f)
                    val sc1Len = smoothstep(0.26f, 0.17f, abs(sc1x))
                    val sc1 = (exp(-abs(sc1Curve) * 230f) * 0.72f + exp(-abs(sc1Curve) * 36f) * 0.34f) * sc1Len

                    // Scratch 2 (secondary finer cross-scuff)
                    val a2 = -pS2 * PI.toFloat() - 0.78f
                    val ox2 = screenU - (0.14f + pS3 * 0.72f)
                    val oy2 = screenV - (0.20f + pS1 * 0.60f)
                    val sc2x = cos(a2) * ox2 - sin(a2) * oy2
                    val sc2y = sin(a2) * ox2 + cos(a2) * oy2
                    val sc2Curve = sc2y + sc2x * sc2x * 0.60f
                    val sc2Len = smoothstep(0.21f, 0.13f, abs(sc2x))
                    val sc2 = (exp(-abs(sc2Curve) * 280f) * 0.58f + exp(-abs(sc2Curve) * 44f) * 0.26f) * sc2Len

                    // Scratch 3 (short corner abrasion nick)
                    val a3 = pS3 * 6.283f
                    val ox3 = screenU - (0.76f - pS2 * 0.52f)
                    val oy3 = screenV - (0.78f - pS3 * 0.54f)
                    val sc3x = cos(a3) * ox3 - sin(a3) * oy3
                    val sc3y = sin(a3) * ox3 + cos(a3) * oy3
                    val sc3 = exp(-abs(sc3y) * 320f) * smoothstep(0.13f, 0.07f, abs(sc3x)) * 0.50f

                    // Localized hazy acrylic pocket scuff patch
                    val scuffDx = screenU - (0.22f + pS2 * 0.56f)
                    val scuffDy = screenV - (0.24f + pS3 * 0.52f)
                    val scuffPatch = exp(-(scuffDx * scuffDx + scuffDy * scuffDy) * 14f) *
                        cpuFbm(screenU * 10.5f + pS1 * 13f, screenV * 10.5f + pS2 * 7f)

                    val scatter = (swirl * 0.34f + (sc1 + sc2 + sc3) * 0.42f + scuffPatch * 0.20f) * plasticScratches * lightCatch
                    r = screenBlend1(r, scatter * 0.96f)
                    g = screenBlend1(g, scatter * 0.93f)
                    b = screenBlend1(b, scatter * 0.87f)

                    // Vertical plastic film-gate emulsion pressure scratch (Holga / Jetable transport rail)
                    if (plasticScratches > 0.35f) {
                        val railX1 = 0.14f + fract(seed * 31.7f + 0.19f) * 0.72f
                        val railBreak = smoothstep(0.32f, 0.76f, cpuFbm(screenV * 6.2f, railX1 * 14f))
                        val brightGroove = exp(-abs(screenU - railX1) * 780f) * railBreak
                        val darkEdge = exp(-abs(screenU - (railX1 + 0.0026f)) * 680f) * railBreak
                        r = r * (1f - darkEdge * 0.16f * plasticScratches) + 0.34f * brightGroove * 0.28f * plasticScratches
                        g = g * (1f - darkEdge * 0.16f * plasticScratches) + 0.31f * brightGroove * 0.28f * plasticScratches
                        b = b * (1f - darkEdge * 0.16f * plasticScratches) + 0.27f * brightGroove * 0.28f * plasticScratches
                    }
                }

                // Vignetting
                val vig = (1f - lens.vignetteStrength * smoothstep(0.22f, 0.95f, r2 * 1.45f)).coerceIn(0.18f, 1f)
                r *= vig * totalEvGain
                g *= vig * totalEvGain
                b *= vig * totalEvGain

                // Stage 2: Body color, warmth, contrast & saturation
                r *= (1f + body.warmth * 0.55f)
                b *= (1f - body.warmth * 0.55f)
                val lumaB = r * 0.299f + g * 0.587f + b * 0.114f
                val shW = 1f - smoothstep(0f, 0.55f, lumaB)
                val hiW = smoothstep(0.45f, 1f, lumaB)
                r += body.shadowR * shW + body.highlightR * hiW
                g += body.shadowG * shW + body.highlightG * hiW
                b += body.shadowB * shW + body.highlightB * hiW
                r = (r - 0.5f) * body.contrast + 0.5f
                g = (g - 0.5f) * body.contrast + 0.5f
                b = (b - 0.5f) * body.contrast + 0.5f
                val lumaAB = r * 0.299f + g * 0.587f + b * 0.114f
                r = mix(lumaAB, r, body.saturation)
                g = mix(lumaAB, g, body.saturation)
                b = mix(lumaAB, b, body.saturation)

                // Procedural Light Leaks (all 8 archetypes + two-stage burn!)
                var postLeakR = 0f
                var postLeakG = 0f
                var postLeakB = 0f
                if (leakIntensity > 0.01f || exp.fogLift > 0.025f) {
                    val px = if (s2 > 0.5f) 1f - screenU else screenU
                    val py = if (s3 > 0.5f) 1f - screenV else screenV
                    val nCloud = cpuFbm(px * 2.6f + s1 * 17f, py * 3.6f + s3 * 23f)
                    val nFine = cpuFbm(px * 6.2f + s2 * 11f, py * 5.0f + s2 * 11f)
                    val leakBreath = 0.88f + 0.14f * sin(tSlow * 0.82f + s2 * 6.283f + nCloud * 2.2f)
                    val lR: Float; val lG: Float; val lB: Float
                    if (leakIntensity <= 0.01f) {
                        val ec = exp(-hypot((px + sweepDrift) * 1.18f, (py - sweepDrift) * 1.12f) * 2.35f) * (0.58f + 0.55f * nCloud)
                        lR = ec * (0.92f + 0.35f * 0.95f)
                        lG = ec * (0.38f * 0.92f + 0.16f * 0.35f)
                        lB = ec * (0.14f * 0.92f + 0.28f * 0.35f)
                    } else if (archetype < 0.5f) {
                        val edgeW = max(0.14f + s3 * 0.15f + sweepDrift * 0.45f, 0.05f)
                        val d = max(px + (nCloud - 0.5f) * 0.085f, 0f)
                        val band = exp(-sq(d / edgeW)) * (0.64f + 0.58f * nCloud)
                        val core = exp(-sq(d / max(edgeW * 0.38f, 0.02f))) * (0.80f + 0.25f * nFine)
                        lR = band * 1.05f + core * 0.72f
                        lG = 0.24f * band * 1.05f + 0.86f * core * 0.72f
                        lB = 0.04f * band * 1.05f + 0.42f * core * 0.72f
                    } else if (archetype < 1.5f) {
                        val dc = hypot((px + sweepDrift * 0.7f) * 1.05f, py * 0.88f)
                        val spShadow = 0.72f + 0.28f * sin(py * 32f + s2 * 6.283f + tSlow * 1.15f)
                        val spill = exp(-dc * dc * (2.6f + s3 * 1.6f)) * spShadow * (0.65f + 0.50f * nCloud)
                        val sec = 0.38f + 0.24f * nFine
                        lR = spill * (1.12f + 0.98f * sec)
                        lG = spill * (0.48f * 1.12f + 0.14f * sec)
                        lB = spill * (0.10f * 1.12f + 0.08f * sec)
                    } else if (archetype < 2.5f) {
                        val wash = smoothstep(0.68f + sweepDrift, 0f, px * 0.80f + py * 0.22f + (nCloud - 0.5f) * 0.38f)
                        val t = smoothstep(0.38f, 0.88f, nCloud)
                        val k = wash * (0.62f + 0.48f * nCloud) * 1.04f
                        lR = mix(0.94f, 1.0f, t) * k
                        lG = mix(0.12f, 0.48f, t) * k
                        lB = mix(0.16f, 0.12f, t) * k
                    } else if (archetype < 3.5f) {
                        val waveY = 0.12f + s3 * 0.28f + sweepDrift + (nCloud - 0.5f) * 0.08f
                        val ribbon = exp(-sq((py - waveY) * (5.4f + s2 * 3.8f))) * smoothstep(0.96f, 0.08f, px) * (0.64f + 0.56f * nCloud)
                        val fil = exp(-sq((py - waveY) * 16f)) * smoothstep(0.84f, 0.04f, px) * (0.65f + 0.35f * nFine)
                        lR = ribbon * 1.02f + fil * 0.65f
                        lG = 0.54f * ribbon * 1.02f + 0.90f * fil * 0.65f
                        lB = 0.16f * ribbon * 1.02f + 0.52f * fil * 0.65f
                    } else if (archetype < 4.5f) {
                        val diag = abs((px * 0.84f + py * 0.54f) - (0.30f + s3 * 0.34f + sweepDrift))
                        val beam = exp(-diag * diag * 15f) * (0.62f + 0.58f * nCloud)
                        val oppFog = exp(-hypot(1f - px, 1f - py) * 1.25f * 2.1f) * (0.70f + 0.30f * nFine) * 0.62f
                        lR = beam * 1.04f + 0.92f * oppFog
                        lG = 0.44f * beam * 1.04f + 0.18f * oppFog
                        lB = 0.10f * beam * 1.04f + 0.58f * oppFog
                    } else if (archetype < 5.5f) {
                        val plasma = cpuFbm(px * 3.0f + s1 * 9f, py * 4.0f + s3 * 13f)
                        val mask = smoothstep(0.24f, 0.82f, plasma * 0.95f + (1f - px) * 0.35f) * 1.08f
                        lR = 1.0f * mask
                        lG = mix(0.20f, 0.62f, plasma) * mask
                        lB = mix(0.32f, 0.14f, plasma) * mask
                    } else if (archetype < 6.5f) {
                        val cA = exp(-hypot(px + sweepDrift * 0.6f, py) * 1.15f * 2.55f) * (0.68f + 0.45f * nCloud)
                        val cB = exp(-hypot(1f - px, 1f - py - sweepDrift * 0.6f) * 1.22f * 2.80f) * (0.62f + 0.45f * nFine)
                        lR = 1.0f * cA * 1.02f + 0.96f * cB * 0.88f
                        lG = 0.54f * cA * 1.02f + 0.14f * cB * 0.88f
                        lB = 0.12f * cA * 1.02f + 0.20f * cB * 0.88f
                    } else {
                        val curtain = exp(-sq((px + sweepDrift * 0.5f) / 0.24f)) * (0.58f + 0.56f * nCloud)
                        val corner = exp(-hypot(px * 1.2f, py) * 2.4f) * (0.65f + 0.35f * nFine)
                        lR = curtain * 0.86f + corner * 0.62f
                        lG = 0.42f * curtain * 0.86f + 0.74f * corner * 0.62f
                        lB = 0.10f * curtain * 0.86f + 0.30f * corner * 0.62f
                    }
                    val totalGain = baseShotStrength * leakBreath
                    r = screenBlend1(r, lR * totalGain * 0.60f)
                    g = screenBlend1(g, lG * totalGain * 0.60f)
                    b = screenBlend1(b, lB * totalGain * 0.60f)
                    postLeakR = lR * totalGain * 0.48f
                    postLeakG = lG * totalGain * 0.48f
                    postLeakB = lB * totalGain * 0.48f
                }

                // Stage 3: Film Emulsion (Infrared, HueShift, RGB gain, Mono, Sensitometric S-curve)
                r = r.coerceIn(0f, 1.35f); g = g.coerceIn(0f, 1.35f); b = b.coerceIn(0f, 1.35f)
                if (film.infraredAmount > 0.02f) {
                    val foliage = ((g - max(r, b)) * 3f).coerceIn(0f, 1f)
                    val irR = (g * 1.38f + r * 0.22f).coerceIn(0f, 1f)
                    val irG = (r * 0.55f + b * 0.25f).coerceIn(0f, 1f)
                    val irB = (b * 1.15f + g * 0.18f).coerceIn(0f, 1f)
                    val m = film.infraredAmount * (0.45f + 0.55f * foliage)
                    r = mix(r, irR, m); g = mix(g, irG, m); b = mix(b, irB, m)
                }
                if (abs(film.hueShift) > 0.01f) {
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(
                        (r.coerceIn(0f, 1f) * 255f).roundToInt(),
                        (g.coerceIn(0f, 1f) * 255f).roundToInt(),
                        (b.coerceIn(0f, 1f) * 255f).roundToInt(),
                        hsv
                    )
                    hsv[0] = ((hsv[0] / 360f + film.hueShift) % 1f + 1f) % 1f * 360f
                    val shifted = Color.HSVToColor(hsv)
                    r = ((shifted ushr 16) and 0xFF) / 255f
                    g = ((shifted ushr 8) and 0xFF) / 255f
                    b = (shifted and 0xFF) / 255f
                }

                r *= film.redGain; g *= film.greenGain; b *= film.blueGain
                if (film.isMonochrome || film.orthoUvAmount > 0.05f) {
                    val stdBw = r * 0.32f + g * 0.56f + b * 0.12f
                    val orthoBw = (b * 0.72f + g * 0.25f + r * 0.03f).coerceIn(0f, 1.25f)
                    val bw = mix(stdBw, orthoBw, film.orthoUvAmount.coerceIn(0f, 1f))
                    if (film.isMonochrome) {
                        r = bw; g = bw; b = bw
                    } else {
                        val om = film.orthoUvAmount * 0.85f
                        r = mix(r, bw, om); g = mix(g, bw, om); b = mix(b, bw, om)
                    }
                }

                val shK = film.shoulderCompress * 1.15f
                r = mix(r, r / (1f + shK * max(r - 0.52f, 0f)), 0.88f)
                g = mix(g, g / (1f + shK * max(g - 0.52f, 0f)), 0.88f)
                b = mix(b, b / (1f + shK * max(b - 0.52f, 0f)), 0.88f)

                val filmCon = film.contrast * dev.contrastMul * exp.contrastFactor
                val toe = film.toeLift + exp.fogLift
                r = mix(toe, 0.985f, ((r - 0.5f) * filmCon + 0.5f).coerceIn(0f, 1f))
                g = mix(toe, 0.985f, ((g - 0.5f) * filmCon + 0.5f).coerceIn(0f, 1f))
                b = mix(toe, 0.985f, ((b - 0.5f) * filmCon + 0.5f).coerceIn(0f, 1f))

                val lumaF = r * 0.299f + g * 0.587f + b * 0.114f
                val fSh = 1f - smoothstep(0f, 0.52f, lumaF)
                val fHi = smoothstep(0.48f, 1f, lumaF)
                r += film.shadowTintR * fSh + film.highlightTintR * fHi
                g += film.shadowTintG * fSh + film.highlightTintG * fHi
                b += film.shadowTintB * fSh + film.highlightTintB * fHi

                val lumaSat = r * 0.299f + g * 0.587f + b * 0.114f
                val filmSat = film.saturation * dev.saturationMul * exp.saturationFactor
                r = mix(lumaSat, r, filmSat); g = mix(lumaSat, g, filmSat); b = mix(lumaSat, b, filmSat)

                // Expiration Cast
                val expW = 0.35f + 0.65f * (1f - smoothstep(0f, 0.85f, lumaSat))
                r += exp.castR * expW; g += exp.castG * expW; b += exp.castB * expW

                // Lab Dev: Cross-Process & Bleach Bypass
                if (dev.crossProcessStrength > 0.02f) {
                    val l = r * 0.299f + g * 0.587f + b * 0.114f
                    val xr = smoothstep(0.05f, 0.92f, r * 1.08f) - 0.02f * (1f - l) + 0.06f * smoothstep(0.5f, 1f, l)
                    val xg = smoothstep(0.02f, 0.95f, g * 1.12f) + 0.05f * (1f - l) + 0.07f * smoothstep(0.5f, 1f, l)
                    val xb = b.coerceIn(0f, 1f).toDouble().pow(1.18).toFloat() * 0.94f + (1f - l) * 0.18f - 0.05f * smoothstep(0.5f, 1f, l)
                    r = mix(r, xr, dev.crossProcessStrength)
                    g = mix(g, xg, dev.crossProcessStrength)
                    b = mix(b, xb, dev.crossProcessStrength)
                }
                if (dev.bleachBypassStrength > 0.02f) {
                    val l = (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f)
                    val silver = smoothstep(0.08f, 0.92f, l)
                    val oR = if (r < 0.5f) 2f * r * silver else 1f - 2f * (1f - r) * (1f - silver)
                    val oG = if (g < 0.5f) 2f * g * silver else 1f - 2f * (1f - g) * (1f - silver)
                    val oB = if (b < 0.5f) 2f * b * silver else 1f - 2f * (1f - b) * (1f - silver)
                    val m = dev.bleachBypassStrength * 0.85f
                    r = mix(r, mix(l, oR, 0.45f), m)
                    g = mix(g, mix(l, oG, 0.45f), m)
                    b = mix(b, mix(l, oB, 0.45f), m)
                }
                if (sepia > 0.02f) {
                    val l = r * 0.299f + g * 0.587f + b * 0.114f
                    r = mix(r, l * 1.12f + 0.04f, sepia)
                    g = mix(g, l * 0.92f + 0.01f, sepia)
                    b = mix(b, l * 0.66f, sepia)
                }
                if (dev.platinumBathStrength > 0.02f) {
                    val l = smoothstep(0.02f, 0.98f, (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f))
                    r = mix(r, mix(0.07f, 0.95f, l), dev.platinumBathStrength)
                    g = mix(g, mix(0.065f, 0.92f, l), dev.platinumBathStrength)
                    b = mix(b, mix(0.058f, 0.86f, l), dev.platinumBathStrength)
                }
                if (cyano > 0.02f) {
                    val l = smoothstep(0.04f, 0.96f, (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f))
                    r = mix(r, mix(0.05f, 0.88f, l), cyano)
                    g = mix(g, mix(0.16f, 0.93f, l), cyano)
                    b = mix(b, mix(0.34f, 0.95f, l), cyano)
                }
                val effAutochrome = max(film.autochromeMosaicAmount, body.autochromeMosaicAmount * 0.85f)
                if (effAutochrome > 0.02f) {
                    val sCellX = floor(x / 2.25f)
                    val sCellY = floor(y / 2.25f)
                    val gHash = cpuHash(sCellX + params.lightLeakSeed * 37f, sCellY + 19.3f)
                    val mR: Float; val mG: Float; val mB: Float
                    if (gHash < 0.34f) {
                        mR = r * 1.24f; mG = g * 0.90f; mB = b * 0.72f
                    } else if (gHash < 0.68f) {
                        mR = r * 0.82f; mG = g * 1.22f; mB = b * 0.80f
                    } else {
                        mR = r * 0.86f; mG = g * 0.84f; mB = b * 1.28f
                    }
                    val am = (effAutochrome * 0.72f).coerceIn(0f, 0.75f)
                    r = mix(r, mR, am); g = mix(g, mG, am); b = mix(b, mB, am)
                }
                if (film.paperFiberAmount > 0.02f) {
                    val rag = (cpuFbm(screenU * 24f + params.lightLeakSeed * 11f, screenV * 24f + params.lightLeakSeed * 23f) - 0.5f) * 0.16f * film.paperFiberAmount
                    r = (r + rag * 1.05f).coerceIn(0f, 1f)
                    g = (g + rag * 0.98f).coerceIn(0f, 1f)
                    b = (b + rag * 0.86f).coerceIn(0f, 1f)
                }
                if (film.brushBorderAmount > 0.02f) {
                    val edgeD = min(min(screenU, 1f - screenU), min(screenV, 1f - screenV))
                    val bNoise = cpuFbm(screenU * 18f + params.lightLeakSeed * 17f, screenV * 18f + params.lightLeakSeed * 29f) * 0.068f
                    val pMask = smoothstep(0.025f + bNoise, 0.008f + bNoise * 0.45f, edgeD) * film.brushBorderAmount.coerceIn(0f, 1f)
                    r = mix(r, 0.92f, pMask); g = mix(g, 0.89f, pMask); b = mix(b, 0.82f, pMask)
                }
                val effCollodion = max(max(film.collodionPlateAmount, body.collodionPlateAmount * 0.85f), dev.collodionBathStrength)
                if (effCollodion > 0.02f) {
                    val cEdge = min(min(screenU, 1f - screenU), min(screenV, 1f - screenV))
                    val cDist = hypot(screenU - 0.5f, screenV - 0.5f)
                    val pNoise = cpuFbm(screenU * 5.2f + params.lightLeakSeed * 19f, screenV * 5.2f + params.lightLeakSeed * 7f)
                    val pourMask = max(
                        smoothstep(0.055f + pNoise * 0.065f, 0.005f + pNoise * 0.02f, cEdge),
                        smoothstep(0.56f + pNoise * 0.14f, 0.72f + pNoise * 0.08f, cDist) * 0.92f
                    ).coerceIn(0f, 1f)
                    r = r * (1f - pourMask * 0.84f * effCollodion) + 0.045f * pourMask * effCollodion
                    g = g * (1f - pourMask * 0.84f * effCollodion) + 0.042f * pourMask * effCollodion
                    b = b * (1f - pourMask * 0.84f * effCollodion) + 0.036f * pourMask * effCollodion
                }
                val effDaguerre = max(max(film.daguerreTarnishAmount, body.daguerreTarnishAmount * 0.85f), dev.daguerreBathStrength)
                if (effDaguerre > 0.02f) {
                    val dEdge = hypot((screenU - 0.5f) * 1.08f, (screenV - 0.5f) * 0.96f)
                    val tRing = smoothstep(0.38f, 0.72f, dEdge)
                    r = mix(r, r * 0.50f + 0.24f, tRing * 0.72f * effDaguerre)
                    g = mix(g, g * 0.50f + 0.20f, tRing * 0.72f * effDaguerre)
                    b = mix(b, b * 0.50f + 0.24f, tRing * 0.72f * effDaguerre)
                }

                // Post-shoulder luminous light-leak burn
                if (max(postLeakR, max(postLeakG, postLeakB)) > 0.002f) {
                    if (film.isMonochrome) {
                        val bwL = (postLeakR * 0.45f + postLeakG * 0.45f + postLeakB * 0.10f) * 0.85f
                        r = screenBlend1(r, bwL); g = screenBlend1(g, bwL); b = screenBlend1(b, bwL)
                    } else {
                        r = screenBlend1(r, postLeakR)
                        g = screenBlend1(g, postLeakG)
                        b = screenBlend1(b, postLeakB)
                    }
                }

                // Film Grain
                val n = (rng.nextFloat() - 0.5f) * grainStrength * 0.36f
                val ir = ((r + n).coerceIn(0f, 1f) * 255f).roundToInt()
                val ig = ((g + n).coerceIn(0f, 1f) * 255f).roundToInt()
                val ib = ((b + n).coerceIn(0f, 1f) * 255f).roundToInt()
                outPixels[y * w + x] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }
        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun fract(x: Float): Float = x - floor(x)
    private fun sq(x: Float): Float = x * x
    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private fun screenBlend1(base: Float, overlay: Float): Float {
        val cb = base.coerceIn(0f, 1f)
        val co = overlay.coerceIn(0f, 1f)
        return 1f - (1f - cb) * (1f - co)
    }
    private fun cpuHash(ix: Float, iy: Float): Float {
        val h = sin(ix * 127.1f + iy * 311.7f) * 43758.5453f
        return h - floor(h)
    }
    private fun cpuValueNoise(x: Float, y: Float): Float {
        val ix = floor(x); val iy = floor(y)
        val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = cpuHash(ix, iy)
        val b = cpuHash(ix + 1f, iy)
        val c = cpuHash(ix, iy + 1f)
        val d = cpuHash(ix + 1f, iy + 1f)
        return mix(mix(a, b, ux), mix(c, d, ux), uy)
    }
    private fun cpuFbm(x: Float, y: Float): Float {
        var v = 0.55f * cpuValueNoise(x, y)
        v += 0.30f * cpuValueNoise(x * 2.04f + 13.7f, y * 2.04f + 7.1f)
        v += 0.15f * cpuValueNoise(x * 4.12f + 5.3f, y * 4.12f + 19.4f)
        return v
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

    /**
     * Wraps the developed photograph inside the Camera Body's authentic physical contour
     * (Polaroid SX-70 white card, Instax Mini card, 35mm perforated film strip,
     * Hasselblad 6x6 V-notched rabbet, XPan panoramic sprockets, 90s satin print, Holga mask, Jetable card).
     */
    private fun applyCameraFrameBorder(
        photo: Bitmap,
        body: com.example.domain.model.CameraBodyModel,
        film: com.example.domain.model.FilmStockModel,
        frameNumber: Int
    ): Bitmap {
        val w = photo.width
        val h = photo.height
        val base = min(w, h).toFloat()
        val style = body.frameBorderStyle

        val padStart: Int
        val padTop: Int
        val padEnd: Int
        val padBottom: Int
        val bgColor: Int

        when (style) {
            com.example.domain.model.FrameBorderStyle.POLAROID_WHITE -> {
                val side = (base * 0.072f).roundToInt().coerceAtLeast(24)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.255f).roundToInt().coerceAtLeast(90)
                bgColor = Color.rgb(245, 241, 232)
            }
            com.example.domain.model.FrameBorderStyle.INSTAX_MINI_WHITE -> {
                val side = (base * 0.085f).roundToInt().coerceAtLeast(24)
                padStart = side; padEnd = side
                padTop = (base * 0.095f).roundToInt().coerceAtLeast(28)
                padBottom = (base * 0.25f).roundToInt().coerceAtLeast(80)
                bgColor = Color.rgb(249, 248, 244)
            }
            com.example.domain.model.FrameBorderStyle.COMPACT_LAB_PRINT -> {
                val side = (base * 0.055f).roundToInt().coerceAtLeast(20)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.065f).roundToInt().coerceAtLeast(24)
                bgColor = Color.rgb(241, 235, 223)
            }
            com.example.domain.model.FrameBorderStyle.FILM_35MM_SPROCKETS,
            com.example.domain.model.FrameBorderStyle.PANORAMIC_XPAN -> {
                val rail = (base * 0.115f).roundToInt().coerceAtLeast(42)
                val side = (base * 0.028f).roundToInt().coerceAtLeast(10)
                padStart = side; padEnd = side; padTop = rail; padBottom = rail
                bgColor = Color.rgb(12, 12, 15)
            }
            com.example.domain.model.FrameBorderStyle.HALF_FRAME_STRIP -> {
                val rail = (base * 0.11f).roundToInt().coerceAtLeast(38)
                val topB = (base * 0.03f).roundToInt().coerceAtLeast(10)
                padStart = rail; padEnd = rail; padTop = topB; padBottom = topB
                bgColor = Color.rgb(12, 12, 15)
            }
            com.example.domain.model.FrameBorderStyle.MEDIUM_FORMAT_6X6 -> {
                padStart = (base * 0.065f).roundToInt().coerceAtLeast(24)
                padEnd = (base * 0.048f).roundToInt().coerceAtLeast(18)
                padTop = (base * 0.048f).roundToInt().coerceAtLeast(18)
                padBottom = (base * 0.065f).roundToInt().coerceAtLeast(24)
                bgColor = Color.rgb(10, 10, 12)
            }
            com.example.domain.model.FrameBorderStyle.HOLGA_MASK_6X6 -> {
                val side = (base * 0.055f).roundToInt().coerceAtLeast(20)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.075f).roundToInt().coerceAtLeast(28)
                bgColor = Color.rgb(10, 10, 11)
            }
            com.example.domain.model.FrameBorderStyle.DISPOSABLE_CARD -> {
                val side = (base * 0.048f).roundToInt().coerceAtLeast(18)
                padStart = side; padEnd = side
                padTop = (base * 0.055f).roundToInt().coerceAtLeast(22)
                padBottom = (base * 0.082f).roundToInt().coerceAtLeast(34)
                bgColor = Color.rgb(22, 21, 19)
            }
            com.example.domain.model.FrameBorderStyle.WET_PLATE_COLLODION -> {
                val side = (base * 0.062f).roundToInt().coerceAtLeast(24)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.092f).roundToInt().coerceAtLeast(36)
                bgColor = Color.rgb(19, 17, 15)
            }
            com.example.domain.model.FrameBorderStyle.DAGUERREOTYPE_CASE -> {
                val side = (base * 0.088f).roundToInt().coerceAtLeast(32)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.11f).roundToInt().coerceAtLeast(42)
                bgColor = Color.rgb(42, 20, 18)
            }
            com.example.domain.model.FrameBorderStyle.AUTOCHROME_GLASS -> {
                val side = (base * 0.058f).roundToInt().coerceAtLeast(22)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.088f).roundToInt().coerceAtLeast(34)
                bgColor = Color.rgb(16, 15, 14)
            }
            com.example.domain.model.FrameBorderStyle.PINHOLE_WOOD_BOX -> {
                val side = (base * 0.064f).roundToInt().coerceAtLeast(24)
                padStart = side; padEnd = side; padTop = side
                padBottom = (base * 0.088f).roundToInt().coerceAtLeast(34)
                bgColor = Color.rgb(44, 29, 18)
            }
        }

        val outW = w + padStart + padEnd
        val outH = h + padTop + padBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bgColor)

        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(photo, padStart.toFloat(), padTop.toFloat(), imgPaint)

        // Subtle inner edge hairline
        val innerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = max(2f, base * 0.002f)
            color = if (bgColor == Color.rgb(245, 241, 232) || bgColor == Color.rgb(249, 248, 244) || bgColor == Color.rgb(241, 235, 223)) {
                Color.argb(65, 25, 22, 18)
            } else {
                Color.argb(40, 255, 255, 255)
            }
        }
        canvas.drawRect(
            padStart.toFloat(),
            padTop.toFloat(),
            (padStart + w).toFloat(),
            (padTop + h).toFloat(),
            innerBorder
        )

        when (style) {
            com.example.domain.model.FrameBorderStyle.FILM_35MM_SPROCKETS,
            com.example.domain.model.FrameBorderStyle.PANORAMIC_XPAN -> {
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(38, 40, 48) }
                val holeW = base * 0.052f
                val holeH = padTop * 0.42f
                val gap = base * 0.048f
                val r = holeH * 0.22f
                var x = padStart.toFloat() + gap * 0.3f
                val topHoleY = padTop * 0.44f
                val botHoleY = outH - padBottom + padBottom * 0.14f
                while (x + holeW < outW - padEnd) {
                    canvas.drawRoundRect(x, topHoleY, x + holeW, topHoleY + holeH, r, r, holePaint)
                    canvas.drawRoundRect(x, botHoleY, x + holeW, botHoleY + holeH, r, r, holePaint)
                    x += holeW + gap
                }
                val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(245, 168, 56)
                    typeface = Typeface.MONOSPACE
                    textSize = (padTop * 0.24f).coerceIn(14f, 36f)
                    letterSpacing = 0.12f
                }
                val topLabel = if (style == com.example.domain.model.FrameBorderStyle.PANORAMIC_XPAN) {
                    "XPAN 24×65  •  ${film.code}  •  ISO ${film.iso}"
                } else {
                    "PELOCHE 135  •  ${film.code}  •  ISO ${film.iso}"
                }
                canvas.drawText(topLabel, padStart.toFloat(), padTop * 0.32f, edgePaint)
                canvas.drawText(
                    "▲ %02dA      ${body.shortCode}      %02d ▲".format(frameNumber, frameNumber + 1),
                    padStart.toFloat(),
                    outH - padBottom * 0.16f,
                    edgePaint
                )
            }

            com.example.domain.model.FrameBorderStyle.HALF_FRAME_STRIP -> {
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(38, 40, 48) }
                val holeW = padStart * 0.44f
                val holeH = base * 0.055f
                val gap = base * 0.05f
                val r = holeW * 0.2f
                val lx = (padStart - holeW) / 2f
                val rx = outW - padEnd + (padEnd - holeW) / 2f
                var y = padTop.toFloat() + gap * 0.4f
                while (y + holeH < outH - padBottom) {
                    canvas.drawRoundRect(lx, y, lx + holeW, y + holeH, r, r, holePaint)
                    canvas.drawRoundRect(rx, y, rx + holeW, y + holeH, r, r, holePaint)
                    y += holeH + gap
                }
            }

            com.example.domain.model.FrameBorderStyle.MEDIUM_FORMAT_6X6 -> {
                // Hasselblad A12 dual V-notches on left edge of photo aperture
                val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 10, 12) }
                val notchDepth = padStart * 0.38f
                val y1 = padTop + h * 0.28f
                val y2 = padTop + h * 0.36f
                val p = android.graphics.Path().apply {
                    moveTo(padStart.toFloat(), y1 - notchDepth)
                    lineTo(padStart + notchDepth, y1)
                    lineTo(padStart.toFloat(), y1 + notchDepth)
                    close()
                    moveTo(padStart.toFloat(), y2 - notchDepth)
                    lineTo(padStart + notchDepth, y2)
                    lineTo(padStart.toFloat(), y2 + notchDepth)
                    close()
                }
                canvas.drawPath(p, notchPaint)
                val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(245, 168, 56)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.36f).coerceIn(14f, 32f)
                }
                canvas.drawText("120 • 6×6 • ${film.code} • N°%02d".format(frameNumber), padStart.toFloat(), outH - padBottom * 0.30f, markPaint)
            }

            com.example.domain.model.FrameBorderStyle.POLAROID_WHITE -> {
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(58, 54, 48)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    textSize = (padBottom * 0.22f).coerceIn(22f, 56f)
                }
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(122, 116, 106)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.12f).coerceIn(14f, 30f)
                }
                val textY = padTop + h + padBottom * 0.46f
                canvas.drawText("Peloche SX-70", padStart.toFloat(), textY, titlePaint)
                canvas.drawText("${film.name}  •  ISO ${film.iso}", padStart.toFloat(), textY + padBottom * 0.22f, subPaint)

                // Iconic 5-stripe spectrum mark on bottom right
                val stripeColors = intArrayOf(
                    Color.rgb(230, 57, 70),
                    Color.rgb(244, 162, 97),
                    Color.rgb(233, 196, 106),
                    Color.rgb(42, 157, 143),
                    Color.rgb(38, 70, 83)
                )
                val sw = (base * 0.012f).coerceAtLeast(6f)
                val sg = sw * 0.45f
                val sh = padBottom * 0.34f
                val startX = outW - padEnd - (stripeColors.size * sw + (stripeColors.size - 1) * sg)
                val topY = padTop + h + (padBottom - sh) * 0.5f
                val sp = Paint(Paint.ANTI_ALIAS_FLAG)
                stripeColors.forEachIndexed { idx, col ->
                    sp.color = col
                    val sx = startX + idx * (sw + sg)
                    canvas.drawRoundRect(sx, topY, sx + sw, topY + sh, 3f, 3f, sp)
                }
            }

            com.example.domain.model.FrameBorderStyle.INSTAX_MINI_WHITE -> {
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(86, 82, 76)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.16f).coerceIn(16f, 34f)
                    letterSpacing = 0.12f
                }
                canvas.drawText(
                    "INSTAX  •  ${film.code}",
                    padStart.toFloat(),
                    padTop + h + padBottom * 0.56f,
                    labelPaint
                )
            }

            com.example.domain.model.FrameBorderStyle.DISPOSABLE_CARD -> {
                val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 185, 49) }
                canvas.drawRect(0f, 0f, outW.toFloat(), max(6f, padTop * 0.16f), stripe)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(232, 185, 49)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.32f).coerceIn(14f, 30f)
                }
                canvas.drawText(
                    "JETABLE 27 • ${film.code} • %02d/27".format(frameNumber),
                    padStart.toFloat(),
                    outH - padBottom * 0.32f,
                    labelPaint
                )
            }

            com.example.domain.model.FrameBorderStyle.HOLGA_MASK_6X6 -> {
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(150, 144, 132)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.30f).coerceIn(14f, 28f)
                }
                canvas.drawText(
                    "HOLGA 120N • ${film.code} • %02d".format(frameNumber),
                    padStart.toFloat(),
                    outH - padBottom * 0.32f,
                    labelPaint
                )
            }

            com.example.domain.model.FrameBorderStyle.COMPACT_LAB_PRINT -> Unit

            com.example.domain.model.FrameBorderStyle.WET_PLATE_COLLODION -> {
                // Dark tin/glass wet-plate rim with subtle silver-nitrate corner tarnish & 1855 engraving
                val brassRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(2f, base * 0.003f)
                    color = Color.argb(125, 197, 168, 126)
                }
                canvas.drawRect(padStart * 0.45f, padTop * 0.45f, outW - padEnd * 0.45f, outH - padBottom * 0.55f, brassRim)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(206, 182, 138)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    textSize = (padBottom * 0.34f).coerceIn(15f, 34f)
                }
                canvas.drawText("COLLODION HUMIDE 1855  •  ${film.code}  •  PL. %02d".format(frameNumber), padStart.toFloat(), outH - padBottom * 0.30f, labelPaint)
            }

            com.example.domain.model.FrameBorderStyle.DAGUERREOTYPE_CASE -> {
                // Iconic 1842 Victorian gilded brass oval passe-partout frame over crimson velvet
                val goldOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(4f, base * 0.012f)
                    color = Color.rgb(212, 175, 55)
                }
                val goldInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(2f, base * 0.004f)
                    color = Color.rgb(245, 218, 130)
                }
                canvas.drawRect(padStart * 0.38f, padTop * 0.38f, outW - padEnd * 0.38f, outH - padBottom * 0.42f, goldOuter)
                canvas.drawOval(
                    padStart.toFloat() + w * 0.015f,
                    padTop.toFloat() + h * 0.015f,
                    (padStart + w).toFloat() - w * 0.015f,
                    (padTop + h).toFloat() - h * 0.015f,
                    goldInner
                )
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(232, 198, 112)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    textSize = (padBottom * 0.30f).coerceIn(15f, 32f)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("DAGUERRÉOTYPE 1842  •  PLAQUE N°%02d".format(frameNumber), outW * 0.5f, outH - padBottom * 0.24f, labelPaint)
            }

            com.example.domain.model.FrameBorderStyle.AUTOCHROME_GLASS -> {
                // Archival paper corner label on black taped glass plate (Lumière 1907)
                val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(3f, base * 0.005f)
                    color = Color.rgb(52, 48, 44)
                }
                canvas.drawRect(padStart * 0.3f, padTop * 0.3f, outW - padEnd * 0.3f, outH - padBottom * 0.35f, tapePaint)
                val paperBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 226, 206) }
                val labelW = w * 0.42f
                val labelH = padBottom * 0.58f
                val lx = padStart.toFloat()
                val ly = outH - padBottom * 0.76f
                canvas.drawRect(lx, ly, lx + labelW, ly + labelH, paperBg)
                val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(46, 36, 28)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    textSize = (labelH * 0.56f).coerceIn(13f, 28f)
                }
                canvas.drawText("Autochrome Lumière • %02d".format(frameNumber), lx + labelH * 0.25f, ly + labelH * 0.72f, inkPaint)
            }

            com.example.domain.model.FrameBorderStyle.PINHOLE_WOOD_BOX -> {
                val woodLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = max(2f, base * 0.004f)
                    color = Color.rgb(184, 138, 76)
                }
                canvas.drawRect(padStart * 0.4f, padTop * 0.4f, outW - padEnd * 0.4f, outH - padBottom * 0.48f, woodLine)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(218, 178, 114)
                    typeface = Typeface.MONOSPACE
                    textSize = (padBottom * 0.30f).coerceIn(14f, 30f)
                }
                canvas.drawText("STÉNOPÉ 0,3mm f/128  •  ${film.code}  •  %02d".format(frameNumber), padStart.toFloat(), outH - padBottom * 0.28f, labelPaint)
            }
        }

        return out
    }

    /**
     * Authentic analog Double Exposure (surimpression):
     * Photon exposure on the same emulsion frame combines additively with film highlight shoulder roll-off
     * (screen blend with 0.85 per-exposure attenuation so bright subjects never clip flat white).
     */
    fun blendDoubleExposure(firstShot: Bitmap, secondShot: Bitmap): Bitmap {
        val w = firstShot.width.coerceAtLeast(1)
        val h = firstShot.height.coerceAtLeast(1)

        // Center-crop & scale secondShot into w x h to match firstShot cleanly
        val alignedSecond = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val alignCanvas = Canvas(alignedSecond)
        val srcAspect = secondShot.width.toFloat() / secondShot.height.coerceAtLeast(1).toFloat()
        val dstAspect = w.toFloat() / h.toFloat()
        val srcRect = if (srcAspect > dstAspect) {
            val cropW = (secondShot.height * dstAspect).toInt().coerceIn(1, secondShot.width)
            val left = (secondShot.width - cropW) / 2
            android.graphics.Rect(left, 0, left + cropW, secondShot.height)
        } else {
            val cropH = (secondShot.width / dstAspect).toInt().coerceIn(1, secondShot.height)
            val top = (secondShot.height - cropH) / 2
            android.graphics.Rect(0, top, secondShot.width, top + cropH)
        }
        alignCanvas.drawBitmap(
            secondShot,
            srcRect,
            android.graphics.Rect(0, 0, w, h),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val pxA = IntArray(w * h)
        val pxB = IntArray(w * h)
        firstShot.getPixels(pxA, 0, w, 0, 0, w, h)
        alignedSecond.getPixels(pxB, 0, w, 0, 0, w, h)
        alignedSecond.recycle()

        val outPixels = IntArray(w * h)
        val inv255 = 1f / 255f
        val gain = 0.84f
        for (i in outPixels.indices) {
            val a = pxA[i]
            val b = pxB[i]

            val rA = ((a ushr 16) and 0xFF) * inv255 * gain
            val gA = ((a ushr 8) and 0xFF) * inv255 * gain
            val bA = (a and 0xFF) * inv255 * gain

            val rB = ((b ushr 16) and 0xFF) * inv255 * gain
            val gB = ((b ushr 8) and 0xFF) * inv255 * gain
            val bB = (b and 0xFF) * inv255 * gain

            // Optical screen blend: 1 - (1 - A)(1 - B)
            val r = ((1f - (1f - rA) * (1f - rB)) * 255f).toInt().coerceIn(0, 255)
            val g = ((1f - (1f - gA) * (1f - gB)) * 255f).toInt().coerceIn(0, 255)
            val bl = ((1f - (1f - bA) * (1f - bB)) * 255f).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, w, 0, 0, w, h)
        }
    }
}
