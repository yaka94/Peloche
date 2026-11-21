package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import com.example.R
import com.example.domain.model.OpticalChainParams
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * Thread-safe lifecycle wrapper around an OpenGL OES SurfaceTexture so CameraX
 * never configures or writes into a released/abandoned SurfaceTexture.
 */
class CameraSurfaceHolder(val surfaceTexture: SurfaceTexture) {
    private val lock = Any()
    private var disposed = false
    private var activeCameraSurfaces = 0
    private var released = false

    @Volatile
    private var bufferWidth: Int = 1280
    @Volatile
    private var bufferHeight: Int = 960

    /**
     * Computes the true visual aspect ratio (width/height) of the camera buffer in screen space
     * for any device orientation (0°, 90°, 180°, 270°) by inspecting the SurfaceTexture matrix.
     */
    fun computeDisplayAspect(texMatrix: FloatArray): Float {
        val w = bufferWidth.coerceAtLeast(1).toFloat()
        val h = bufferHeight.coerceAtLeast(1).toFloat()
        // Column 0 of texMatrix maps screen +X into texture (u, v).
        // If |m00| >= |m01|, screen X aligns with texture U (0°/180°); otherwise rotated 90°/270°.
        val unrotated = kotlin.math.abs(texMatrix[0]) >= kotlin.math.abs(texMatrix[1])
        return if (unrotated) (w / h) else (h / w)
    }

