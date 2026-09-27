package com.intelliattend.kiosk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: SecurePreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        navigateToKiosk()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePreferences.getInstance(this)

        // Pre-fill existing configuration if available
        if (prefs.targetUrl.isNotBlank()) {
            binding.etTargetUrl.setText(prefs.targetUrl)
        }
        if (prefs.webUsername.isNotBlank()) {
            binding.etWebUsername.setText(prefs.webUsername)
        }
        if (prefs.webPassword.isNotBlank()) {
            binding.etWebPassword.setText(prefs.webPassword)
        }
        if (prefs.masterPin.isNotBlank()) {
            binding.etMasterPin.setText(prefs.masterPin)
        }
        binding.switchAutoLogin.isChecked = prefs.autoLoginEnabled

        binding.btnSubmit.setOnClickListener {
            handleSaveAndLaunch()
        }
    }

    private fun handleSaveAndLaunch() {
        val targetUrl = binding.etTargetUrl.text?.toString()?.trim() ?: ""
        val webUsername = binding.etWebUsername.text?.toString()?.trim() ?: ""
        val webPassword = binding.etWebPassword.text?.toString() ?: ""
        val masterPin = binding.etMasterPin.text?.toString()?.trim() ?: ""
        val autoLogin = binding.switchAutoLogin.isChecked

        if (targetUrl.isBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
            showError("Please enter a valid website URL (starting with http:// or https://)")
            return
        }

        if (masterPin.isBlank()) {
            showError("Please enter a Master PIN/Password to protect the kiosk unlock menu")
            return
        }

        // Save preferences securely
        prefs.targetUrl = targetUrl
        prefs.webUsername = webUsername
        prefs.webPassword = webPassword
        prefs.masterPin = masterPin
        prefs.autoLoginEnabled = autoLogin
        prefs.isKioskLocked = true

        Toast.makeText(this, "Configuration Saved! Entering Kiosk Mode...", Toast.LENGTH_SHORT).show()
        checkPermissionsAndProceed()
    }

    private fun checkPermissionsAndProceed() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        } else {
            navigateToKiosk()
        }
    }

    private fun navigateToKiosk() {
        val intent = Intent(this, KioskActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }
}
