/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — first-run and app-drawer entry point.
 *
 * Name      : WelcomeActivity.kt
 * Version   : 1.0.0
 * Purpose   : The one activity in this package that carries
 *             `CATEGORY_LAUNCHER`, and therefore the only way the app can be
 *             opened from anywhere except a Home press. A Home app that
 *             declared only `CATEGORY_HOME` would install successfully and then
 *             be invisible: no app-drawer entry, no icon, nothing to tap, and
 *             no route back to the app on a device where the user had not yet
 *             granted it the Home role. This screen closes that hole.
 *
 * Behaviour : Three states, in the order a new user meets them.
 *
 *               1. Not the Home app yet — explains what the launcher is and
 *                  offers the one button that matters, which opens Android's
 *                  own Home-role screen. The app never tries to claim the role
 *                  silently: the RoleManager request is the user's decision and
 *                  presenting it is the whole point of this screen.
 *               2. The Home app — a short confirmation and a button that goes
 *                  straight Home, so the drawer icon stays useful after setup
 *                  rather than becoming a dead end.
 *               3. Always — a route to Settings, because this screen is the
 *                  only surface reachable without already being inside the
 *                  launcher.
 *
 * Notes     : Deliberately a normal themed activity, not the transparent Home
 *             one. It is a dialog-like setup screen, so it owns a real window
 *             background and is not launched into the Home task. `noHistory`
 *             is not set: a user who backs out of the Home-role prompt should
 *             land back here, which is exactly what the back stack gives.
 */

package com.ihimanshunayak.liquidtab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.ThemeMode
import com.ihimanshunayak.liquidtab.settings.SettingsActivity
import com.ihimanshunayak.liquidtab.ui.theme.LiquidTabTheme
import com.ihimanshunayak.liquidtab.ui.theme.SystemBarIcons
import com.ihimanshunayak.liquidtab.util.isDefaultHome
import com.ihimanshunayak.liquidtab.util.openDefaultHomeSettings
import com.ihimanshunayak.liquidtab.util.toast

class WelcomeActivity : ComponentActivity() {

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

                val activity = this@WelcomeActivity

                // Re-read on every resume rather than every recomposition: the
                // user leaves this screen to answer Android's own prompt, and
                // what it says on the way back has to reflect that answer. A
                // value read once at first composition would still be claiming
                // the role is unset after the user had just granted it.
                var isHome by remember { mutableStateOf(isDefaultHome(activity)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isHome = isDefaultHome(activity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                WelcomeScreen(
                    isHome = isHome,
                    onSetHome = {
                        if (!openDefaultHomeSettings(activity)) {
                            toast(activity, "This device has no Home-app setting")
                        }
                        // Otherwise the prompt is now Android's; the resume
                        // observer above picks up whatever the user decided.
                    },
                    onOpenLauncher = {
                        activity.startActivity(Intent(activity, HomeActivity::class.java))
                    },
                    onOpenSettings = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    },
                )
            }
        }
    }
}

/**
 * The setup screen.
 *
 * Sized and aligned like a dialog rather than a page: there is exactly one
 * decision to make here, and a full-width tablet page would leave it stranded
 * in a corner. [widthIn] caps the column so it stays readable in landscape.
 */
@Composable
private fun WelcomeScreen(
    isHome: Boolean,
    onSetHome: () -> Unit,
    onOpenLauncher: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // The same generated mesh the Home background draws, so the
                // very first screen the user sees is recognisably this app and
                // not a generic settings form.
                Brush.linearGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.surfaceVariant,
                        scheme.background,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isHome) Icons.Rounded.CheckCircle else Icons.Rounded.Home,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(56.dp),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Liquid Tab Launcher",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (isHome) {
                    "This launcher is your Home app. Pressing Home opens it from anywhere."
                } else {
                    "A tablet Home screen built on the FreeMusic liquid-glass design. " +
                        "Set it as your Home app to use it, or open it once to look around."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            if (!isHome) {
                Button(
                    onClick = onSetHome,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Set as Home app")
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = onOpenLauncher,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isHome) "Go to Home screen" else "Open the launcher")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Launcher settings")
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
