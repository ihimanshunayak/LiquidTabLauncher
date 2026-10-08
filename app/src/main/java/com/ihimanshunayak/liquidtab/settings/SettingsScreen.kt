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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.LibraryStyle
import com.ihimanshunayak.liquidtab.data.ThemeMode
import com.ihimanshunayak.liquidtab.data.WallpaperMode
import com.ihimanshunayak.liquidtab.data.Workspace
import com.ihimanshunayak.liquidtab.data.WorkspaceOps
import com.ihimanshunayak.liquidtab.ui.glass.isGlassSupported
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics
import com.ihimanshunayak.liquidtab.ui.home.PagePreview
import com.ihimanshunayak.liquidtab.ui.home.workspaceLabels
import com.ihimanshunayak.liquidtab.util.toast

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
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
                    // Only offered while parallax is on: a strength slider for a
                    // disabled effect is a control that cannot change anything.
                    if (parallax) {
                        RowDivider()
                        val amount by LauncherSettings.wallpaperParallaxAmount.state
                            .collectAsStateWithLifecycle()
                        SliderRow(
                            title = "Parallax strength",
                            value = amount,
                            valueRange = 0.05f..0.40f,
                            steps = 6,
                            label = { value -> "${(value * 100).toInt()}%" },
                            onChange = { LauncherSettings.wallpaperParallaxAmount.value = it },
                        )
                    }
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

            item {
                PagesSection()
            }

            item {
                SettingsSection(title = "About") {
                    NavRow(
                        title = "About Liquid Tab Launcher",
                        subtitle = "Version, device, Home status and software notices",
                        onClick = onOpenAbout,
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Pages.
 *
 * The same page operations the Home long-press sheet offers, in the place a
 * user who went looking through Settings rather than through a gesture will
 * find them. Both entry points call the same [WorkspaceOps] functions, so a
 * page added here behaves exactly like a page added there.
 */
@Composable
private fun PagesSection() {
    val context = LocalContext.current
    val workspace by LauncherStore.workspace.collectAsStateWithLifecycle()
    val labels = remember(workspace) { workspaceLabels(workspace, emptyMap()) }

    SettingsSection(title = "Home pages") {
        PagesStrip(
            workspace = workspace,
            labels = labels,
            onAddPage = { LauncherStore.update { WorkspaceOps.addPage(it) } },
            onSetHomePage = { pageId ->
                LauncherStore.update { WorkspaceOps.setDefaultPage(it, pageId) }
                val number = workspace.pages.indexOfFirst { page -> page.id == pageId } + 1
                toast(context, "A Home press will open page $number")
            },
        )
        RowDivider()
        val showIndicator by LauncherSettings.showPageIndicator.state.collectAsStateWithLifecycle()
        SwitchRow(
            title = "Show page indicator",
            subtitle = "Dots under the grid, each one a way to reach its page",
            checked = showIndicator,
            enabled = true,
            onChange = { LauncherSettings.showPageIndicator.value = it },
        )
    }
}

/**
 * The page strip: a card per page, plus an add action.
 *
 * It reads as the same film-strip the Home sheet shows, using the same preview
 * composable, because a page that looks different in two places is a page the
 * user has to re-learn. The Home-page marker is a pin rather than a highlight:
 * the highlighted card in this strip is the one being *chosen*, and a user
 * tapping through pages must be able to see which one Home already opens.
 */
@Composable
private fun PagesStrip(
    workspace: Workspace,
    labels: Map<String, String>,
    onAddPage: () -> Unit,
    onSetHomePage: (String) -> Unit,
) {
    val haptics = rememberHaptics()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        workspace.pages.forEachIndexed { index, page ->
            val isHome = page.id == workspace.defaultPageId ||
                (workspace.defaultPageId == null && index == 0)
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Make page ${index + 1} the Home page",
                        onClick = {
                            haptics.play(Haptic.Select)
                            onSetHomePage(page.id)
                        },
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PagePreview(page = page, labels = labels)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isHome) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = if (isHome) "Page ${index + 1} · Home" else "Page ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHome) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .width(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClickLabel = "Add a page", onClick = onAddPage)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = "Add page",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
                .clickable(onClickLabel = "Back", onClick = onBack),
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
            // The whole row toggles, not just the switch: a 48 dp pill on the
            // far right is a small target for a setting whose label spans the
            // row. The switch keeps its own state but stops handling input so
            // a reader announces this as one switch, not a switch plus text.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
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
            onCheckedChange = null,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // One node per option, each announcing which one is chosen.
            modifier = Modifier.selectableGroup(),
        ) {
            options.forEach { (value, label) ->
                val active = value == selected
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.14f else 0.04f),
                        )
                        .selectable(
                            selected = active,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
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
            // The label is drawn above the track, but a reader walking the
            // controls hears only the slider, so it has to carry the name.
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
