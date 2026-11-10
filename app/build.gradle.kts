plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.essentiel.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.essentiel.launcher"
        minSdk = 29          // Android 10 (ASUS Zenfone 5Z)
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"
        // Le Zenfone 5Z est en 64 bits : on n'embarque que cette version de la carte (APK plus léger)
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Clé fixe : les nouvelles versions s'installent par-dessus l'ancienne sans tout désinstaller.
    signingConfigs {
        create("stable") {
            storeFile = file("essentiel.keystore")
            storePassword = "essentiel"
            keyAlias = "essentiel"
            keyPassword = "essentiel"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
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
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Appareil photo
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Images (galerie, MMS, photos de contacts)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // Carte (moteur MapLibre, tuiles OpenFreeMap / OpenStreetMap)
    implementation("org.maplibre.gl:android-sdk:11.7.0")
}
