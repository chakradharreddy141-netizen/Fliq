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

class FliqAccessibilityService : AccessibilityService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isScrolling = false
    private var lastScrollTime = 0L
    private var isFliqPaused = false
    private var lastToggleTime = 0L
    private var isTorchOn = false
    private var lastLoggedGesture = ""

    // Custom Toast Overlay
    private var toastView: android.widget.TextView? = null
    private var toastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideToastRunnable = Runnable { toastView?.visibility = android.view.View.GONE }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        GestureBridge.setAccessibilityListener { gesture, landmarks -> handleGesture(gesture, landmarks) }
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
                Log.e(TAG, "Failed to show toast: ${e.message}")
            }
        }
    }

    private fun handleGesture(gesture: String, landmarks: List<NormalizedLandmark>) {
        if (gesture != "None" && gesture != lastLoggedGesture) {
            Log.d(TAG, "Detected gesture: $gesture")
            lastLoggedGesture = gesture
        }
        if (gesture == "None") return

        val now = SystemClock.uptimeMillis()

        // Map gesture string to preference key
        val prefKey = when (gesture) {
            "ILoveYou" -> GesturePreferences.KEY_I_LOVE_YOU
            "Thumb_Up" -> GesturePreferences.KEY_THUMB_UP
            "Thumb_Down" -> GesturePreferences.KEY_THUMB_DOWN
            "Closed_Fist" -> GesturePreferences.KEY_CLOSED_FIST
            "Open_Palm" -> GesturePreferences.KEY_OPEN_PALM
            "Victory" -> GesturePreferences.KEY_VICTORY
            "Pointing_Up" -> GesturePreferences.KEY_POINTING_UP
            else -> return
        }

        // Get configured action for this gesture
        val defaultAction = when (prefKey) {
            GesturePreferences.KEY_I_LOVE_YOU -> GesturePreferences.ACTION_PAUSE_RESUME
            GesturePreferences.KEY_THUMB_UP -> GesturePreferences.ACTION_SCROLL_UP
            GesturePreferences.KEY_THUMB_DOWN -> GesturePreferences.ACTION_SCROLL_DOWN
            GesturePreferences.KEY_CLOSED_FIST -> GesturePreferences.ACTION_BACK
            GesturePreferences.KEY_OPEN_PALM -> GesturePreferences.ACTION_HOME
            GesturePreferences.KEY_VICTORY -> GesturePreferences.ACTION_OPEN_WHATSAPP
            GesturePreferences.KEY_POINTING_UP -> GesturePreferences.ACTION_OPEN_INSTAGRAM
            else -> return
        }
        
        val action = GesturePreferences.getActionForGesture(this, prefKey, defaultAction)

        // Always allow pause/resume even if app is paused
        if (action == GesturePreferences.ACTION_PAUSE_RESUME) {
            if (now - lastToggleTime > 2000) {
                lastToggleTime = now
                isFliqPaused = !isFliqPaused
                val status = if (isFliqPaused) "Paused" else "Resumed"
                val emoji = getEmojiForGesture(gesture)
                showToast("Fliq $status $emoji")
            }
            return
        }

        if (isFliqPaused) return

        // Set cooldown based on action type
        val cooldown = if (action.startsWith("scroll_")) 1000L else 3000L

        if (now - lastScrollTime > cooldown) {
            lastScrollTime = now
            val emoji = getEmojiForGesture(gesture)
            val label = GesturePreferences.getActionLabel(action)
            
            showToast("$label $emoji")
            
            executeAction(action)
        }
    }

    private fun getEmojiForGesture(gesture: String): String {
        return when (gesture) {
            "ILoveYou" -> "\uD83E\uDD1F"
            "Thumb_Up" -> "\uD83D\uDC4D"
            "Thumb_Down" -> "\uD83D\uDC4E"
            "Closed_Fist" -> "\u270A"
            "Open_Palm" -> "\uD83D\uDD90\uFE0F"
            "Victory" -> "\u270C\uFE0F"
            "Pointing_Up" -> "\u261D\uFE0F"
            else -> ""
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            GesturePreferences.ACTION_OPEN_WHATSAPP -> openApp("com.whatsapp")
            GesturePreferences.ACTION_OPEN_INSTAGRAM -> openApp("com.instagram.android")
            GesturePreferences.ACTION_OPEN_YOUTUBE -> openApp("com.google.android.youtube")
            GesturePreferences.ACTION_OPEN_CAMERA -> {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_CHROME -> openApp("com.android.chrome")
            GesturePreferences.ACTION_OPEN_SPOTIFY -> openApp("com.spotify.music")
            GesturePreferences.ACTION_OPEN_MAPS -> openApp("com.google.android.apps.maps")
            GesturePreferences.ACTION_OPEN_PHONE -> {
                val intent = Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_MESSAGES -> {
                val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Failed: ${e.message}") }
            }
            GesturePreferences.ACTION_OPEN_SETTINGS -> {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                startActivity(intent)
            }
            GesturePreferences.ACTION_TOGGLE_TORCH -> toggleTorch()
            GesturePreferences.ACTION_SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                else showToast("Screenshot requires Android 9+")
            }
            GesturePreferences.ACTION_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GesturePreferences.ACTION_NEXT_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            GesturePreferences.ACTION_PREV_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            GesturePreferences.ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GesturePreferences.ACTION_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GesturePreferences.ACTION_RECENT_APPS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GesturePreferences.ACTION_LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                else showToast("Lock Screen requires Android 9+")
            }
            GesturePreferences.ACTION_GOOGLE_ASSISTANT -> {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Failed: ${e.message}") }
            }
            GesturePreferences.ACTION_VOLUME_UP -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            GesturePreferences.ACTION_VOLUME_DOWN -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            GesturePreferences.ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GesturePreferences.ACTION_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GesturePreferences.ACTION_SCROLL_UP -> performScroll(0f, 1f)
            GesturePreferences.ACTION_SCROLL_DOWN -> performScroll(0f, -1f)
            GesturePreferences.ACTION_SCROLL_LEFT -> performScroll(1f, 0f)
            GesturePreferences.ACTION_SCROLL_RIGHT -> performScroll(-1f, 0f)
        }
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            showToast("App not installed")
        }
    }

    private fun toggleTorch() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
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

    // Scroll by specifying direction vectors (-1 to 1)
    private fun performScroll(dirX: Float, dirY: Float) {
        if (isScrolling) return
        isScrolling = true

        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        
        // Distance is 1/3 of the screen dimension
        val distY = metrics.heightPixels / 3f
        val distX = metrics.widthPixels / 3f

        // Start opposite to direction to scroll "towards" direction
        val startX = centerX - (dirX * distX / 2)
        val startY = centerY - (dirY * distY / 2)
        val endX = centerX + (dirX * distX / 2)
        val endY = centerY + (dirY * distY / 2)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val builder = GestureDescription.Builder().addStroke(stroke)

        dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                coroutineScope.launch { delay(100); isScrolling = false }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                isScrolling = false
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        GestureBridge.removeAccessibilityListener()
    }

    companion object {
        private const val TAG = "FliqAccessibility"
    }
}
