package com.intelliattend.kiosk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.ui.KioskActivity
import com.intelliattend.kiosk.ui.SetupActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = SecurePreferences.getInstance(this)

        val targetActivity = if (prefs.isConfigured) {
            KioskActivity::class.java
        } else {
            SetupActivity::class.java
        }

        startActivity(Intent(this, targetActivity))
        finish()
    }
}
