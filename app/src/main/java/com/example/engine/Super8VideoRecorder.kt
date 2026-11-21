package com.example.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import java.io.File
import kotlin.math.roundToInt

/**
 * Hardware-accelerated silent Super 8 / 35mm clip recorder running directly on the
 * AnalogGlSurfaceView GL thread. Shares the live EGLContext and shader pipeline to record
 * every procedural optical effect (breathing light leaks, grain, halation, dust, plastic scratches)
 * into a clean H.264 MP4 container — requiring ZERO microphone or storage permissions.
 */
class Super8VideoRecorder {

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TARGET_FPS = 24
        private const val I_FRAME_INTERVAL = 1
        private const val BIT_RATE = 5_000_000
    }

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private var outputFile: File? = null
    private var startTimeNs: Long = 0L
    private var lastPtsNs: Long = -1L
    private var minFrameStepNs: Long = 1_000_000_000L / TARGET_FPS
    private var bufferInfo = MediaCodec.BufferInfo()

    var videoWidth: Int = 720
        private set
    var videoHeight: Int = 480
        private set

    @Volatile
    var isRecording: Boolean = false
        private set

    fun start(
        eglDisplay: EGLDisplay,
        fallbackConfig: EGLConfig?,
        eglContext: EGLContext,
        targetFile: File,
        aspectRatio: Float,
        targetFps: Int = TARGET_FPS
    ): Boolean {
        if (isRecording) return false
        return try {
            val safeFps = targetFps.coerceIn(6, 30)
            val (w, h) = computeAlignedDimensions(aspectRatio.coerceIn(0.45f, 2.8f))
            videoWidth = w
            videoHeight = h
            outputFile = targetFile
            targetFile.parentFile?.mkdirs()

            val format = MediaFormat.createVideoFormat(MIME_TYPE, w, h).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            val mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = mediaCodec.createInputSurface()
            mediaCodec.start()

            val recordableConfig = chooseRecordableConfig(eglDisplay) ?: fallbackConfig
                ?: throw IllegalStateException("No valid EGLConfig for Super 8 recorder")
            val eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                recordableConfig,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("eglCreateWindowSurface failed for Super 8 recorder")
            }

            val mediaMuxer = MediaMuxer(
                targetFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            codec = mediaCodec
            inputSurface = surface
            encoderEglSurface = eglSurface
            muxer = mediaMuxer
            trackIndex = -1
            muxerStarted = false
            startTimeNs = System.nanoTime()
            lastPtsNs = -1L
            minFrameStepNs = (1_000_000_000L / safeFps.toLong()).coerceAtLeast(16_000_000L)
            isRecording = true
            true
        } catch (_: Throwable) {
            releaseResources(eglDisplay)
            false
        }
    }

    /**
     * Switches to the encoder EGLSurface, invokes [drawAtSize] to draw the live analog shader
     * at (videoWidth, videoHeight), throttles cadence to the selected cinema format FPS
     * (8 i/s LomoKino, 14 i/s Muet 1920, 18 i/s Super 8, 24/25 i/s), stamps monotonic PTS,
     * swaps buffers, and drains encoded NAL units into the MP4 muxer.
     */
    fun encodeFrame(
        eglDisplay: EGLDisplay,
        eglContext: EGLContext,
        drawAtSize: (width: Int, height: Int) -> Unit
    ) {
        if (!isRecording || encoderEglSurface == EGL14.EGL_NO_SURFACE) return
        val rawPts = (System.nanoTime() - startTimeNs).coerceAtLeast(0L)
        // Enforce authentic vintage cinema frame-rate cadence (skip intermediate preview frames)
        if (lastPtsNs >= 0L && (rawPts - lastPtsNs) < (minFrameStepNs * 85L / 100L)) {
            return
        }
        try {
            drainEncoder(endOfStream = false)
            if (!EGL14.eglMakeCurrent(eglDisplay, encoderEglSurface, encoderEglSurface, eglContext)) {
                return
            }
            drawAtSize(videoWidth, videoHeight)

            val ptsNs = if (lastPtsNs >= 0L && rawPts <= lastPtsNs) {
                lastPtsNs + minFrameStepNs
            } else {
                rawPts
            }
            lastPtsNs = ptsNs
            EGLExt.eglPresentationTimeANDROID(eglDisplay, encoderEglSurface, ptsNs)
            EGL14.eglSwapBuffers(eglDisplay, encoderEglSurface)
        } catch (_: Throwable) {
        }
    }

    /**
     * Signals EOS, flushes the MP4 muxer, restores [restoreSurface] on the GL thread,
     * and returns the finalized MP4 file if valid.
     */
    fun stopAndRelease(
        eglDisplay: EGLDisplay,
        restoreSurface: EGLSurface,
        eglContext: EGLContext
    ): File? {
        if (!isRecording) return null
        isRecording = false
        val out = outputFile
        var success = false
        try {
            drainEncoder(endOfStream = true)
            success = muxerStarted && out != null && out.exists() && out.length() > 1024L
        } catch (_: Throwable) {
            success = false
        } finally {
            releaseResources(eglDisplay)
            if (eglDisplay != EGL14.EGL_NO_DISPLAY &&
                restoreSurface != EGL14.EGL_NO_SURFACE &&
                eglContext != EGL14.EGL_NO_CONTEXT
            ) {
                EGL14.eglMakeCurrent(eglDisplay, restoreSurface, restoreSurface, eglContext)
            }
        }
        return if (success) out else {
            out?.delete()
            null
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val encoder = codec ?: return
        if (endOfStream) {
            try {
                encoder.signalEndOfInputStream()
            } catch (_: Throwable) {
            }
        }

        val timeoutUs = if (endOfStream) 10_000L else 0L
        var loops = 0
        while (loops < 64) {
            loops++
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        val newFormat = encoder.outputFormat
                        val m = muxer ?: break
                        trackIndex = m.addTrack(newFormat)
                        m.start()
                        muxerStarted = true
                    }
                }

                outputIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted && trackIndex >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun releaseResources(eglDisplay: EGLDisplay) {
        try {
            codec?.stop()
        } catch (_: Throwable) {
        }
        try {
            codec?.release()
        } catch (_: Throwable) {
        }
        codec = null

        if (encoderEglSurface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try {
                EGL14.eglDestroySurface(eglDisplay, encoderEglSurface)
            } catch (_: Throwable) {
            }
        }
        encoderEglSurface = EGL14.EGL_NO_SURFACE

        try {
            inputSurface?.release()
        } catch (_: Throwable) {
        }
        inputSurface = null

        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (_: Throwable) {
        }
        try {
            muxer?.release()
        } catch (_: Throwable) {
        }
        muxer = null
        muxerStarted = false
        trackIndex = -1
    }

    private fun chooseRecordableConfig(eglDisplay: EGLDisplay): EGLConfig? {
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val ok = EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0)
        return if (ok && num[0] > 0) configs[0] else null
    }

    /**
     * Rounds encoder dimensions strictly to multiples of 16 for universal MediaCodec compatibility
     * while preserving the selected camera body's authentic aspect ratio.
     */
    private fun computeAlignedDimensions(aspect: Float): Pair<Int, Int> {
        val rawW: Float
        val rawH: Float
        if (aspect >= 1f) {
            val targetLong = if (aspect > 2.0f) 832f else 720f
            rawW = targetLong
            rawH = targetLong / aspect
        } else {
            rawH = 720f
            rawW = 720f * aspect
        }
        val w = ((rawW / 16f).roundToInt() * 16).coerceIn(256, 960)
        val h = ((rawH / 16f).roundToInt() * 16).coerceIn(256, 960)
        return w to h
    }
}
