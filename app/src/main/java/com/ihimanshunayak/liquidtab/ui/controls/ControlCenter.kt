/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — Control Center.
 *
 * Name      : ControlCenter.kt
 * Version   : 1.0.0
 * Purpose   : The panel behind the top-right swipe: quick toggles, a brightness
 *             slider, media transport and a way through to the app library,
 *             search and settings.
 *
 * Notes     : Every tile is driven by [QuickControlsState], which reads real
 *             platform state. A tile whose platform API cannot actually perform
 *             the change is labeled with what it does instead of pretending —
 *             the Wi-Fi tile says "Wi-Fi settings" because Android no longer
 *             lets an app switch Wi-Fi off, and a launcher that hides that fact
 *             is a launcher whose controls cannot be trusted.
 */

package com.ihimanshunayak.liquidtab.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass

/**
 * The panel. [onDismiss] fires from a tap outside the sheet, so the gesture
 * that opened it can close it again.
 */
@Composable
fun ControlCenter(
    onDismiss: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = rememberQuickControlsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .width(340.dp)
                .clip(RoundedCornerShape(28.dp))
                .lightweightLiquidGlass(RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceVariant)
                // Consumes taps so choosing a tile does not also dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Connectivity ──────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    icon = Icons.Rounded.Wifi,
                    label = if (controls.wifiEnabled) "Wi-Fi on" else "Wi-Fi settings",
                    active = controls.wifiEnabled,
                    onClick = { controls.openWifiSettings() },
                    modifier = Modifier.weight(1f),
                )
                ControlTile(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    label = when (controls.ringerMode) {
                        0 -> "Silent"
                        1 -> "Vibrate"
                        else -> "Sound"
                    },
                    active = controls.ringerMode != 0,
                    onClick = { controls.cycleRinger() },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    icon = Icons.Rounded.NotificationsOff,
                    label = if (controls.dndEnabled) "Do not disturb on" else "Do not disturb",
                    active = controls.dndEnabled,
                    onClick = { controls.toggleDnd() },
                    modifier = Modifier.weight(1f),
                )
                ControlTile(
                    icon = Icons.Rounded.FlashlightOn,
                    label = if (controls.torchEnabled) "Torch on" else "Torch",
                    active = controls.torchEnabled,
                    enabled = controls.torchAvailable,
                    onClick = { controls.toggleTorch() },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    icon = Icons.Rounded.ScreenRotation,
                    label = if (controls.rotationLocked) "Rotation locked" else "Auto-rotate",
                    active = controls.rotationLocked,
                    onClick = { controls.toggleRotationLock() },
                    modifier = Modifier.weight(1f),
                )
                ControlTile(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    active = false,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Brightness ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Brightness6,
                    contentDescription = null,
                    tint = glassContentColor().copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Slider(
                    value = controls.brightness,
                    onValueChange = { controls.applyBrightness(it) },
                    valueRange = 0.02f..1f,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Destinations ──────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    active = false,
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f),
                )
                ControlTile(
                    icon = Icons.Rounded.Apps,
                    label = "All apps",
                    active = false,
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One tile. [active] fills it, [enabled] greys it; a tile whose platform cannot
 * do the thing is still tappable when it opens a system panel instead.
 */
@Composable
private fun ControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    val tint = when {
        !enabled -> glassContentColor().copy(alpha = 0.30f)
        active -> glassContentColor()
        else -> glassContentColor().copy(alpha = 0.70f)
    }
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(shape)
            .background(
                if (active) {
                    glassContentColor().copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
