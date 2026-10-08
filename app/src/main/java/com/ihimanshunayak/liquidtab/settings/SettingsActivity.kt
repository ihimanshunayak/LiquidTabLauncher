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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                SettingsRoot(onBack = { finish() })
            }
        }
    }
}

/** Which page of Settings is showing. */
private enum class SettingsPage { LIST, ABOUT }

/**
 * Settings, and the About page it leads to.
 *
 * About is a page inside this activity rather than an activity of its own: it
 * is reached from exactly one place, and giving it its own task would mean the
 * back stack grows an entry for what is a scroll away.
 */
@Composable
private fun SettingsRoot(onBack: () -> Unit) {
    var page by remember { mutableStateOf(SettingsPage.LIST) }

    BackHandler(enabled = page != SettingsPage.LIST) { page = SettingsPage.LIST }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = page == SettingsPage.LIST,
            enter = fadeIn() + slideInHorizontally { -it / 8 },
            exit = fadeOut() + slideOutHorizontally { -it / 8 },
        ) {
            SettingsScreen(
                onBack = onBack,
                onOpenAbout = { page = SettingsPage.ABOUT },
            )
        }

        AnimatedVisibility(
            visible = page == SettingsPage.ABOUT,
            enter = fadeIn() + slideInHorizontally { it / 6 },
            exit = fadeOut() + slideOutHorizontally { it / 6 },
        ) {
            AboutScreen(onBack = { page = SettingsPage.LIST })
        }
    }
}
