package com.fliq.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

/**
 * Wraps MediaPipe GestureRecognizer: converts camera frames to MPImage,
 * runs inference, and reports results back via [Listener].
 */
class HandTrackingHelper(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onResults(result: GestureRecognizerResult, inputImageWidth: Int, inputImageHeight: Int)
        fun onError(error: String)
    }

    private var gestureRecognizer: GestureRecognizer? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .setDelegate(Delegate.GPU) // GPU for battery efficiency; falls back to CPU
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(1) // Single hand for now (saves compute)
                .setResultListener(::onResult)
                .setErrorListener { e -> listener.onError(e.message ?: "MediaPipe error") }
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
            Log.d(TAG, "GestureRecognizer initialized (GPU delegate)")
        } catch (e: Exception) {
            // GPU delegate might fail on some devices; retry with CPU
            Log.w(TAG, "GPU delegate failed, falling back to CPU", e)
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("gesture_recognizer.task")
                    .setDelegate(Delegate.CPU)
                    .build()

                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setNumHands(1)
                    .setResultListener(::onResult)
                    .setErrorListener { e2 -> listener.onError(e2.message ?: "MediaPipe error") }
                    .build()

                gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
                Log.d(TAG, "GestureRecognizer initialized (CPU fallback)")
            } catch (e2: Exception) {
                listener.onError("Failed to init MediaPipe: ${e2.message}")
            }
        }
    }

    /**
     * Process a camera frame. Called from CameraX ImageAnalysis analyzer thread.
     * Converts ImageProxy (RGBA_8888) to a rotated Bitmap, then to MPImage for inference.
     */
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val recognizer = gestureRecognizer ?: run {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        // Convert RGBA_8888 ImageProxy to Bitmap
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use {
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        }

        // Rotate + mirror for front camera
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        try {
            recognizer.recognizeAsync(mpImage, frameTime)
        } catch (e: Exception) {
            // Can happen if frames arrive faster than inference; safe to ignore
            Log.v(TAG, "Frame skipped: ${e.message}")
        }
    }

    private fun onResult(result: GestureRecognizerResult, input: com.google.mediapipe.framework.image.MPImage) {
        listener.onResults(result, input.width, input.height)
    }

    fun close() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    companion object {
        private const val TAG = "FliqHandTracking"
    }
}
