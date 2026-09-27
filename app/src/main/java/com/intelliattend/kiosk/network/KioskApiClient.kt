package com.intelliattend.kiosk.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object KioskApiClient {

    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000

    suspend fun authenticateDevice(
        serverUrl: String,
        deviceCode: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = URL("${serverUrl.trimEnd('/')}/api/devices")
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val requestBody = JSONObject().apply {
                put("action", "LOGIN")
                put("deviceCode", deviceCode)
                put("password", password)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = BufferedReader(InputStreamReader(responseStream ?: return@withContext AuthResult(false, errorMessage = "Empty response from server"))).use {
                it.readText()
            }

            if (responseCode in 200..299) {
                val json = JSONObject(responseText)
                val deviceId = json.optString("id", "")
                val siteId = json.optString("siteId", "")
                val siteName = json.optJSONObject("site")?.optString("name", "") ?: ""
                AuthResult(
                    success = true,
                    deviceId = deviceId,
                    siteId = siteId,
                    siteName = siteName
                )
            } else {
                val errorMsg = try {
                    JSONObject(responseText).optString("error", "Authentication failed ($responseCode)")
                } catch (e: Exception) {
                    "Authentication failed ($responseCode)"
                }
                AuthResult(success = false, errorMessage = errorMsg)
            }
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = "Connection error: ${e.localizedMessage ?: "Could not reach server"}")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun sendHeartbeat(
        serverUrl: String,
        telemetry: TelemetryData
    ): HeartbeatResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = URL("${serverUrl.trimEnd('/')}/api/kiosk/heartbeat")
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("deviceCode", telemetry.deviceCode)
                put("batteryLevel", telemetry.batteryLevel)
                put("isCharging", telemetry.isCharging)
                put("networkType", telemetry.networkType)
                put("networkSsid", telemetry.networkSsid ?: JSONObject.NULL)
                put("latitude", telemetry.latitude ?: JSONObject.NULL)
                put("longitude", telemetry.longitude ?: JSONObject.NULL)
                put("locationAccuracy", telemetry.locationAccuracy ?: JSONObject.NULL)
                put("appVersion", telemetry.appVersion)
                put("androidVersion", telemetry.androidVersion)
                put("isKioskLocked", telemetry.isKioskLocked)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val json = JSONObject(responseText)
                val commandsList = mutableListOf<DeviceCommand>()
                val commandsArray: JSONArray? = json.optJSONArray("commands")
                if (commandsArray != null) {
                    for (i in 0 until commandsArray.length()) {
                        val cmdObj = commandsArray.getJSONObject(i)
                        commandsList.add(
                            DeviceCommand(
                                id = cmdObj.optString("id", ""),
                                command = cmdObj.optString("command", ""),
                                payload = cmdObj.optString("payload", null)
                            )
                        )
                    }
                }
                HeartbeatResponse(success = true, commands = commandsList)
            } else {
                HeartbeatResponse(success = false, errorMessage = "HTTP $responseCode")
            }
        } catch (e: Exception) {
            HeartbeatResponse(success = false, errorMessage = e.localizedMessage)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun acknowledgeCommand(
        serverUrl: String,
        deviceCode: String,
        commandId: String,
        status: String
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = URL("${serverUrl.trimEnd('/')}/api/kiosk/command/ack")
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = JSONObject().apply {
                put("deviceCode", deviceCode)
                put("commandId", commandId)
                put("status", status)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
