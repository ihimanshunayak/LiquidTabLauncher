plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The FreeMusic design system, shared.
 *
 * This module is a port of the FreeMusic app's visual identity — theme, type
 * scale, bundled SF Pro Display faces, the Liquid Glass glue and the vendored
 * backdrop engine — with no dependency on any app's business logic. Launcher
 * and player settings are supplied through composition locals, so this module
 * never reads a Settings store of its own.
 */
android {
    namespace = "com.ihimanshunayak.liquidtab.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // ---- Compose (Material 3) ----
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    // Pinned above the BOM's 1.7.6, the same pairing the FreeMusic app ships:
    // newer foundation alongside the BOM's older ui/material3 is a combination
    // Compose supports deliberately, and the backdrop engine needs recent
    // graphics-layer APIs.
    implementation("androidx.compose.foundation:foundation:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
