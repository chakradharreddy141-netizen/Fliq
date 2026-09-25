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

        findViewById<android.view.View>(R.id.rowVictory).setOnClickListener {
            showActionPicker("Victory") { action ->
                GesturePreferences.setVictoryAction(this, action)
                refreshLabels()
            }
        }

        findViewById<android.view.View>(R.id.rowPointingUp).setOnClickListener {
            showActionPicker("Pointing Up") { action ->
                GesturePreferences.setPointingUpAction(this, action)
                refreshLabels()
            }
        }

        findViewById<android.view.View>(R.id.rowPinch).setOnClickListener {
            showActionPicker("Pinch") { action ->
                GesturePreferences.setPinchAction(this, action)
                refreshLabels()
            }
        }
    }

    private fun refreshLabels() {
        findViewById<TextView>(R.id.tvVictoryAction).text =
            GesturePreferences.getActionLabel(GesturePreferences.getVictoryAction(this))
        findViewById<TextView>(R.id.tvPointingUpAction).text =
            GesturePreferences.getActionLabel(GesturePreferences.getPointingUpAction(this))
        findViewById<TextView>(R.id.tvPinchAction).text =
            GesturePreferences.getActionLabel(GesturePreferences.getPinchAction(this))
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
