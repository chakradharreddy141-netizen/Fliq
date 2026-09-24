package com.fliq.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FliqAccessibilityService : AccessibilityService() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isScrolling = false
    private var lastScrollTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        
        // Listen to events from our Gesture engine
        GestureBridge.setAccessibilityListener { gesture, landmarks ->
            handleGesture(gesture, landmarks)
        }
    }

    private var isFliqPaused = false
    private var lastToggleTime = 0L

    // Custom Toast Overlay for reliable background display on strict OS (Realme/Oppo)
    private var toastView: android.widget.TextView? = null
    private var toastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideToastRunnable = Runnable { toastView?.visibility = android.view.View.GONE }

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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            else
                                android.view.WindowManager.LayoutParams.TYPE_PHONE,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            android.graphics.PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                            y = 200 // Offset from bottom
                        }
                        
                        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
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

    private var lastLoggedGesture = ""

    private fun handleGesture(gesture: String, landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) {
        if (gesture != "None" && gesture != lastLoggedGesture) {
            Log.d(TAG, "Detected new gesture: $gesture")
            lastLoggedGesture = gesture
        }

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

        when (gesture) {
            "Victory" -> {
                if (now - lastScrollTime > 3000) {
                    lastScrollTime = now
                    showToast("Opening WhatsApp ✌️")
                    openWhatsApp()
                }
            }
            "Thumb_Up" -> {
                if (now - lastScrollTime > 1000) { // Slower scroll cooldown (1 second)
                    lastScrollTime = now
                    showToast("Scrolling Up 👍")
                    performScroll(scrollUp = true)
                }
            }
            "Thumb_Down" -> {
                if (now - lastScrollTime > 1000) { // Slower scroll cooldown (1 second)
                    lastScrollTime = now
                    showToast("Scrolling Down 👎")
                    performScroll(scrollUp = false)
                }
            }
            "Closed_Fist" -> {
                if (now - lastScrollTime > 2000) {
                    lastScrollTime = now
                    showToast("Going Back ✊")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            "Open_Palm" -> {
                if (now - lastScrollTime > 2000) {
                    lastScrollTime = now
                    showToast("Going Home 🖐️")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun openWhatsApp() {
        Log.d(TAG, "Opening WhatsApp...")
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Log.e(TAG, "WhatsApp not installed")
        }
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
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        gestureBuilder.addStroke(stroke)

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                coroutineScope.launch {
                    delay(100) // Small delay before allowing next scroll
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
