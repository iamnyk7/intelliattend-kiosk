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

    var targetUrl: String
        get() = sharedPreferences.getString(KEY_TARGET_URL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_TARGET_URL, value.trim()).apply()

    var webUsername: String
        get() = sharedPreferences.getString(KEY_WEB_USERNAME, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_WEB_USERNAME, value.trim()).apply()

    var webPassword: String
        get() = sharedPreferences.getString(KEY_WEB_PASSWORD, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_WEB_PASSWORD, value).apply()

    var masterPin: String
        get() = sharedPreferences.getString(KEY_MASTER_PIN, "1234") ?: "1234"
        set(value) = sharedPreferences.edit().putString(KEY_MASTER_PIN, value.trim()).apply()

    var autoLoginEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_LOGIN, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_LOGIN, value).apply()

    var isKioskLocked: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_LOCKED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_IS_LOCKED, value).apply()

    val isConfigured: Boolean
        get() = targetUrl.isNotBlank() && masterPin.isNotBlank()

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_TARGET_URL = "target_url"
        private const val KEY_WEB_USERNAME = "web_username"
        private const val KEY_WEB_PASSWORD = "web_password"
        private const val KEY_MASTER_PIN = "master_pin"
        private const val KEY_AUTO_LOGIN = "auto_login"
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
