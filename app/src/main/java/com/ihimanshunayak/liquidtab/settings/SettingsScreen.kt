/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — Settings screen.
 *
 * Name      : SettingsScreen.kt
 * Version   : 1.0.0
 * Purpose   : Every preference the launcher actually honours, and nothing
 *             else. A settings row that does not change behaviour is a promise
 *             the app cannot keep, so this screen is built directly off
 *             [LauncherSettings] and a new row is only added alongside the code
 *             that reads it.
 *
 * Notes     : Rows write through [LauncherSettings] and read the same
 *             StateFlows the launcher itself reads, so the screen cannot drift
 *             from what the Home screen is doing. Each row collects only its
 *             own setting, so toggling one re-composes that row rather than the
 *             whole list.
 */

package com.ihimanshunayak.liquidtab.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LibraryStyle
import com.ihimanshunayak.liquidtab.data.ThemeMode
import com.ihimanshunayak.liquidtab.data.WallpaperMode
import com.ihimanshunayak.liquidtab.ui.glass.isGlassSupported
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SettingsTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSection(title = "Appearance") {
                    val themeMode by LauncherSettings.themeMode.state.collectAsStateWithLifecycle()
                    SegmentRow(
                        title = "Theme",
                        options = listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                        ),
                        selected = themeMode,
                        onSelect = { LauncherSettings.themeMode.value = it },
                    )
                    RowDivider()
                    val glassEnabled by LauncherSettings.glassEnabled.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = "Liquid glass",
                        subtitle = if (isGlassSupported()) {
                            "Blurred, refractive surfaces over the Home background"
                        } else {
                            "Unavailable on this Android version — needs Android 12"
                        },
                        checked = glassEnabled,
                        enabled = isGlassSupported(),
                        onChange = { LauncherSettings.glassEnabled.value = it },
                    )
                    RowDivider()
                    val reduceBlur by LauncherSettings.reduceDynamicBlur.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = "Reduce dynamic blur",
                        subtitle = "Fills glass surfaces solid instead of sampling behind them",
                        checked = reduceBlur,
                        enabled = true,
                        onChange = { LauncherSettings.reduceDynamicBlur.value = it },
                    )
                    RowDivider()
                    val showLabels by LauncherSettings.showLabels.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = "Show app names",
                        subtitle = "Draws each shortcut's label under its icon",
                        checked = showLabels,
                        enabled = true,
                        onChange = { LauncherSettings.showLabels.value = it },
                    )
                    RowDivider()
                    val iconScale by LauncherSettings.iconScale.state.collectAsStateWithLifecycle()
                    SliderRow(
                        title = "Icon size",
                        value = iconScale,
                        valueRange = 0.8f..1.2f,
                        steps = 3,
                        label = { scale -> "${(scale * 100).toInt()}%" },
                        onChange = { LauncherSettings.iconScale.value = it },
                    )
                    RowDivider()
                    val columns by LauncherSettings.gridColumns.state.collectAsStateWithLifecycle()
                    SliderRow(
                        title = "Grid columns",
                        value = columns.toFloat(),
                        valueRange = 0f..10f,
                        steps = 9,
                        label = { value -> value.toInt().let { if (it == 0) "Automatic" else "$it columns" } },
                        onChange = { LauncherSettings.gridColumns.value = it.toInt() },
                    )
                }
            }

            item {
                SettingsSection(title = "Dock") {
                    val dockMax by LauncherSettings.dockMaxItems.state.collectAsStateWithLifecycle()
                    SliderRow(
                        title = "Maximum apps in the dock",
                        value = dockMax.toFloat(),
                        valueRange = 3f..8f,
                        steps = 4,
                        label = { count -> count.toInt().toString() },
                        onChange = { LauncherSettings.dockMaxItems.value = it.toInt() },
                    )
                }
            }

            item {
                SettingsSection(title = "Background") {
                    val wallpaperMode by LauncherSettings.wallpaperMode.state.collectAsStateWithLifecycle()
                    SegmentRow(
                        title = "Source",
                        options = listOf(
                            WallpaperMode.GRADIENT to "Gradient",
                            WallpaperMode.IMAGE to "Photo",
                            WallpaperMode.ARTWORK to "Now playing",
                        ),
                        selected = wallpaperMode,
                        onSelect = { LauncherSettings.wallpaperMode.value = it },
                    )
                    RowDivider()
                    val parallax by LauncherSettings.parallax.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = "Parallax",
                        subtitle = "Moves the background slightly as pages change",
                        checked = parallax,
                        enabled = true,
                        onChange = { LauncherSettings.parallax.value = it },
                    )
                }
            }

            item {
                SettingsSection(title = "Behaviour") {
                    val reduceMotion by LauncherSettings.reduceMotion.state.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = "Reduce motion",
                        subtitle = "Drops the scale and lift animations during a drag",
                        checked = reduceMotion,
                        enabled = true,
                        onChange = { LauncherSettings.reduceMotion.value = it },
                    )
                    RowDivider()
                    val libraryStyle by LauncherSettings.libraryStyle.state.collectAsStateWithLifecycle()
                    SegmentRow(
                        title = "App library",
                        options = listOf(
                            LibraryStyle.LIST to "List",
                            LibraryStyle.GRID to "Grid",
                        ),
                        selected = libraryStyle,
                        onSelect = { LauncherSettings.libraryStyle.value = it },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Chrome ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Launcher settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * A titled group of rows in one glass plate. Rows come in as a [ColumnScope]
 * block rather than a list of lambdas, which is what lets each row read its own
 * setting and only that setting.
 */
@Composable
private fun SettingsSection(
    title: String,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant),
            content = rows,
        )
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    )
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
        )
    }
}

/**
 * A segmented choice. Drawn as a row of pills rather than a dropdown: the
 * options are three at most, and seeing all of them beats opening a menu to
 * find out what they are.
 */
@Composable
private fun <T> SegmentRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val active = value == selected
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.14f else 0.04f),
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
