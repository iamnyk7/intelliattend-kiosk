# IntelliAttend Android Managed Kiosk

A dedicated, 100% free, zero-vendor-dependency Native Android Kiosk application built specifically for **IntelliAttend**. It locks down Android phones and tablets into dedicated attendance terminals that run your web application exclusively.

---

## 🌟 Key Features

* 🔐 **Device ID & Password Onboarding:** No complicated QR setup required. Technicians enter the **Server URL**, **Device Code** (e.g. `KIOSK-BLR-001`), and **Password** once. Credentials are encrypted at rest using Android Keystore AES-256 GCM.
* 🛡️ **Unbreakable Lockdown (LockTask Mode):** Completely suppresses the Home button, Back button, Recent Apps list, Notification bar swipe-down, and Android Settings.
* 🛰️ **Hourly GPS & Battery Telemetry:** Background `WorkManager` runs once an hour to report exact **GPS Latitude & Longitude**, **Battery %**, **Charging State**, and **WiFi SSID/Signal** to the backend.
* ⚡ **Remote Management & Commands:** Supports remote commands triggered from the Admin Dashboard:
  * `HARD_REFRESH` — Forces the WebView to flush cache and reload the latest page.
  * `UNLOCK_KIOSK` — Temporarily unlocks the tablet from the backend for technician maintenance.
  * `LOCK_KIOSK` — Re-engages fullscreen lockdown remotely.
* 🚀 **Auto-Start on Boot:** Automatically launches into the attendance terminal as soon as the tablet is powered on (`BOOT_COMPLETED`).
* 📷 **Silent WebRTC Camera Approval:** Auto-approves camera access for facial recognition without prompting the user.
* 🛡️ **Auto-Recovery Watchdog:** Automatically detects WebKit render crashes (`onRenderProcessGone`) and restarts the WebView with offline retry handling.
* 🛠️ **Technician Emergency Exit:** 5 rapid taps on the top-right corner opens a password-protected technician menu to refresh, unlock, or reconfigure the terminal.

---

## 🚀 Cloud Build via GitHub Actions (0 GB on your laptop)

You do **not** need to install Android Studio or Java on your PC.

### How to Build & Download the APK:
1. Push this folder to a GitHub repository (e.g., `github.com/your-username/intelliattend-kiosk`).
2. Go to your repository on GitHub and click the **Actions** tab.
3. Select **"Build IntelliAttend Kiosk APK"** $\rightarrow$ Click **Run workflow**.
4. Once completed (~2 minutes), click on the build run.
5. Under **Artifacts** at the bottom, download **`intelliattend-kiosk-apk.zip`** to get your compiled `app-debug.apk`!

---

## 🧪 How to Test Without a Phone (0 GB on PC)

1. Download the compiled `.apk` from GitHub Actions.
2. Open your browser and go to **[appetize.io/upload](https://appetize.io/upload)**.
3. Drag & drop the `.apk`.
4. An interactive virtual Android device will open directly inside your browser window. You can click, type your Device ID & Password, and test the app immediately.

---

## 📱 Production Deployment on Physical Tablets

### 1. Install the APK
Download or copy the `.apk` onto your tablet and tap **Install**. (If Play Protect shows "Unrecognized app", tap *More details* $\rightarrow$ *Install anyway*).

### 2. Enter Device Credentials
Open **IntelliAttend Kiosk** $\rightarrow$ Enter:
* **Server Base URL:** `https://your-domain.com`
* **Device ID:** `KIOSK-BLR-001`
* **Password:** (your device password)
* Tap **Verify & Enter Locked Kiosk**.

### 3. Make it 100% Unbreakable (Enterprise Device Owner Mode)
To permanently lock down the tablet so users cannot swipe down the notification bar, open settings, or exit:
1. Connect the tablet to your PC with USB and enable USB Debugging.
2. Run this single command in your terminal:
   ```bash
   adb shell dpm set-device-owner com.intelliattend.kiosk/.kiosk.DeviceAdminReceiver
   ```
*(Note: To set Device Owner, ensure no Google accounts are logged into the tablet prior to running the command, or set it during initial setup).*
