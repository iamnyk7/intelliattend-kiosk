plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.intelliattend.kiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.intelliattend.kiosk"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // WorkManager for hourly background telemetry & location sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Android Keystore EncryptedSharedPreferences for device credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Location Services for GPS Latitude & Longitude
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // AndroidX WebKit for hardware-accelerated WebView features
    implementation("androidx.webkit:webkit:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
