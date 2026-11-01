plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.projectorprobe"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.projectorprobe"
        minSdk = 23
        // Deliberately 28: lets the probe test Wi-Fi control and see all apps on Android 11.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
