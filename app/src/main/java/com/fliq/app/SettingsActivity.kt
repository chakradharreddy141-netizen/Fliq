package com.fliq.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        refreshLabels()

        setupRow(R.id.rowILoveYou, "I Love You", GesturePreferences.KEY_I_LOVE_YOU)
        setupRow(R.id.rowThumbUp, "Thumb Up", GesturePreferences.KEY_THUMB_UP)
        setupRow(R.id.rowThumbDown, "Thumb Down", GesturePreferences.KEY_THUMB_DOWN)
        setupRow(R.id.rowClosedFist, "Closed Fist", GesturePreferences.KEY_CLOSED_FIST)
        setupRow(R.id.rowOpenPalm, "Open Palm", GesturePreferences.KEY_OPEN_PALM)
        setupRow(R.id.rowVictory, "Victory", GesturePreferences.KEY_VICTORY)
        setupRow(R.id.rowPointingUp, "Pointing Up", GesturePreferences.KEY_POINTING_UP)
    }

    private fun setupRow(rowId: Int, gestureName: String, prefKey: String) {
        findViewById<android.view.View>(rowId).setOnClickListener {
            showActionPicker(gestureName) { action ->
                GesturePreferences.setActionForGesture(this, prefKey, action)
                refreshLabels()
            }
        }
    }

    private fun refreshLabels() {
        findViewById<TextView>(R.id.tvILoveYouAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_I_LOVE_YOU, GesturePreferences.ACTION_PAUSE_RESUME))
            
        findViewById<TextView>(R.id.tvThumbUpAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_THUMB_UP, GesturePreferences.ACTION_SCROLL_UP))
            
        findViewById<TextView>(R.id.tvThumbDownAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_THUMB_DOWN, GesturePreferences.ACTION_SCROLL_DOWN))
            
        findViewById<TextView>(R.id.tvClosedFistAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_CLOSED_FIST, GesturePreferences.ACTION_BACK))
            
        findViewById<TextView>(R.id.tvOpenPalmAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_OPEN_PALM, GesturePreferences.ACTION_HOME))
            
        findViewById<TextView>(R.id.tvVictoryAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_VICTORY, GesturePreferences.ACTION_OPEN_WHATSAPP))
            
        findViewById<TextView>(R.id.tvPointingUpAction).text = GesturePreferences.getActionLabel(
            GesturePreferences.getActionForGesture(this, GesturePreferences.KEY_POINTING_UP, GesturePreferences.ACTION_OPEN_INSTAGRAM))
    }

    private fun showActionPicker(gestureName: String, onSelected: (String) -> Unit) {
        val labels = GesturePreferences.ALL_ACTIONS.map { it.second }.toTypedArray()
        val keys = GesturePreferences.ALL_ACTIONS.map { it.first }

        AlertDialog.Builder(this, R.style.Theme_Fliq_Dialog)
            .setTitle("Action for $gestureName")
            .setItems(labels) { _, which -> onSelected(keys[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
