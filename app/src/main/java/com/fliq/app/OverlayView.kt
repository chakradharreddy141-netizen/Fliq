package com.fliq.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

/**
 * Custom View that draws hand landmarks and connections on top of the camera preview.
 * Coordinates are normalized (0..1) by MediaPipe, scaled to view dimensions here.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results: GestureRecognizerResult? = null
    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val landmarkPaint = Paint().apply {
        color = Color.parseColor("#00FF88")
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.parseColor("#00CC66")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun setResults(result: GestureRecognizerResult, imgWidth: Int, imgHeight: Int) {
        results = result
        imageWidth = imgWidth
        imageHeight = imgHeight

        // Calculate scale to fill the view while maintaining aspect ratio
        val viewRatio = width.toFloat() / height.toFloat()
        val imageRatio = imgWidth.toFloat() / imgHeight.toFloat()
        if (viewRatio > imageRatio) {
            scaleFactor = width.toFloat() / imgWidth.toFloat()
            offsetX = 0f
            offsetY = (height - imgHeight * scaleFactor) / 2f
        } else {
            scaleFactor = height.toFloat() / imgHeight.toFloat()
            offsetX = (width - imgWidth * scaleFactor) / 2f
            offsetY = 0f
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val res = results ?: return

        for (landmarks in res.landmarks()) {
            // Draw connections between landmarks
            for ((start, end) in HAND_CONNECTIONS) {
                if (start < landmarks.size && end < landmarks.size) {
                    val p1 = landmarks[start]
                    val p2 = landmarks[end]
                    canvas.drawLine(
                        p1.x() * imageWidth * scaleFactor + offsetX,
                        p1.y() * imageHeight * scaleFactor + offsetY,
                        p2.x() * imageWidth * scaleFactor + offsetX,
                        p2.y() * imageHeight * scaleFactor + offsetY,
                        connectionPaint
                    )
                }
            }

            // Draw landmark points
            for (landmark in landmarks) {
                canvas.drawCircle(
                    landmark.x() * imageWidth * scaleFactor + offsetX,
                    landmark.y() * imageHeight * scaleFactor + offsetY,
                    6f,
                    landmarkPaint
                )
            }
        }
    }

    companion object {
        // MediaPipe hand landmark connections (21 landmarks, standard topology)
        private val HAND_CONNECTIONS = listOf(
            // Thumb
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            // Index
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            // Middle
            0 to 9, 9 to 10, 10 to 11, 11 to 12,
            // Ring
            0 to 13, 13 to 14, 14 to 15, 15 to 16,
            // Pinky
            0 to 17, 17 to 18, 18 to 19, 19 to 20,
            // Palm connections
            5 to 9, 9 to 13, 13 to 17
        )
    }
}
