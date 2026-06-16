plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.peerloomllc.satscream"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.peerloomllc.satscream"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Release signing pulls every field from the env (the release
            // script exports them before the gradle invocation). If any are
            // missing, leave storeFile unset so the buildTypes.release block
            // below falls back to debug signing -- lets you produce a
            // (debug-signed) release build locally without the keystore.
            val ksFile = System.getenv("KEYSTORE_FILE") ?: findProperty("KEYSTORE_FILE") as String?
            val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: findProperty("KEYSTORE_PASSWORD") as String?
            val kAlias = System.getenv("KEY_ALIAS") ?: findProperty("KEY_ALIAS") as String?
            val kPassword = System.getenv("KEY_PASSWORD") ?: findProperty("KEY_PASSWORD") as String?
            if (ksFile != null && ksPassword != null && kAlias != null && kPassword != null) {
                storeFile = file(ksFile)
                storePassword = ksPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        release {
            // Use the real release key when configured, else debug-sign so
            // local release builds still install.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"

            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.material:material:1.12.0")
    // For HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // For coroutines (async operations)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // appcompat/Android X
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}