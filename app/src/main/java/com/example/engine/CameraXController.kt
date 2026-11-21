package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.domain.model.CameraFlashMode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Manages Android CameraX lifecycle, binding live camera frames directly into our
 * OpenGL ES CameraSurfaceHolder, plus full-resolution ImageCapture, Tap-to-Focus,
 * AE/AF lock, Exposure Compensation (EV), and Zoom control.
 */
class CameraXController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var currentFlashMode: CameraFlashMode = CameraFlashMode.OFF
    private var isBound = false
    private val bindTokenGenerator = AtomicLong(0L)

    fun bindToSurfaceHolder(
        lifecycleOwner: LifecycleOwner,
        surfaceHolder: CameraSurfaceHolder,
        isFrontCamera: Boolean,
        targetRotation: Int = android.view.Surface.ROTATION_0,
        onBindResult: (Boolean) -> Unit
    ) {
        if (!surfaceHolder.isValid()) {
            onBindResult(false)
            return
        }

        val myToken = bindTokenGenerator.incrementAndGet()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Abort cleanly if unbind() or a newer bind superseded this async request,
            // or if the SurfaceTexture was already disposed while waiting for ProcessCameraProvider.
            if (bindTokenGenerator.get() != myToken || !surfaceHolder.isValid()) {
                return@addListener
            }

            try {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                val selector = if (isFrontCamera) {
                    if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                } else {
                    if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                }

                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                preview.setSurfaceProvider(mainExecutor) { request ->
                    if (bindTokenGenerator.get() != myToken) {
                        request.willNotProvideSurface()
                        return@setSurfaceProvider
                    }
                    val size = request.resolution
                    val surface = surfaceHolder.acquireCameraSurface(size.width, size.height)
                    if (surface == null) {
                        request.willNotProvideSurface()
                        return@setSurfaceProvider
                    }
                    request.provideSurface(surface, mainExecutor) {
                        try {
                            surface.release()
                        } catch (_: Throwable) {
                        }
                        surfaceHolder.onCameraSurfaceReleased()
                    }
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(toCameraXFlashMode(currentFlashMode))
                    .setTargetRotation(targetRotation)
                    .build()

                previewUseCase = preview
                imageCapture = capture
                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                isBound = true
                onBindResult(true)
            } catch (_: Throwable) {
                isBound = false
                onBindResult(false)
            }
        }, mainExecutor)
    }

    fun setFlashMode(mode: CameraFlashMode) {
        currentFlashMode = mode
        try {
            imageCapture?.flashMode = toCameraXFlashMode(mode)
        } catch (_: Throwable) {
        }
    }

    private fun toCameraXFlashMode(mode: CameraFlashMode): Int = when (mode) {
        CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
    }

    fun updateTargetRotation(rotation: Int) {
        try {
            previewUseCase?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
        } catch (_: Throwable) {
        }
    }

    fun setExposureCompensationEv(ev: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val step = state.exposureCompensationStep.toFloat()
        if (step <= 0f) return
        val index = (ev / step).roundToInt()
            .coerceIn(state.exposureCompensationRange.lower, state.exposureCompensationRange.upper)
        cam.cameraControl.setExposureCompensationIndex(index)
    }

    fun setZoomRatio(zoom: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = zoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun tapToFocus(normX: Float, normY: Float, lockAeAf: Boolean) {
        val cam = camera ?: return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val point = factory.createPoint(normX.coerceIn(0.05f, 0.95f), normY.coerceIn(0.05f, 0.95f))
            val builder = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
            if (lockAeAf) {
                builder.disableAutoCancel()
            } else {
                builder.setAutoCancelDuration(4, TimeUnit.SECONDS)
            }
            cam.cameraControl.startFocusAndMetering(builder.build())
        } catch (_: Throwable) {
        }
    }

    fun unlockAeAf() {
        try {
            camera?.cameraControl?.cancelFocusAndMetering()
        } catch (_: Throwable) {
        }
    }

    /**
     * Captures a full-resolution Bitmap from the hardware sensor via CameraX ImageCapture.
     * Returns null if camera is not bound or capture fails (allowing seamless sample fallback).
     */
    suspend fun captureBitmap(isFrontCamera: Boolean): Bitmap? {
        val capture = imageCapture ?: return null
        if (!isBound) return null
        val executor = ContextCompat.getMainExecutor(context)
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = imageProxyToBitmap(image, isFrontCamera)
                        image.close()
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy, mirrorHorizontal: Boolean): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0 && !mirrorHorizontal) return raw
            val matrix = Matrix().apply {
                if (rotation != 0) postRotate(rotation.toFloat())
                if (mirrorHorizontal) postScale(-1f, 1f, raw.width / 2f, raw.height / 2f)
            }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (rotated !== raw) raw.recycle()
            rotated
        } catch (_: Throwable) {
            null
        }
    }

    fun unbind() {
        bindTokenGenerator.incrementAndGet()
        isBound = false
        try {
            cameraProvider?.unbindAll()
        } catch (_: Throwable) {
        }
        camera = null
        previewUseCase = null
        imageCapture = null
    }
}
