package com.fliq.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class FliqAccessibilityService : AccessibilityService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isScrolling = false
    private var lastScrollTime = 0L
    private var isFliqPaused = false
    private var lastToggleTime = 0L
    private var isTorchOn = false
    private var lastLoggedGesture = ""

    // Custom Toast Overlay for reliable background display on strict OS (Realme/Oppo)
    private var toastView: android.widget.TextView? = null
    private var toastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideToastRunnable = Runnable { toastView?.visibility = android.view.View.GONE }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")

        GestureBridge.setAccessibilityListener { gesture, landmarks ->
            handleGesture(gesture, landmarks)
        }
    }

    private fun showToast(message: String) {
        toastHandler.post {
            try {
                if (toastView == null) {
                    toastView = android.widget.TextView(this).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
                        setTextColor(android.graphics.Color.WHITE)
                        setPadding(40, 20, 40, 20)
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER

                        val layoutParams = android.view.WindowManager.LayoutParams(
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            else
                                @Suppress("DEPRECATION")
                                android.view.WindowManager.LayoutParams.TYPE_PHONE,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            android.graphics.PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                            y = 200
                        }

                        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                        wm.addView(this, layoutParams)
                    }
                }

                toastView?.text = message
                toastView?.visibility = android.view.View.VISIBLE

                toastHandler.removeCallbacks(hideToastRunnable)
                toastHandler.postDelayed(hideToastRunnable, 2000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show custom toast: ${e.message}")
            }
        }
    }

    // Pinch detection: thumb tip (4) close to index tip (8)
    private fun isPinch(landmarks: List<NormalizedLandmark>): Boolean {
        if (landmarks.size < 21) return false
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val dx = thumbTip.x() - indexTip.x()
        val dy = thumbTip.y() - indexTip.y()
        val distance = sqrt((dx * dx + dy * dy).toDouble())
        return distance < 0.06
    }

    private fun handleGesture(gesture: String, landmarks: List<NormalizedLandmark>) {
        if (gesture != "None" && gesture != lastLoggedGesture) {
            Log.d(TAG, "Detected new gesture: $gesture")
            lastLoggedGesture = gesture
        }

        // ILoveYou toggle always works (even when paused)
        if (gesture == "ILoveYou") {
            val now = SystemClock.uptimeMillis()
            if (now - lastToggleTime > 2000) {
                lastToggleTime = now
                isFliqPaused = !isFliqPaused
                val status = if (isFliqPaused) "Paused" else "Resumed"
                showToast("Fliq $status \uD83E\uDD1F")
            }
            return
        }

        if (isFliqPaused) return

        val now = SystemClock.uptimeMillis()

        // Check pinch first (landmark-based, not a built-in MediaPipe gesture)
        // Only trigger if MediaPipe didn't classify it as Closed_Fist to avoid conflicts
        if (isPinch(landmarks) && gesture != "Closed_Fist") {
            if (now - lastScrollTime > 3000) {
                lastScrollTime = now
                val action = GesturePreferences.getPinchAction(this)
                showToast("${GesturePreferences.getActionLabel(action)} \uD83E\uDD0F")
                executeAction(action)
            }
            return
        }

        when (gesture) {
            "Victory" -> {
                if (now - lastScrollTime > 3000) {
                    lastScrollTime = now
                    val action = GesturePreferences.getVictoryAction(this)
                    showToast("${GesturePreferences.getActionLabel(action)} \u270C\uFE0F")
                    executeAction(action)
                }
            }
            "Pointing_Up" -> {
                if (now - lastScrollTime > 3000) {
                    lastScrollTime = now
                    val action = GesturePreferences.getPointingUpAction(this)
                    showToast("${GesturePreferences.getActionLabel(action)} \u261D\uFE0F")
                    executeAction(action)
                }
            }
            "Thumb_Up" -> {
                if (now - lastScrollTime > 1000) {
                    lastScrollTime = now
                    showToast("Scrolling Up \uD83D\uDC4D")
                    performScroll(scrollUp = true)
                }
            }
            "Thumb_Down" -> {
                if (now - lastScrollTime > 1000) {
                    lastScrollTime = now
                    showToast("Scrolling Down \uD83D\uDC4E")
                    performScroll(scrollUp = false)
                }
            }
            "Closed_Fist" -> {
                if (now - lastScrollTime > 2000) {
                    lastScrollTime = now
                    showToast("Going Back \u270A")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            "Open_Palm" -> {
                if (now - lastScrollTime > 2000) {
                    lastScrollTime = now
                    showToast("Going Home \uD83D\uDD90\uFE0F")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            GesturePreferences.ACTION_OPEN_WHATSAPP -> openApp("com.whatsapp")
            GesturePreferences.ACTION_OPEN_INSTAGRAM -> openApp("com.instagram.android")
            GesturePreferences.ACTION_OPEN_YOUTUBE -> openApp("com.google.android.youtube")
            GesturePreferences.ACTION_OPEN_CAMERA -> {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Camera launch failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_CHROME -> openApp("com.android.chrome")
            GesturePreferences.ACTION_OPEN_SPOTIFY -> openApp("com.spotify.music")
            GesturePreferences.ACTION_OPEN_MAPS -> openApp("com.google.android.apps.maps")
            GesturePreferences.ACTION_OPEN_PHONE -> {
                val intent = Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Phone launch failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_MESSAGES -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Messages launch failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_SETTINGS -> {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                startActivity(intent)
            }
            GesturePreferences.ACTION_TOGGLE_TORCH -> toggleTorch()
            GesturePreferences.ACTION_SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    showToast("Screenshot requires Android 9+")
                }
            }
            GesturePreferences.ACTION_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GesturePreferences.ACTION_NEXT_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            GesturePreferences.ACTION_PREV_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            GesturePreferences.ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GesturePreferences.ACTION_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GesturePreferences.ACTION_RECENT_APPS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GesturePreferences.ACTION_LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    showToast("Lock Screen requires Android 9+")
                }
            }
            GesturePreferences.ACTION_GOOGLE_ASSISTANT -> {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Assistant launch failed: ${e.message}") }
            }
            GesturePreferences.ACTION_VOLUME_UP -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            GesturePreferences.ACTION_VOLUME_DOWN -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun openApp(packageName: String) {
        Log.d(TAG, "Opening $packageName...")
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            showToast("App not installed")
            Log.e(TAG, "$packageName not installed")
        }
    }

    private fun toggleTorch() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
            } else {
                showToast("No flashlight available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle failed: ${e.message}")
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun performScroll(scrollUp: Boolean) {
        if (isScrolling) return

        isScrolling = true

        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f
        val scrollDistance = displayMetrics.heightPixels / 3f

        val startY = if (scrollUp) centerY - (scrollDistance / 2) else centerY + (scrollDistance / 2)
        val endY = if (scrollUp) centerY + (scrollDistance / 2) else centerY - (scrollDistance / 2)

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                coroutineScope.launch {
                    delay(100)
                    isScrolling = false
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                isScrolling = false
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, we only trigger actions based on camera input
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        GestureBridge.removeAccessibilityListener()
    }

    companion object {
        private const val TAG = "FliqAccessibility"
    }
}
