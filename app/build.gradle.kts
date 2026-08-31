import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun propertyOrEnv(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val releaseStoreFile = propertyOrEnv("RELEASE_STORE_FILE")
val releaseStorePassword = propertyOrEnv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = propertyOrEnv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = propertyOrEnv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

fun getBuildTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd.HHmmss", Locale.getDefault()).format(Date())
}

android {
    namespace = "me.kafuuneko.rpclient"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "me.kafuuneko.rpclient"
        minSdk = 26
        targetSdk = 36
        versionCode = 20260203
        versionName = "2026.2.3"
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTimestamp()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // kotpref
    implementation(libs.kotpref)

    // koin
    implementation(libs.koin.android)

    // room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    // okhttp
    implementation(libs.okhttp)

    // gson
    implementation(libs.gson)

    // model-aware local tokenization
    implementation(libs.jtokkit)

}

tasks.configureEach {
    if (name.startsWith("package") && (name.endsWith("Debug") || name.endsWith("Release")) && !name.contains("AndroidTest")) {
        val variantName = if (name.endsWith("Debug")) "debug" else "release"
        doLast {
            val variantDir = layout.buildDirectory.dir("outputs/apk/$variantName").get().asFile
            if (!variantDir.exists()) return@doLast
            val timestamp = getBuildTimestamp()
            variantDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "apk") {
                    // Extract base clean prefix e.g. "app-debug" or "app-release-unsigned"
                    val cleanBase = when {
                        file.name.startsWith("app-debug") -> "app-debug"
                        file.name.startsWith("app-release-unsigned") -> "app-release-unsigned"
                        file.name.startsWith("app-release") -> "app-release"
                        else -> file.nameWithoutExtension.substringBefore('-')
                    }
                    val newName = "$cleanBase-$timestamp.apk"
                    val targetFile = File(variantDir, newName)
                    if (file.name != newName) {
                        file.renameTo(targetFile)
                    }
                }
            }
        }
    }
}