    fun isValid(): Boolean = synchronized(lock) {
        if (disposed || released) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            released = true
            return false
        }
        true
    }

    /**
     * Atomically verifies validity, applies buffer size, and constructs a Surface for CameraX.
     * Prevents SurfaceTexture abandonment while Camera2 holds an active capture session.
     */
    fun acquireCameraSurface(width: Int, height: Int): Surface? = synchronized(lock) {
        if (disposed || released) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            released = true
            return null
        }
        return try {
            if (width > 0 && height > 0) {
                bufferWidth = width
                bufferHeight = height
            }
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)
            activeCameraSurfaces++
            surface
        } catch (_: Throwable) {
            null
        }
    }

    fun onCameraSurfaceReleased() {
        val shouldReleaseNow: Boolean
        synchronized(lock) {
            activeCameraSurfaces = (activeCameraSurfaces - 1).coerceAtLeast(0)
            shouldReleaseNow = disposed && activeCameraSurfaces == 0 && !released
            if (shouldReleaseNow) {
                released = true
            }
        }
        if (shouldReleaseNow) {
            try {
                surfaceTexture.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun markDisposedAndReleaseWhenIdle() {
        val shouldReleaseNow: Boolean
        synchronized(lock) {
            disposed = true
            shouldReleaseNow = activeCameraSurfaces == 0 && !released
            if (shouldReleaseNow) {
                released = true
            }
        }
        if (shouldReleaseNow) {
            try {
                surfaceTexture.release()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Hardware-accelerated OpenGL ES TextureView rendering EITHER live CameraX video
 * (via detached OES SurfaceTexture) OR local high-resolution Scène Témoin (Day/Night)
 * through the exact same 3-stage GLSL optical pipeline in real time.
 *
 * Uses TextureView + dedicated EGL14 HandlerThread instead of GLSurfaceView/SurfaceView so:
 * 1. Dynamic aspect-ratio changes in Compose (3:2, 1:1, 65:24, 3:4) never trigger
 *    SurfaceView BLASTBufferQueue buffer-size rejections.
 * 2. Detached SurfaceTexture(false) is instantiated off-GL-context on API 26+ before
 *    attachToGLContext(), avoiding spurious EGL_PROTECTED_CONTENT_EXT (0x32c0) queries.
 * 3. Rounded corners, bottom sheets, and dialogs clip and layer seamlessly in Compose.
 */
class AnalogGlSurfaceView(
    context: Context,
    initialParams: OpticalChainParams,
    useCameraStream: Boolean,
    private val onSurfaceTextureReady: ((CameraSurfaceHolder) -> Unit)? = null,
    private val onSurfaceTextureDestroyed: ((CameraSurfaceHolder) -> Unit)? = null
) : TextureView(context), TextureView.SurfaceTextureListener {

    private companion object {
        const val MSG_INIT_SURFACE = 1
        const val MSG_RESIZE = 2
        const val MSG_ATTACH_CAMERA = 3
        const val MSG_RENDER = 4
        const val MSG_DESTROY_SURFACE = 5
        const val MSG_RELEASE_ALL = 6
        const val MSG_ANIM_TICK = 7
        const val MSG_START_RECORDING = 8
        const val MSG_STOP_RECORDING = 9
        const val ANIM_FRAME_INTERVAL_MS = 42L
    }

    private data class StartRecordingPayload(
        val outputFile: File,
        val aspectRatio: Float,
        val targetFps: Int,
        val onStarted: (Boolean) -> Unit
    )

    private data class SurfaceInitPayload(
        val viewSurfaceTexture: SurfaceTexture,
        val width: Int,
        val height: Int,
        val detachedCameraSt: SurfaceTexture?
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isDisposed = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val paramsRef = AtomicReference(initialParams)
    private val useCameraFeed = AtomicBoolean(useCameraStream)
    private val sampleNight = AtomicBoolean(false)
    private val sampleTextureDirty = AtomicBoolean(true)
    private val animatedEffectsEnabled = AtomicBoolean(true)
    private val frameAvailable = AtomicBoolean(false)
    private val cameraAttachRequested = AtomicBoolean(false)
    private val cameraHolderRef = AtomicReference<CameraSurfaceHolder?>(null)
    private val animStartTimeMs = SystemClock.uptimeMillis()

    private val glThread = HandlerThread("PelocheGlPreview").apply { start() }
    private val glHandler = Handler(glThread.looper, ::handleGlMessage)

    // GL thread-owned state
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var boundViewSurfaceTexture: SurfaceTexture? = null

    private var programOes = 0
    private var program2d = 0
    private var oesTextureId = 0
    private var sampleTextureId = 0
    private var overlayTextureId = 0
    private var sampleBitmapWidth = 1024
    private var sampleBitmapHeight = 768
    private var viewWidth = 1
    private var viewHeight = 1
    private var frameCount = 0
    private var hasReceivedCameraFrame = false
    private val videoRecorder = Super8VideoRecorder()
    private var pendingRecordingFinishedCallback: ((File?) -> Unit)? = null

    @Volatile
    private var displayRotation: Int = Surface.ROTATION_0

    private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val displayRotMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val finalTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private val vertexCoords = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )
    private val texCoordsOes = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )
    private val texCoords2D = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertexCoords)
            position(0)
        }
    private val texBufferOes: FloatBuffer = ByteBuffer.allocateDirect(texCoordsOes.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(texCoordsOes)
            position(0)
        }
    private val texBuffer2d: FloatBuffer = ByteBuffer.allocateDirect(texCoords2D.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(texCoords2D)
            position(0)
        }

    init {
        isOpaque = true
        surfaceTextureListener = this
    }

    fun updateParams(params: OpticalChainParams) {
        paramsRef.set(params)
        scheduleRender()
    }

    fun setSampleNight(isNight: Boolean) {
        if (sampleNight.getAndSet(isNight) != isNight) {
            sampleTextureDirty.set(true)
        }
        scheduleRender()
    }

    fun setDisplayRotation(rotation: Int) {
        if (displayRotation != rotation) {
            displayRotation = rotation
            scheduleRender()
        }
    }

    fun setCameraFeedActive(active: Boolean) {
        useCameraFeed.set(active)
        if (active && onSurfaceTextureReady != null && !isDisposed.get() && isAvailable) {
            requestCameraSurfaceTextureAttachmentOnMainThread()
        }
        scheduleRender()
    }

    fun setAnimatedEffectsEnabled(enabled: Boolean) {
        if (animatedEffectsEnabled.getAndSet(enabled) != enabled) {
            if (!enabled) {
                glHandler.removeMessages(MSG_ANIM_TICK)
            }
            scheduleRender()
        }
    }

    fun startSuper8Recording(
        outputFile: File,
        aspectRatio: Float,
        targetFps: Int = 24,
        onStarted: (Boolean) -> Unit
    ) {
        if (isDisposed.get() || isPaused.get()) {
            mainHandler.post { onStarted(false) }
            return
        }
        glHandler.obtainMessage(
            MSG_START_RECORDING,
            StartRecordingPayload(outputFile, aspectRatio, targetFps, onStarted)
        ).sendToTarget()
    }

    fun stopSuper8Recording(onFinished: (File?) -> Unit) {
        if (isDisposed.get()) {
            mainHandler.post { onFinished(null) }
            return
        }
        glHandler.obtainMessage(MSG_STOP_RECORDING, onFinished).sendToTarget()
    }

    fun onResume() {
        isPaused.set(false)
        scheduleRender()
    }

    fun onPause() {
        isPaused.set(true)
        glHandler.removeMessages(MSG_ANIM_TICK)
        if (videoRecorder.isRecording) {
            glHandler.obtainMessage(MSG_STOP_RECORDING, null).sendToTarget()
        }
    }

    /**
     * Safely tears down camera & GL resources without abandoned SurfaceTexture races
     * or calls to OpenGL ES without a current context.
     */
    fun releaseSafely() {
        if (!isDisposed.compareAndSet(false, true)) return
        disposeCameraHolderOnMainThread()
        glHandler.removeCallbacksAndMessages(null)
        glHandler.sendEmptyMessage(MSG_RELEASE_ALL)
        glThread.quitSafely()
    }

    // ==================== TextureView.SurfaceTextureListener (Main Thread) ====================

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (isDisposed.get()) {
            try {
                surface.release()
            } catch (_: Throwable) {
            }
            return
        }
        val detachedCameraSt = if (useCameraFeed.get() && onSurfaceTextureReady != null) {
            cameraAttachRequested.set(true)
            createDetachedCameraSurfaceTextureOnMainThread()
        } else {
            null
        }
        display?.rotation?.let { displayRotation = it }
        glHandler.obtainMessage(
            MSG_INIT_SURFACE,
            SurfaceInitPayload(surface, width, height, detachedCameraSt)
        ).sendToTarget()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (isDisposed.get()) return
        display?.rotation?.let { displayRotation = it }
        glHandler.removeMessages(MSG_RESIZE)
        glHandler.obtainMessage(MSG_RESIZE, width, height).sendToTarget()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        disposeCameraHolderOnMainThread()
        if (!isDisposed.get()) {
            glHandler.removeMessages(MSG_RENDER)
            glHandler.removeMessages(MSG_ANIM_TICK)
            glHandler.obtainMessage(MSG_DESTROY_SURFACE, surface).sendToTarget()
            // Return false: GL thread releases `surface` right after eglDestroySurface().
            return false
        }
        // releaseSafely() already enqueued MSG_RELEASE_ALL which releases boundViewSurfaceTexture.
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    // ==================== Main Thread Helpers ====================

    private fun createDetachedCameraSurfaceTextureOnMainThread(): SurfaceTexture? {
        // Creating detached SurfaceTexture(false) on Main thread (where EGL_NO_CONTEXT is current)
        // prevents Android's SurfaceTexture_init from querying EGL_PROTECTED_CONTENT_EXT (0x32c0).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                SurfaceTexture(false)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    private fun requestCameraSurfaceTextureAttachmentOnMainThread() {
        val existing = cameraHolderRef.get()
        if (existing != null && existing.isValid()) return
        if (!cameraAttachRequested.compareAndSet(false, true)) return
        val detachedSt = createDetachedCameraSurfaceTextureOnMainThread()
        glHandler.obtainMessage(MSG_ATTACH_CAMERA, detachedSt).sendToTarget()
    }

    private fun disposeCameraHolderOnMainThread() {
        cameraAttachRequested.set(false)
        val holder = cameraHolderRef.getAndSet(null)
        if (holder != null) {
            onSurfaceTextureDestroyed?.invoke(holder)
            holder.markDisposedAndReleaseWhenIdle()
        }
    }

    private fun scheduleRender() {
        if (isDisposed.get() || isPaused.get()) return
        glHandler.removeMessages(MSG_RENDER)
        glHandler.sendEmptyMessage(MSG_RENDER)
    }

    // ==================== GL Thread Message Loop ====================

    private fun handleGlMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_INIT_SURFACE -> {
                val payload = msg.obj as? SurfaceInitPayload ?: return true
                initGlSurfaceOnGlThread(payload)
            }
            MSG_RESIZE -> {
                viewWidth = msg.arg1.coerceAtLeast(1)
                viewHeight = msg.arg2.coerceAtLeast(1)
                if (isGlReady()) {
                    GLES20.glViewport(0, 0, viewWidth, viewHeight)
                    renderFrameOnGlThread()
                }
            }
            MSG_ATTACH_CAMERA -> {
                val detachedSt = msg.obj as? SurfaceTexture
                if (isGlReady() && !isDisposed.get()) {
                    attachCameraSurfaceTextureOnGlThread(detachedSt)
                    renderFrameOnGlThread()
                } else {
                    releaseQuietly(detachedSt)
                    cameraAttachRequested.set(false)
                }
            }
            MSG_RENDER, MSG_ANIM_TICK -> {
                if (isGlReady() && !isDisposed.get() && !isPaused.get()) {
                    renderFrameOnGlThread()
                }
            }
            MSG_DESTROY_SURFACE -> {
                glHandler.removeMessages(MSG_ANIM_TICK)
                stopRecordingIfActiveOnGlThread(null)
                val surfaceToRelease = msg.obj as? SurfaceTexture
                destroyEglSurfaceOnlyOnGlThread(surfaceToRelease)
            }
            MSG_RELEASE_ALL -> {
                glHandler.removeMessages(MSG_ANIM_TICK)
                stopRecordingIfActiveOnGlThread(null)
                releaseAllGlResourcesOnGlThread()
            }
            MSG_START_RECORDING -> {
                val payload = msg.obj as? StartRecordingPayload ?: return true
                val started = if (isGlReady() && !isDisposed.get()) {
                    videoRecorder.start(
                        eglDisplay = eglDisplay,
                        fallbackConfig = eglConfig,
                        eglContext = eglContext,
                        targetFile = payload.outputFile,
                        aspectRatio = payload.aspectRatio,
                        targetFps = payload.targetFps
                    )
                } else {
                    false
                }
                if (started) {
                    scheduleRender()
                }
                mainHandler.post { payload.onStarted(started) }
            }
            MSG_STOP_RECORDING -> {
                @Suppress("UNCHECKED_CAST")
                val callback = msg.obj as? ((File?) -> Unit)
                stopRecordingIfActiveOnGlThread(callback)
            }
        }
        return true
    }

    private fun stopRecordingIfActiveOnGlThread(callback: ((File?) -> Unit)?) {
        val finishedFile = if (videoRecorder.isRecording) {
            videoRecorder.stopAndRelease(eglDisplay, eglSurface, eglContext)
        } else {
            null
        }
        val effectiveCallback = callback ?: pendingRecordingFinishedCallback
        pendingRecordingFinishedCallback = null
        if (effectiveCallback != null) {
            mainHandler.post { effectiveCallback(finishedFile) }
        }
    }

    private fun isGlReady(): Boolean =
        eglDisplay != EGL14.EGL_NO_DISPLAY &&
            eglContext != EGL14.EGL_NO_CONTEXT &&
            eglSurface != EGL14.EGL_NO_SURFACE

    private fun initGlSurfaceOnGlThread(payload: SurfaceInitPayload) {
        if (isDisposed.get()) {
            releaseQuietly(payload.detachedCameraSt)
            releaseQuietly(payload.viewSurfaceTexture)
            return
        }

        // Destroy any previous window surface cleanly
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            destroyEglSurfaceOnlyOnGlThread(boundViewSurfaceTexture)
        }

        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                releaseQuietly(payload.detachedCameraSt)
                return
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                eglDisplay = EGL14.EGL_NO_DISPLAY
                releaseQuietly(payload.detachedCameraSt)
                return
            }
        }

        if (eglConfig == null) {
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                releaseQuietly(payload.detachedCameraSt)
                return
            }
            eglConfig = configs[0]
        }

        val config = eglConfig ?: run {
            releaseQuietly(payload.detachedCameraSt)
            return
        }

        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                releaseQuietly(payload.detachedCameraSt)
                return
            }
        }

        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = try {
            EGL14.eglCreateWindowSurface(eglDisplay, config, payload.viewSurfaceTexture, surfAttribs, 0)
        } catch (_: Throwable) {
            EGL14.EGL_NO_SURFACE
        }
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            releaseQuietly(payload.detachedCameraSt)
            return
        }

        boundViewSurfaceTexture = payload.viewSurfaceTexture

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            destroyEglSurfaceOnlyOnGlThread(payload.viewSurfaceTexture)
            releaseQuietly(payload.detachedCameraSt)
            return
        }

        viewWidth = payload.width.coerceAtLeast(1)
        viewHeight = payload.height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(0.043f, 0.043f, 0.051f, 1.0f)

        ensureProgramsAndTexturesOnGlThread()

        if (payload.detachedCameraSt != null || (useCameraFeed.get() && onSurfaceTextureReady != null)) {
            attachCameraSurfaceTextureOnGlThread(payload.detachedCameraSt)
        }

        renderFrameOnGlThread()
    }

    private fun ensureProgramsAndTexturesOnGlThread() {
        if (programOes == 0) {
            programOes = OpticalShaderSource.compileProgram(
                OpticalShaderSource.VERTEX_SHADER,
                OpticalShaderSource.FRAGMENT_SHADER_OES
            )
        }
        if (program2d == 0) {
            program2d = OpticalShaderSource.compileProgram(
                OpticalShaderSource.VERTEX_SHADER,
                OpticalShaderSource.FRAGMENT_SHADER_2D
            )
        }
        if (oesTextureId == 0 || sampleTextureId == 0 || overlayTextureId == 0) {
            val texIds = IntArray(3)
            GLES20.glGenTextures(3, texIds, 0)
            oesTextureId = texIds[0]
            sampleTextureId = texIds[1]
            overlayTextureId = texIds[2]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sampleTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            uploadOverlayBitmap()
            uploadSampleBitmap()
            sampleTextureDirty.set(false)
        }
    }

    private fun attachCameraSurfaceTextureOnGlThread(detachedSt: SurfaceTexture?) {
        if (isDisposed.get() || oesTextureId == 0 || onSurfaceTextureReady == null) {
            releaseQuietly(detachedSt)
            cameraAttachRequested.set(false)
            return
        }
        val existing = cameraHolderRef.get()
        if (existing != null && existing.isValid()) {
            releaseQuietly(detachedSt)
            return
        }

        val st: SurfaceTexture = if (detachedSt != null) {
            try {
                detachedSt.attachToGLContext(oesTextureId)
                detachedSt
            } catch (_: Throwable) {
                releaseQuietly(detachedSt)
                cameraAttachRequested.set(false)
                return
            }
        } else {
            // API 24–25 fallback only
            SurfaceTexture(oesTextureId)
        }

        hasReceivedCameraFrame = false
        st.setOnFrameAvailableListener({
            frameAvailable.set(true)
            scheduleRender()
        }, glHandler)

        val newHolder = CameraSurfaceHolder(st)
        val previousHolder = cameraHolderRef.getAndSet(newHolder)
        if (previousHolder != null) {
            mainHandler.post {
                onSurfaceTextureDestroyed?.invoke(previousHolder)
                previousHolder.markDisposedAndReleaseWhenIdle()
            }
        }
        mainHandler.post {
            if (!isDisposed.get() && cameraHolderRef.get() === newHolder && newHolder.isValid()) {
                onSurfaceTextureReady.invoke(newHolder)
            } else {
                newHolder.markDisposedAndReleaseWhenIdle()
            }
        }
    }

    private fun renderFrameOnGlThread() {
        if (!isGlReady() || isDisposed.get()) return
        if (EGL14.eglGetCurrentContext() != eglContext || EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) != eglSurface) {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return
        }

        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (sampleTextureDirty.getAndSet(false)) {
            uploadSampleBitmap()
        }

        val cameraEnabled = useCameraFeed.get()
        val holder = cameraHolderRef.get()
        var drewCamera = false
        if (cameraEnabled && holder != null && holder.isValid()) {
            if (frameAvailable.compareAndSet(true, false)) {
                try {
                    holder.surfaceTexture.updateTexImage()
                    holder.surfaceTexture.getTransformMatrix(texMatrix)
                    hasReceivedCameraFrame = true
                } catch (_: Exception) {
                }
            }
            drewCamera = hasReceivedCameraFrame
        }

        val params = paramsRef.get()
        frameCount = (frameCount + 1) % 10000
        // Continuous elapsed seconds (wrapped every 10 min for highp float precision)
        // drives smooth procedural light leaks, flares, dust & 18fps grain in real time.
        val timeSeed = if (animatedEffectsEnabled.get()) {
            ((SystemClock.uptimeMillis() - animStartTimeMs) % 600_000L) / 1000.0f
        } else {
            0.42f
        }

        val shouldRecordFrame = videoRecorder.isRecording
        if (drewCamera && programOes != 0 && holder != null) {
            val rotDegrees = when (displayRotation) {
                Surface.ROTATION_90 -> -90f
                Surface.ROTATION_180 -> -180f
                Surface.ROTATION_270 -> -270f
                else -> 0f
            }
            val activeMatrix = if (rotDegrees == 0f) {
                texMatrix
            } else {
                Matrix.setIdentityM(displayRotMatrix, 0)
                Matrix.translateM(displayRotMatrix, 0, 0.5f, 0.5f, 0f)
                Matrix.rotateM(displayRotMatrix, 0, rotDegrees, 0f, 0f, 1f)
                Matrix.translateM(displayRotMatrix, 0, -0.5f, -0.5f, 0f)
                Matrix.multiplyMM(finalTexMatrix, 0, texMatrix, 0, displayRotMatrix, 0)
                finalTexMatrix
            }
            val camAspect = holder.computeDisplayAspect(activeMatrix)
            drawQuad(
                program = programOes,
                textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId = oesTextureId,
                matrix = activeMatrix,
                tBuf = texBufferOes,
                params = params,
                srcAspect = camAspect,
                timeSeed = timeSeed,
                targetWidth = viewWidth,
                targetHeight = viewHeight
            )
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            if (shouldRecordFrame) {
                val uprightParams = if (params.invertedGroundGlass) params.copy(invertedGroundGlass = false) else params
                videoRecorder.encodeFrame(eglDisplay, eglContext) { encW, encH ->
                    GLES20.glViewport(0, 0, encW, encH)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    drawQuad(
                        program = programOes,
                        textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        textureId = oesTextureId,
                        matrix = activeMatrix,
                        tBuf = texBufferOes,
                        params = uprightParams,
                        srcAspect = camAspect,
                        timeSeed = timeSeed,
                        targetWidth = encW,
                        targetHeight = encH
                    )
                }
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                GLES20.glViewport(0, 0, viewWidth, viewHeight)
            }
        } else if (!cameraEnabled && program2d != 0) {
            val sampleAspect = sampleBitmapWidth.toFloat() / sampleBitmapHeight.coerceAtLeast(1).toFloat()
            drawQuad(
                program = program2d,
                textureTarget = GLES20.GL_TEXTURE_2D,
                textureId = sampleTextureId,
                matrix = identityMatrix,
                tBuf = texBuffer2d,
                params = params,
                srcAspect = sampleAspect,
                timeSeed = timeSeed,
                targetWidth = viewWidth,
                targetHeight = viewHeight
            )
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            if (shouldRecordFrame) {
                val uprightParams = if (params.invertedGroundGlass) params.copy(invertedGroundGlass = false) else params
                videoRecorder.encodeFrame(eglDisplay, eglContext) { encW, encH ->
                    GLES20.glViewport(0, 0, encW, encH)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    drawQuad(
                        program = program2d,
                        textureTarget = GLES20.GL_TEXTURE_2D,
                        textureId = sampleTextureId,
                        matrix = identityMatrix,
                        tBuf = texBuffer2d,
                        params = uprightParams,
                        srcAspect = sampleAspect,
                        timeSeed = timeSeed,
                        targetWidth = encW,
                        targetHeight = encH
                    )
                }
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                GLES20.glViewport(0, 0, viewWidth, viewHeight)
            }
        } else {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        // Maintain silky ~24fps procedural motion in Scène Témoin / Labo / Boutique previews
        // and guarantee continuous 24fps capture whenever Super 8 video recording is active.
        glHandler.removeMessages(MSG_ANIM_TICK)
        if ((animatedEffectsEnabled.get() || videoRecorder.isRecording) &&
            !isPaused.get() && !isDisposed.get() && (drewCamera || !cameraEnabled)
        ) {
            glHandler.sendEmptyMessageDelayed(MSG_ANIM_TICK, ANIM_FRAME_INTERVAL_MS)
        }
    }

    private fun drawQuad(
        program: Int,
        textureTarget: Int,
        textureId: Int,
        matrix: FloatArray,
        tBuf: FloatBuffer,
        params: OpticalChainParams,
        srcAspect: Float,
        timeSeed: Float,
        targetWidth: Int = viewWidth,
        targetHeight: Int = viewHeight
    ) {
        GLES20.glUseProgram(program)

        val viewAspect = targetWidth.toFloat() / targetHeight.coerceAtLeast(1).toFloat()
        var cropX = 1f
        var cropY = 1f
        if (viewAspect > srcAspect) {
            cropY = srcAspect / viewAspect
        } else {
            cropX = viewAspect / srcAspect
        }

        OpticalShaderSource.bindOpticalUniforms(
            program = program,
            params = params,
            surfaceWidth = targetWidth,
            surfaceHeight = targetHeight,
            cropScaleX = cropX,
            cropScaleY = cropY,
            timeSeed = timeSeed
        )

        val uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, matrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uOverlayTex"), 1)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uHasOverlay"), 1.0f)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tBuf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun uploadSampleBitmap() {
        val resId = if (sampleNight.get()) R.drawable.img_sample_scene_night else R.drawable.img_sample_scene_day
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeResource(appContext.resources, resId, opts) ?: return
        sampleBitmapWidth = bmp.width
        sampleBitmapHeight = bmp.height
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sampleTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
    }

    private fun uploadOverlayBitmap() {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeResource(appContext.resources, R.drawable.img_optical_flare_overlay, opts) ?: return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
    }

    private fun destroyEglSurfaceOnlyOnGlThread(surfaceToRelease: SurfaceTexture?) {
        hasReceivedCameraFrame = false
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
        }
        val bound = boundViewSurfaceTexture
        boundViewSurfaceTexture = null
        releaseQuietly(surfaceToRelease ?: bound)
    }

    private fun releaseAllGlResourcesOnGlThread() {
        hasReceivedCameraFrame = false
        if (isGlReady()) {
            if (EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                val texIds = intArrayOf(oesTextureId, sampleTextureId, overlayTextureId).filter { it != 0 }.toIntArray()
                if (texIds.isNotEmpty()) {
                    GLES20.glDeleteTextures(texIds.size, texIds, 0)
                }
                if (programOes != 0) GLES20.glDeleteProgram(programOes)
                if (program2d != 0) GLES20.glDeleteProgram(program2d)
            }
        }
        oesTextureId = 0
        sampleTextureId = 0
        overlayTextureId = 0
        programOes = 0
        program2d = 0

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        eglConfig = null
        EGL14.eglReleaseThread()

        val bound = boundViewSurfaceTexture
        boundViewSurfaceTexture = null
        releaseQuietly(bound)
    }

    private fun releaseQuietly(st: SurfaceTexture?) {
        if (st == null) return
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !st.isReleased) {
                st.release()
            }
        } catch (_: Throwable) {
        }
    }
}
