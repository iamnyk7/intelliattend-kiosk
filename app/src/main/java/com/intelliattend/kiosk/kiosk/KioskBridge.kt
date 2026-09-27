package com.intelliattend.kiosk.kiosk

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.ui.KioskActivity
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KioskBridge(
    private val activity: KioskActivity,
    private val kioskManager: KioskManager
) {

    private val context: Context = activity.applicationContext
    private val prefs = SecurePreferences.getInstance(context)

    @JavascriptInterface
    fun isKiosk(): Boolean = true

    @JavascriptInterface
    fun getCredentialsJson(): String {
        return JSONObject().apply {
            put("username", prefs.webUsername)
            put("password", prefs.webPassword)
        }.toString()
    }

    @JavascriptInterface
    fun getBatteryJson(): String {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Int = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return JSONObject().apply {
            put("level", batteryPct)
            put("isCharging", isCharging)
        }.toString()
    }

    @JavascriptInterface
    fun getLocationJson(): String {
        val loc = try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            Tasks.await(task, 5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                null
            }
        }

        return JSONObject().apply {
            put("latitude", loc?.latitude ?: JSONObject.NULL)
            put("longitude", loc?.longitude ?: JSONObject.NULL)
            put("accuracy", loc?.accuracy ?: JSONObject.NULL)
        }.toString()
    }

    @JavascriptInterface
    fun getDeviceInfoJson(): String {
        return JSONObject().apply {
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("androidVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("isLocked", prefs.isKioskLocked)
        }.toString()
    }

    @JavascriptInterface
    fun unlock(pin: String): Boolean {
        if (pin == prefs.masterPin) {
            activity.runOnUiThread {
                kioskManager.stopKioskMode(activity)
            }
            return true
        }
        return false
    }

    @JavascriptInterface
    fun lock(): Boolean {
        activity.runOnUiThread {
            kioskManager.startKioskMode(activity)
        }
        return true
    }

    @JavascriptInterface
    fun hardRefresh() {
        activity.runOnUiThread {
            activity.performHardRefresh()
        }
    }

    @JavascriptInterface
    fun triggerHaptic() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }
}
