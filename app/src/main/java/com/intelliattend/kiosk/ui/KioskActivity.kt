package com.intelliattend.kiosk.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.intelliattend.kiosk.R
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.databinding.ActivityKioskBinding
import com.intelliattend.kiosk.databinding.DialogAdminExitBinding
import com.intelliattend.kiosk.kiosk.KioskManager
import com.intelliattend.kiosk.network.KioskApiClient
import com.intelliattend.kiosk.worker.TelemetryWorker
import kotlinx.coroutines.launch

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private lateinit var prefs: SecurePreferences
    private lateinit var kioskManager: KioskManager

    private var secretTapCount = 0
    private var lastTapTime = 0L

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val commandType = intent.getStringExtra(TelemetryWorker.EXTRA_COMMAND_TYPE) ?: return
            val commandId = intent.getStringExtra(TelemetryWorker.EXTRA_COMMAND_ID) ?: ""
            handleRemoteCommand(commandType, commandId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePreferences.getInstance(this)
        kioskManager = KioskManager(this)

        if (!prefs.isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setupWebView()
        setupSecretTapZone()
        setupOfflineOverlay()

        // Register for real-time remote commands sent from TelemetryWorker
        val filter = IntentFilter(TelemetryWorker.ACTION_KIOSK_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        loadKioskPage()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isKioskLocked) {
            kioskManager.startKioskMode(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.isKioskLocked) {
            kioskManager.applyImmersiveSticky(this)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Auto-grant camera and microphone permissions to IntelliAttend without browser popups
                runOnUiThread {
                    request.grant(request.resources)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.webProgressBar.visibility = View.VISIBLE
                    binding.webProgressBar.progress = newProgress
                } else {
                    binding.webProgressBar.visibility = View.GONE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.offlineContainer.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject credentials and deviceId directly into localStorage so kiosk auto-logs in!
                val deviceId = prefs.deviceId ?: ""
                if (deviceId.isNotBlank()) {
                    val script = """
                        try {
                            localStorage.setItem('kiosk_deviceId', '$deviceId');
                            localStorage.setItem('kiosk_login_time', '${System.currentTimeMillis()}');
                        } catch (e) {
                            console.error('Failed to inject kiosk storage', e);
                        }
                    """.trimIndent()
                    view?.evaluateJavascript(script, null)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.offlineContainer.visibility = View.VISIBLE
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Automatic crash recovery watchdog: recreate webview if render process dies
                view?.destroy()
                recreate()
                return true
            }
        }
    }

    private fun loadKioskPage() {
        val baseUrl = prefs.serverUrl.trimEnd('/')
        val targetUrl = "$baseUrl/kiosk"
        binding.webView.loadUrl(targetUrl)
    }

    private fun setupOfflineOverlay() {
        binding.btnRetry.setOnClickListener {
            binding.offlineContainer.visibility = View.GONE
            loadKioskPage()
        }
    }

    /**
     * Technician Secret Maintenance Trigger: 5 rapid taps within 2 seconds
     */
    private fun setupSecretTapZone() {
        binding.emergencyTouchTarget.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 2000) {
                secretTapCount++
            } else {
                secretTapCount = 1
            }
            lastTapTime = now

            if (secretTapCount >= 5) {
                secretTapCount = 0
                showAdminExitDialog()
            }
        }
    }

    private fun showAdminExitDialog() {
        val dialogBinding = DialogAdminExitBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Hard Refresh Action
        dialogBinding.btnAdminHardRefresh.setOnClickListener {
            val enteredPassword = dialogBinding.etAdminPassword.text?.toString() ?: ""
            if (enteredPassword == prefs.devicePassword) {
                binding.webView.clearCache(true)
                loadKioskPage()
                dialog.dismiss()
                Toast.makeText(this, "WebView Refreshed", Toast.LENGTH_SHORT).show()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect password"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        // Unlock Kiosk Mode Action
        dialogBinding.btnAdminUnlock.setOnClickListener {
            val enteredPassword = dialogBinding.etAdminPassword.text?.toString() ?: ""
            if (enteredPassword == prefs.devicePassword) {
                kioskManager.stopKioskMode(this)
                dialog.dismiss()
                Toast.makeText(this, "Kiosk Unlocked. Device is now in maintenance mode.", Toast.LENGTH_LONG).show()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect password"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        // Reconfigure Device ID Action
        dialogBinding.btnAdminResetConfig.setOnClickListener {
            val enteredPassword = dialogBinding.etAdminPassword.text?.toString() ?: ""
            if (enteredPassword == prefs.devicePassword) {
                kioskManager.stopKioskMode(this)
                prefs.clear()
                dialog.dismiss()
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect password"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun handleRemoteCommand(commandType: String, commandId: String) {
        when (commandType) {
            "HARD_REFRESH" -> {
                runOnUiThread {
                    binding.webView.clearCache(true)
                    loadKioskPage()
                    Toast.makeText(this, "Remote Hard Refresh Executed", Toast.LENGTH_SHORT).show()
                }
            }
            "UNLOCK_KIOSK" -> {
                runOnUiThread {
                    kioskManager.stopKioskMode(this)
                    Toast.makeText(this, "Kiosk Unlocked Remotely from Admin Dashboard", Toast.LENGTH_LONG).show()
                }
            }
            "LOCK_KIOSK" -> {
                runOnUiThread {
                    kioskManager.startKioskMode(this)
                    Toast.makeText(this, "Kiosk Re-locked Remotely", Toast.LENGTH_SHORT).show()
                }
            }
            "CLEAR_CACHE" -> {
                runOnUiThread {
                    binding.webView.clearCache(true)
                }
            }
        }

        // Acknowledge back to backend
        if (commandId.isNotBlank()) {
            lifecycleScope.launch {
                KioskApiClient.acknowledgeCommand(prefs.serverUrl, prefs.deviceCode, commandId, "EXECUTED")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // In locked kiosk mode, back button is completely disabled
        if (!prefs.isKioskLocked) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(commandReceiver)
        } catch (_: Exception) {}
        binding.webView.destroy()
        super.onDestroy()
    }
}
