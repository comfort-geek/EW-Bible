plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ewbible.app"
    compileSdk = 36

    // FIX: Use fixed debug.keystore from repo root, so SHA-1 never changes on GitHub
    // WARNING: debug.keystore MUST be committed to repo root, or CI fails with "Keystore file not found"
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.ewbible.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
        // REMOVED: GEMINI / GROQ / TAVILY keys - now live only in Cloudflare Worker, not in app
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // --- ADDED FOR YOUR HOME SCREEN ---
    // Fix 1: Icons for ☰ Menu and ⋮ Settings
    implementation(libs.androidx.material.icons.extended)

    // Fix 2: Firebase Auth for Login to Sync Data / Welcome Gmail Name
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // FIX: GeminiRepository uses okhttp3 - this was missing, broke the whole build
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}