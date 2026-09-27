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
import androidx.lifecycle.lifecycleScope
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.databinding.ActivitySetupBinding
import com.intelliattend.kiosk.network.KioskApiClient
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: SecurePreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed to Kiosk regardless; KioskActivity handles missing permissions gracefully
        navigateToKiosk()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePreferences.getInstance(this)

        // Pre-fill existing credentials if already saved
        if (prefs.serverUrl.isNotBlank()) {
            binding.etServerUrl.setText(prefs.serverUrl)
        }
        if (prefs.deviceCode.isNotBlank()) {
            binding.etDeviceCode.setText(prefs.deviceCode)
        }

        binding.btnSubmit.setOnClickListener {
            handleSetupSubmit()
        }
    }

    private fun handleSetupSubmit() {
        val serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        val deviceCode = binding.etDeviceCode.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (serverUrl.isBlank() || !serverUrl.startsWith("http")) {
            showError("Please enter a valid Server URL starting with http:// or https://")
            return
        }
        if (deviceCode.isBlank()) {
            showError("Please enter the assigned Device ID/Code")
            return
        }
        if (password.isBlank()) {
            showError("Please enter the Device Password")
            return
        }

        setLoading(true)
        binding.tvStatusMessage.visibility = View.GONE

        lifecycleScope.launch {
            val result = KioskApiClient.authenticateDevice(serverUrl, deviceCode, password)
            setLoading(false)

            if (result.success) {
                // Save verified credentials securely
                prefs.serverUrl = serverUrl
                prefs.deviceCode = deviceCode
                prefs.devicePassword = password
                prefs.deviceId = result.deviceId
                prefs.siteName = result.siteName
                prefs.isKioskLocked = true

                Toast.makeText(
                    this@SetupActivity,
                    "Device Authenticated Successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                checkPermissionsAndProceed()
            } else {
                showError(result.errorMessage ?: "Authentication failed. Please verify credentials.")
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val ungranted = requiredPermissions.filter {
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

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
        binding.etServerUrl.isEnabled = !isLoading
        binding.etDeviceCode.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvStatusMessage.text = message
        binding.tvStatusMessage.visibility = View.VISIBLE
    }
}
