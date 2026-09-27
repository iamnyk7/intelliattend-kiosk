package com.intelliattend.kiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.intelliattend.kiosk.MainActivity
import com.intelliattend.kiosk.data.SecurePreferences

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = SecurePreferences.getInstance(context)
            if (prefs.isConfigured) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
