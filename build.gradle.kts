// Top-level build file for Liquid Tab Launcher — the FreeMusic design system
// turned into an Android tablet Home application.
buildscript {
    repositories {
        google()
    }
    dependencies {
        /*
         * Overrides the D8/R8 that AGP 8.10.1 bundles (8.10.9), for the same two
         * reasons the FreeMusic project does it:
         *
         *  - 8.10.x miscompiles large Compose functions in debug-mode dexing
         *    (a register-allocation bug; fixed as of 8.11.32), so a large
         *    composable can be rejected by ART's verifier at class load.
         *  - 8.10.9 cannot read Kotlin 2.3 @Metadata (it caps out at 2.2.0) and
         *    warns "malformed kotlin.Metadata" on every class this project
         *    compiles.
         *
         * 8.13.x is the newest 8.x line and handles both. Removable once AGP
         * itself bundles something past 8.11.32.
         */
        classpath("com.android.tools:r8:8.13.23")
    }
}

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}
