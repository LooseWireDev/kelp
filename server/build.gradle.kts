import java.util.Properties

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.lightphone.tide.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        consumerProguardFiles("proguard-rules.pro")
        buildConfigField(
            "String",
            "CLIENT_ID",
            localProperties.getProperty("tidal.clientid", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLIENT_SECRET",
            localProperties.getProperty("tidal.clientsecret", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "CLIENT_SCOPES",
            localProperties.getProperty("tidal.clientscopes", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "REDIRECT_URI",
            localProperties.getProperty("tidal.clientredirecturi", "tide://auth").asBuildConfigString(),
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.sdk.server)
    implementation(libs.compose.activity)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines)
    // TIDAL Auth needs AndroidKeysetManager, so use the Android artifact matching UnifiedPush's Tink version.
    implementation("com.google.crypto.tink:tink-android:1.20.0")
    implementation("com.tidal.sdk:auth:0.10.1") {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }
    implementation("com.tidal.sdk:player:0.0.71") {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }
    implementation("com.tidal.sdk:tidalapi:0.3.53") {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }
    testImplementation(libs.kotlin.test)
}
