plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val spotifyClientId = localProperties.getProperty("SPOTIFY_CLIENT_ID") ?: ""

android {
    namespace = "com.chiron.core.spotify"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:ui")) // MiniPlayerBar imports theme colors

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose) // rememberLauncherForActivityResult in MiniPlayerBar
    implementation(libs.kotlinx.coroutines.android)

    compileOnly(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.spotify.android:auth:3.1.0")
    implementation("com.google.code.gson:gson:2.10.1")
}