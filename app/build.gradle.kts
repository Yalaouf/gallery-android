import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** Release key settings: from local.properties on a dev machine, from environment variables in CI. */
fun releaseSetting(localKey: String, envKey: String): String? =
    localProps.getProperty(localKey) ?: System.getenv(envKey)

val releaseStoreFile = releaseSetting("release.storeFile", "RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.mephilia.gallery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mephilia.gallery"
        // Android 11+ for MediaStore.createWriteRequest / createDeleteRequest
        minSdk = 30
        targetSdk = 37
        versionCode = 7
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseSetting("release.storePassword", "RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSetting("release.keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = releaseSetting("release.keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Signed with the release key when it's configured (GitHub releases), otherwise unsigned:
            // F-Droid builds without any key and signs the APK itself.
            signingConfig = when {
                releaseStoreFile != null -> signingConfigs.getByName("release")
                localProps.getProperty("signReleaseWithDebugKey") == "true" -> signingConfigs.getByName("debug")
                else -> null
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric tests use the app's resources and merged manifest (strings, FileProvider, launcher aliases)
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric reaches into JDK internals (file descriptors) that recent Java versions lock down
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
    // Per-app language list (Android 13+)
    androidResources {
        generateLocaleConfig = true
    }
    // No dependency metadata block (encrypted for Google) in the APK/AAB
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    testImplementation(libs.junit)
    // Runs Android code (bitmaps, files, preferences, package manager) on the computer
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
