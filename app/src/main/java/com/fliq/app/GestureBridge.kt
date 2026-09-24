package com.fliq.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * A simple singleton bridge to pass gesture events from the Camera Service to the Accessibility Service.
 */
object GestureBridge {
    private var listener: ((String, List<NormalizedLandmark>) -> Unit)? = null

    fun setAccessibilityListener(l: (String, List<NormalizedLandmark>) -> Unit) {
        listener = l
    }

    fun removeAccessibilityListener() {
        listener = null
    }

    fun onGestureDetected(gesture: String, landmarks: List<NormalizedLandmark>) {
        listener?.invoke(gesture, landmarks)
    }
}
