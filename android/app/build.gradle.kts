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
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Two apps from one codebase, installable side by side. They share the speech engine,
    // settings and look (src/main); each has its own way of typing (src/keyboard, src/bubble).
    flavorDimensions += "edition"
    productFlavors {
        create("keyboard") {
            dimension = "edition"
            versionCode = 11
            versionName = "2.0"
            manifestPlaceholders["appLabel"] = "Murmur"
        }
        create("bubble") {
            dimension = "edition"
            applicationIdSuffix = ".bubble"
            versionCode = 1
            versionName = "1.0"
            manifestPlaceholders["appLabel"] = "Murmur Bubble"
        }
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

// Every release build publishes its APK: to ~/Desktop/Murmur (Murmur.apk / Murmur-Bubble.apk) and
// to Google Drive, named Murmur-<version>-build<code>.apk / Murmur-Bubble-<version>-build<code>.apk.
android.productFlavors.forEach { flavor ->
    val name = flavor.name
    val publish = tasks.register<Exec>("publish${name.replaceFirstChar { it.uppercase() }}Apk") {
        commandLine(rootProject.file("publish.sh").path,
            layout.buildDirectory.file("outputs/apk/$name/release/app-$name-release.apk").get().asFile.path,
            flavor.versionName!!, flavor.versionCode!!.toString(), name)
    }
    afterEvaluate {
        tasks.named("assemble${name.replaceFirstChar { it.uppercase() }}Release") { finalizedBy(publish) }
    }
}
