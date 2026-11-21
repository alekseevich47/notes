import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val sherpaAarVersion = "2.0.1"
val sherpaAarFile = file("libs/sherpa-onnx-$sherpaAarVersion.aar")
val sherpaAarUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaAarVersion/sherpa-onnx-$sherpaAarVersion.aar"

fun ensureSherpaAar() {
    if (sherpaAarFile.exists() && sherpaAarFile.length() > 1_000_000L) return
    sherpaAarFile.parentFile?.mkdirs()
    logger.lifecycle("Downloading sherpa-onnx $sherpaAarVersion AAR…")
    val tmp = file("${sherpaAarFile.absolutePath}.part")
    if (tmp.exists()) tmp.delete()
    val connection = URI(sherpaAarUrl).toURL().openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 180_000
    connection.setRequestProperty("User-Agent", "LoomixxNotes-Gradle")
    connection.inputStream.use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
    }
    connection.disconnect()
    require(tmp.length() > 1_000_000L) {
        "sherpa-onnx AAR download looks truncated (${tmp.length()} bytes)"
    }
    if (sherpaAarFile.exists()) sherpaAarFile.delete()
    check(tmp.renameTo(sherpaAarFile)) { "Failed to move sherpa-onnx AAR into app/libs" }
}

ensureSherpaAar()

tasks.register("downloadSherpaAar") {
    group = "setup"
    description = "Download the sherpa-onnx Android AAR into app/libs if it is missing"
    doLast { ensureSherpaAar() }
}

android {
    namespace = "com.alekseevich47.notes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alekseevich47.notes"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Shared debug keystore so CI and local APKs share the same certificate and
    // can update each other without uninstall (Play/sideload INSTALL_FAILED_UPDATE_INCOMPATIBLE).
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libc++_shared.so", "**/libonnxruntime.so")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    implementation("dev.chrisbanes.haze:haze:1.5.2")

    implementation(files(sherpaAarFile))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
