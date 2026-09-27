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
import com.intelliattend.kiosk.R
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.databinding.ActivityKioskBinding
import com.intelliattend.kiosk.databinding.DialogAdminExitBinding
import com.intelliattend.kiosk.kiosk.KioskBridge
import com.intelliattend.kiosk.kiosk.KioskManager
import com.intelliattend.kiosk.worker.TelemetryWorker

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private lateinit var prefs: SecurePreferences
    private lateinit var kioskManager: KioskManager

    private var tapCount = 0
    private var lastTapTime = 0L

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val commandType = intent.getStringExtra(TelemetryWorker.EXTRA_COMMAND_TYPE) ?: return
            handleRemoteCommand(commandType)
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
        setupMaintenanceTrigger()
        setupOfflineOverlay()

        // Register for remote commands
        val filter = IntentFilter(TelemetryWorker.ACTION_KIOSK_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        loadWebPage()
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

        // Expose Native Android Hardware & Telemetry Bridge to Next.js JavaScript
        binding.webView.addJavascriptInterface(
            KioskBridge(this, kioskManager),
            "KioskBridge"
        )

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Silently auto-grant Camera, Microphone, and Audio permissions to the web page
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
                injectCredentialsAndAutoLogin(view)
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
                // Crash Watchdog: auto-restart webview if memory or render process dies
                view?.destroy()
                recreate()
                return true
            }
        }
    }

    /**
     * Injects credentials and executes auto-fill & auto-submit so the user never has to log in twice
     */
    private fun injectCredentialsAndAutoLogin(view: WebView?) {
        val username = prefs.webUsername
        val password = prefs.webPassword
        val autoLogin = prefs.autoLoginEnabled

        val jsScript = """
            (function() {
                try {
                    window.__KIOSK_CREDENTIALS__ = {
                        username: "$username",
                        password: "$password"
                    };

                    // For IntelliAttend Attendance localStorage compatibility
                    if ("$username".length > 0) {
                        localStorage.setItem('kiosk_deviceId', "$username");
                        localStorage.setItem('kiosk_login_time', Date.now().toString());
                    }

                    // Auto-fill form and submit if enabled
                    if ($autoLogin && "$username".length > 0 && "$password".length > 0) {
                        setTimeout(function() {
                            var userField = document.querySelector('input[type="text"], input[type="email"], input[name*="user"], input[name*="email"], input[name*="code"], input[name*="device"], input[id*="user"], input[id*="email"], input[id*="code"], input[id*="device"]');
                            var passField = document.querySelector('input[type="password"]');

                            if (userField && passField && (userField.value.length === 0 || passField.value.length === 0)) {
                                userField.value = "$username";
                                userField.dispatchEvent(new Event('input', { bubbles: true }));
                                userField.dispatchEvent(new Event('change', { bubbles: true }));

                                passField.value = "$password";
                                passField.dispatchEvent(new Event('input', { bubbles: true }));
                                passField.dispatchEvent(new Event('change', { bubbles: true }));

                                setTimeout(function() {
                                    var submitBtn = document.querySelector('button[type="submit"], input[type="submit"]');
                                    if (!submitBtn) {
                                        var buttons = document.querySelectorAll('button');
                                        for (var i = 0; i < buttons.length; i++) {
                                            var text = (buttons[i].innerText || '').toLowerCase();
                                            if (text.includes('login') || text.includes('sign in') || text.includes('enter') || text.includes('submit')) {
                                                submitBtn = buttons[i];
                                                break;
                                            }
                                        }
                                    }
                                    if (submitBtn) {
                                        submitBtn.click();
                                    }
                                }, 350);
                            }
                        }, 600);
                    }
                } catch(e) {
                    console.error('Kiosk auto-login script error', e);
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(jsScript, null)
    }

    private fun loadWebPage() {
        val url = prefs.targetUrl
        if (url.isNotBlank()) {
            binding.webView.loadUrl(url)
        }
    }

    fun performHardRefresh() {
        binding.webView.clearCache(true)
        loadWebPage()
        Toast.makeText(this, "Page Refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun setupOfflineOverlay() {
        binding.btnRetry.setOnClickListener {
            binding.offlineContainer.visibility = View.GONE
            loadWebPage()
        }
    }

    /**
     * Maintenance Trigger: 3 rapid taps (or long press) on the top-right corner
     */
    private fun setupMaintenanceTrigger() {
        binding.maintenanceTrigger.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 1500) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = now

            if (tapCount >= 3) {
                tapCount = 0
                showMaintenanceDialog()
            }
        }

        binding.maintenanceTrigger.setOnLongClickListener {
            showMaintenanceDialog()
            true
        }
    }

    private fun showMaintenanceDialog() {
        val dialogBinding = DialogAdminExitBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Update Toggle Lock button label depending on current state
        if (prefs.isKioskLocked) {
            dialogBinding.btnToggleLock.text = getString(R.string.btn_unlock)
        } else {
            dialogBinding.btnToggleLock.text = getString(R.string.btn_lock)
        }

        // Toggle Lock/Unlock Action
        dialogBinding.btnToggleLock.setOnClickListener {
            val enteredPin = dialogBinding.etAdminPin.text?.toString()?.trim() ?: ""
            if (enteredPin == prefs.masterPin) {
                if (prefs.isKioskLocked) {
                    kioskManager.stopKioskMode(this)
                    Toast.makeText(this, "Device Unlocked! Maintenance mode active.", Toast.LENGTH_LONG).show()
                } else {
                    kioskManager.startKioskMode(this)
                    Toast.makeText(this, "Device Re-locked in Kiosk Mode.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect Master PIN"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        // Hard Refresh Action
        dialogBinding.btnAdminHardRefresh.setOnClickListener {
            val enteredPin = dialogBinding.etAdminPin.text?.toString()?.trim() ?: ""
            if (enteredPin == prefs.masterPin) {
                performHardRefresh()
                dialog.dismiss()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect Master PIN"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        // Reconfigure Action
        dialogBinding.btnAdminReconfigure.setOnClickListener {
            val enteredPin = dialogBinding.etAdminPin.text?.toString()?.trim() ?: ""
            if (enteredPin == prefs.masterPin) {
                kioskManager.stopKioskMode(this)
                dialog.dismiss()
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
            } else {
                dialogBinding.tvAdminError.text = "Incorrect Master PIN"
                dialogBinding.tvAdminError.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun handleRemoteCommand(commandType: String) {
        when (commandType) {
            "HARD_REFRESH" -> {
                runOnUiThread { performHardRefresh() }
            }
            "UNLOCK_KIOSK" -> {
                runOnUiThread {
                    kioskManager.stopKioskMode(this)
                    Toast.makeText(this, "Kiosk Unlocked Remotely", Toast.LENGTH_LONG).show()
                }
            }
            "LOCK_KIOSK" -> {
                runOnUiThread {
                    kioskManager.startKioskMode(this)
                    Toast.makeText(this, "Kiosk Re-locked Remotely", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
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
