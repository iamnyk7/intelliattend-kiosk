package com.intelliattend.kiosk.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "intelliattend_kiosk_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var serverUrl: String
        get() = sharedPreferences.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()

    var deviceCode: String
        get() = sharedPreferences.getString(KEY_DEVICE_CODE, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_DEVICE_CODE, value.trim()).apply()

    var devicePassword: String
        get() = sharedPreferences.getString(KEY_PASSWORD, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_PASSWORD, value).apply()

    var deviceId: String?
        get() = sharedPreferences.getString(KEY_DEVICE_ID, null)
        set(value) = sharedPreferences.edit().putString(KEY_DEVICE_ID, value).apply()

    var siteName: String?
        get() = sharedPreferences.getString(KEY_SITE_NAME, null)
        set(value) = sharedPreferences.edit().putString(KEY_SITE_NAME, value).apply()

    var isConfigured: Boolean
        get() = serverUrl.isNotBlank() && deviceCode.isNotBlank() && devicePassword.isNotBlank()
        set(_) {}

    var isKioskLocked: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_LOCKED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_IS_LOCKED, value).apply()

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_PASSWORD = "device_password"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_IS_LOCKED = "is_kiosk_locked"

        @Volatile
        private var instance: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
