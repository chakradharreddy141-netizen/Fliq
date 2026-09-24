package com.fliq.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fliq.app.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), HandTrackingHelper.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var handTrackingHelper: HandTrackingHelper? = null

    // FPS tracking
    private var frameCount = 0
    private var lastFpsTime = SystemClock.elapsedRealtime()
    private var currentFps = 0

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupPhase2Buttons()
    }

    override fun onStart() {
        super.onStart()
        // Ensure background service releases the camera when app is visible
        stopService(Intent(this, GestureForegroundService::class.java))
        
        // Re-bind camera if permissions exist, since background service might have held it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    private fun setupPhase2Buttons() {
        binding.btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStartService.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Start the background camera service
            val serviceIntent = Intent(this, GestureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // Prompt user to enable the Accessibility Service for Fliq
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(accessibilityIntent)
            
            Toast.makeText(this, "Enable Fliq in Accessibility Settings!", Toast.LENGTH_LONG).show()
            
            // Finish MainActivity so the background service can use the camera
            finish()
        }

        binding.btnStopService.setOnClickListener {
            stopService(Intent(this, GestureForegroundService::class.java))
            Toast.makeText(this, "Background Service Stopped", Toast.LENGTH_SHORT).show()
            
            // Re-bind camera so preview works again in the main app
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            }
        }
    }

    private fun startCamera() {
        handTrackingHelper = HandTrackingHelper(this, this)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Camera preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // Frame analysis - RGBA format for easy Bitmap conversion
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

            // Use front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                updateStatus("Fliq ready - show your hand")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                updateStatus("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Called by HandTrackingHelper when a gesture is recognized
    override fun onResults(result: GestureRecognizerResult, inputImageWidth: Int, inputImageHeight: Int) {
        // Update FPS counter
        frameCount++
        val now = SystemClock.elapsedRealtime()
        if (now - lastFpsTime >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTime = now
        }

        runOnUiThread {
            // Draw hand landmarks on overlay
            binding.overlayView.setResults(result, inputImageWidth, inputImageHeight)

            // Show detected gesture
            val gestures = result.gestures()
            if (gestures.isNotEmpty() && gestures[0].isNotEmpty()) {
                val topGesture = gestures[0][0]
                val label = topGesture.categoryName()
                val confidence = (topGesture.score() * 100).toInt()

                binding.tvGesture.text = "$label ${confidence}%"
                binding.tvGesture.visibility = View.VISIBLE
                updateStatus("FPS: $currentFps | Hands: ${result.landmarks().size}")
            } else {
                binding.tvGesture.visibility = View.GONE
                updateStatus("FPS: $currentFps | No hand detected")
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread { updateStatus("Error: $error") }
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handTrackingHelper?.close()
    }

    companion object {
        private const val TAG = "FliqMain"
    }
}
