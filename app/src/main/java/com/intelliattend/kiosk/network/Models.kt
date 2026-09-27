package com.intelliattend.kiosk.network

data class AuthResult(
    val success: Boolean,
    val deviceId: String? = null,
    val siteId: String? = null,
    val siteName: String? = null,
    val errorMessage: String? = null
)

data class TelemetryData(
    val deviceCode: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val networkType: String,
    val networkSsid: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracy: Float?,
    val appVersion: String,
    val androidVersion: String,
    val isKioskLocked: Boolean
)

data class DeviceCommand(
    val id: String,
    val command: String,
    val payload: String? = null
)

data class HeartbeatResponse(
    val success: Boolean,
    val commands: List<DeviceCommand> = emptyList(),
    val errorMessage: String? = null
)
