package com.intelliattend.kiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.intelliattend.kiosk.data.SecurePreferences

class KioskManager(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent: ComponentName = DeviceAdminReceiver.getComponentName(context)
    private val securePreferences = SecurePreferences.getInstance(context)

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun isDeviceAdmin(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Configures Android Enterprise lockdown policies if granted Device Owner.
     */
    fun applyDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return

        try {
            // Set our app package as the dedicated lock task package (no confirmation dialog needed)
            devicePolicyManager.setLockTaskPackages(
                adminComponent,
                arrayOf(context.packageName)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Block all system UI, home button, recent apps, and notifications in lock task
                devicePolicyManager.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }

            // Disable keyguard (lock screen) so tablet boots directly into the app
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)

            // Prevent user from factory resetting, safe booting, or adding other users
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Enters locked Kiosk mode.
     */
    fun startKioskMode(activity: Activity) {
        securePreferences.isKioskLocked = true
        applyDeviceOwnerPolicies()
        applyImmersiveSticky(activity)

        try {
            activity.startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Exits locked Kiosk mode (for authorized technician maintenance or remote unlock).
     */
    fun stopKioskMode(activity: Activity) {
        securePreferences.isKioskLocked = false
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        showSystemBars(activity)
    }

    /**
     * Immersive Sticky Fullscreen: hides Status Bar and Navigation Bar completely.
     */
    fun applyImmersiveSticky(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(false)
            val controller = activity.window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    private fun showSystemBars(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
