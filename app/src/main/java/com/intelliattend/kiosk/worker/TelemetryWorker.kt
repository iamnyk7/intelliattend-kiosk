package com.intelliattend.kiosk.worker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.intelliattend.kiosk.data.SecurePreferences
import com.intelliattend.kiosk.network.KioskApiClient
import com.intelliattend.kiosk.network.TelemetryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TelemetryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = SecurePreferences.getInstance(context)
        if (!prefs.isConfigured) {
            return@withContext Result.success()
        }

        try {
            val batteryInfo = getBatteryStatus()
            val networkInfo = getNetworkStatus()
            val location = getLocation()

            val telemetry = TelemetryData(
                deviceCode = prefs.deviceCode,
                batteryLevel = batteryInfo.first,
                isCharging = batteryInfo.second,
                networkType = networkInfo.first,
                networkSsid = networkInfo.second,
                latitude = location?.latitude,
                longitude = location?.longitude,
                locationAccuracy = location?.accuracy,
                appVersion = getAppVersion(),
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                isKioskLocked = prefs.isKioskLocked
            )

            val response = KioskApiClient.sendHeartbeat(prefs.serverUrl, telemetry)
            if (response.success && response.commands.isNotEmpty()) {
                for (command in response.commands) {
                    val intent = Intent(ACTION_KIOSK_COMMAND).apply {
                        putExtra(EXTRA_COMMAND_ID, command.id)
                        putExtra(EXTRA_COMMAND_TYPE, command.command)
                        putExtra(EXTRA_PAYLOAD, command.payload)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Int = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(batteryPct, isCharging)
    }

    private fun getNetworkStatus(): Pair<String, String?> {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return Pair("NONE", null)
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return Pair("NONE", null)

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wifiManager =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ssid = wifiManager?.connectionInfo?.ssid?.replace("\"", "")
                Pair("WIFI", if (ssid == "<unknown ssid>") null else ssid)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                Pair("CELLULAR", null)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                Pair("ETHERNET", null)
            }
            else -> Pair("CONNECTED", null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocation(): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val task = fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Fallback to LocationManager
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                gpsLoc ?: netLoc
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    companion object {
        const val ACTION_KIOSK_COMMAND = "com.intelliattend.kiosk.ACTION_KIOSK_COMMAND"
        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_COMMAND_TYPE = "command_type"
        const val EXTRA_PAYLOAD = "command_payload"
    }
}
