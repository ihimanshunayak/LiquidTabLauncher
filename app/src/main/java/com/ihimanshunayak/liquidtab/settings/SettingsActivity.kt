/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — Settings.
 *
 * Name      : SettingsActivity.kt
 * Version   : 1.0.0
 * Purpose   : Hosts the settings screen. Deliberately a separate, non-exported
 *             Activity rather than a Home sheet: a launcher's Settings is
 *             reached from a menu and should behave like a screen you can back
 *             out of, and keeping it out of the Home activity means a heavy
 *             settings screen never participates in the Home window's
 *             recreation.
 */

package com.ihimanshunayak.liquidtab.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.ThemeMode
import com.ihimanshunayak.liquidtab.ui.theme.LiquidTabTheme
import com.ihimanshunayak.liquidtab.ui.theme.SystemBarIcons

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by LauncherSettings.themeMode.state.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LiquidTabTheme(darkTheme = darkTheme) {
                SystemBarIcons(dark = !darkTheme)
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
