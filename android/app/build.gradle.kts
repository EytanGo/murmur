import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystore = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "co.choclement.murmur"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.choclement.murmur"
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "1.9"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile", "murmur-release.jks"))
            storePassword = keystore.getProperty("password")
            keyAlias = "murmur"
            keyPassword = keystore.getProperty("password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += listOf("onnx", "txt") }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))
}

// Every release build replaces the shared copies: ~/Desktop/Murmur/Murmur.apk and Google Drive
// (named Murmur-<version>-build<code>.apk there).
val publishApk by tasks.registering(Exec::class) {
    commandLine(rootProject.file("publish.sh").path,
        layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile.path,
        android.defaultConfig.versionName, android.defaultConfig.versionCode.toString())
}
afterEvaluate {
    tasks.named("assembleRelease") { finalizedBy(publishApk) }
}
