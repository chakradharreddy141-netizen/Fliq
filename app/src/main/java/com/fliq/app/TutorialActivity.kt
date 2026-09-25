package com.fliq.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            // Mark tutorial as completed
            val prefs = getSharedPreferences("fliq_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("tutorial_completed", true).apply()

            // Launch MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
