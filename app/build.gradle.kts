plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fitzy.apexcleaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitzy.apexcleaner"
        minSdk = 26
        targetSdk = 35
        // CI sets APEX_VERSION_CODE (the run number) so each release is newer than the last.
        val code = (System.getenv("APEX_VERSION_CODE") ?: "1").toInt()
        versionCode = code
        versionName = "1.0.$code"
    }

    // A fixed test-signing key, so every build can update the one before it. Not for a store release.
    signingConfigs {
        getByName("debug") {
            storeFile = file("apex-test.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        aidl = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.tv.material)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
