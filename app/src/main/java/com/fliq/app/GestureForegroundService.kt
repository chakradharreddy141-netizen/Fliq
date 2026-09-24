package com.fliq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureForegroundService : LifecycleService(), HandTrackingHelper.Listener {

    private lateinit var cameraExecutor: ExecutorService
    private var handTrackingHelper: HandTrackingHelper? = null
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        handTrackingHelper = HandTrackingHelper(this, this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Critical for Android 14+ camera background access:
        // We must draw a small overlay window, otherwise Android kills camera access 
        // when the main activity goes to the background.
        setupOverlayWindow()
        
        startCamera()
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = View(this).apply {
            // Invisible, 1x1 pixel view just to keep the camera alive
            alpha = 0f
        }

        val layoutParams = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window. Ensure SYSTEM_ALERT_WINDOW permission is granted.", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // We do NOT bind a Preview use case because this is running in the background.
            // We only need ImageAnalysis.
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(480, 360))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        handTrackingHelper?.detectLiveStream(imageProxy, isFrontCamera = true)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                // LifecycleService provides the lifecycle for CameraX
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                Log.d(TAG, "Background camera bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed in background", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(result: GestureRecognizerResult, inputImageWidth: Int, inputImageHeight: Int) {
        val gestures = result.gestures()
        val gestureName = if (gestures.isNotEmpty() && gestures[0].isNotEmpty()) {
            gestures[0][0].categoryName()
        } else {
            "None"
        }

        val landmarks = if (result.landmarks().isNotEmpty()) {
            result.landmarks()[0]
        } else {
            emptyList()
        }

        // Bridge the result over to the Accessibility Service
        GestureBridge.onGestureDetected(gestureName, landmarks)
    }

    override fun onError(error: String) {
        Log.e(TAG, "HandTrackingError: $error")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fliq Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Fliq active to detect gestures"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fliq is Active")
            .setContentText("Listening for hand gestures...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handTrackingHelper?.close()
        
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    // Returning super.onBind is required for LifecycleService
    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        private const val TAG = "FliqForeground"
        private const val CHANNEL_ID = "fliq_service_channel"
        private const val NOTIFICATION_ID = 141
    }
}
