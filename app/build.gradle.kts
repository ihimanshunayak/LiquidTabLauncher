import java.time.LocalDate
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Signing details, kept out of the repository in `keystore.properties`
 * (see keystore.properties.example). Absent on a fresh checkout, in which case
 * the release build still runs and simply comes out unsigned rather than
 * failing — only whoever holds the key can produce a shippable APK.
 */
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Liquid Tab Launcher: the FreeMusic design system turned into an Android
 * tablet Home application.
 */
android {
    namespace = "com.ihimanshunayak.liquidtab"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ihimanshunayak.liquidtab"
        // 26 keeps reach wide; real-time blur (RenderEffect) kicks in on API 31+,
        // below that the glass falls back to a translucent scrim.
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Resolved when the build runs and shown on the About screen, so the app
        // can state when the build under the user's finger was made.
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Both halves have to be there, not just the properties file: it *names*
        // the keystore rather than containing it, and both are gitignored
        // separately, so a checkout can easily end up with the one and not the
        // other. A signing config pointing at a keystore that is not on disk
        // fails the release build outright at validateSigningRelease — which is
        // exactly the failure the unsigned fallback exists to avoid, so the
        // keystore has to be looked for rather than assumed.
        val store = signing.getProperty("storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // On for what it does to speed, not size: Compose is written to be
            // run through R8, and without it the whole UI runs measurably
            // slower. Nothing is renamed (-dontobfuscate), which also keeps
            // crash reports readable.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Null without a keystore to sign with: the build then produces
            // app-release-unsigned.apk instead of failing outright.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Separate package so a debug build never replaces an installed one.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    packaging {
        resources {
            excludes += "META-INF/*.kotlin_module"
        }
    }

    testOptions {
        unitTests {
            // Unit tests run against a stub android.jar whose methods throw
            // rather than return; the persistence layer logs on every decision
            // it makes, so tests of it would fail on the logging rather than
            // on the logic.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-ui"))

    // ---- Compose ----
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- FreeMusic bridge: Media3 session client (no player of our own) ----
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")

    // ---- Persistence: the launcher's own store, JSON in SharedPreferences ----
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ---- Artwork loading for the Now Playing widget / wallpapers ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
