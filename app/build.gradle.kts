plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The game's version. Bump it for each release; scripts/release.py reads it from here, and the
// game updates itself to a newer one found in the GitHub releases.
val pukiVersion = "0.4.0"

android {
    namespace = "io.github.tomsadeh.puki"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.tomsadeh.puki"
        minSdk = 26
        targetSdk = 34
        versionCode = pukiVersion.split(".").let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }
        versionName = pukiVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 64-bit ARM phones and tablets only (debug builds add the x86_64 emulator, below)
        ndk { abiFilters += listOf("arm64-v8a") }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64") }
        }
        release {
            // Signed like the builds Android Studio installs from this PC, so a release installs
            // over one of those without losing the child's progress
            signingConfig = signingConfigs.getByName("debug")
            // Shrunk: a smaller download and faster start (keep rules for native code in proguard-rules.pro)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
    }

    packaging {
        // Compressed in the APK (unpacked on install): the device brain's libraries are big
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // QR scanner for pairing with the PC (ZXing: no Play services, no network)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Puki's brain on the device itself (optional, for high-end phones): Gemma through
    // LiteRT-LM, the voice and Hebrew pronunciation through ONNX Runtime
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
