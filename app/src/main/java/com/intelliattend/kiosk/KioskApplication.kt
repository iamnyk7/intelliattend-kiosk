package com.intelliattend.kiosk

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.intelliattend.kiosk.worker.TelemetryWorker
import java.util.concurrent.TimeUnit

class KioskApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleTelemetryWork()
    }

    private fun scheduleTelemetryWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Runs once every 1 hour to report GPS location, battery, network and fetch backend commands
        val telemetryWorkRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // 15-minute flex window
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME_TELEMETRY,
            ExistingPeriodicWorkPolicy.KEEP,
            telemetryWorkRequest
        )
    }

    companion object {
        const val WORK_NAME_TELEMETRY = "intelliattend_hourly_telemetry"
    }
}
