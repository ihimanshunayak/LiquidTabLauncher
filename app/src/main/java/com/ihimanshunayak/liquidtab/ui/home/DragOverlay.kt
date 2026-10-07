/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — the lifted icon.
 *
 * Name      : DragOverlay.kt
 * Version   : 1.0.0
 * Purpose   : Draws the item currently under the finger, at the finger, over
 *             everything else. The cell the item came from shows an empty slot
 *             while this is up, so the two together read as "this one is in
 *             your hand".
 *
 * Notes     : Positioned from the drag's root coordinates, not from the cell's
 *             own layout. A lifted icon that travelled with its own layout
 *             would be clipped by the grid it left and could never cross into
 *             the dock — which is exactly the gesture a launcher has to support.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.Workspace
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * The item under the finger, drawn at finger position and scaled up slightly so
 * it sits above the grid it is crossing rather than among it.
 */
@Composable
fun DragOverlay(
    session: DragSession,
    workspace: Workspace,
    icons: Map<String, ImageBitmap>,
    installedApps: Map<String, AppEntry>,
    modifier: Modifier = Modifier,
) {
    val item = remember(workspace, session.itemKey) {
        workspace.pages.flatMap { it.items }
            .plus(workspace.dock)
            .firstOrNull { it.key == session.itemKey }
    } ?: return

    val showLabels by LauncherSettings.showLabels.state.collectAsStateWithLifecycle()
    val reduceMotion by LauncherSettings.reduceMotion.state.collectAsStateWithLifecycle()
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else 1.18f,
        label = "dragScale",
    )

    val density = LocalDensity.current
    val iconSize = if (session.containerId == com.ihimanshunayak.liquidtab.data.WorkspaceOps.IN_DOCK) {
        56.dp
    } else {
        // The grid's own icon box is not known here; the overlay uses a size
        // that reads as "the icon you picked up" without depending on the cell
        // it left, which may already be off screen.
        68.dp
    }
    val halfPx = with(density) { (iconSize / 2).toPx() }

    val label = when (item) {
        is WorkspaceItem.App -> installedApps[item.ref.key]?.label ?: item.ref.packageName
        is WorkspaceItem.Folder -> item.folder.name
        is WorkspaceItem.Widget -> item.kind.name.lowercase().replace('_', ' ')
    }

    Column(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (session.position.x - halfPx).roundToInt(),
                    y = (session.position.y - halfPx).roundToInt(),
                )
            }
            .scale(scale)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(iconSize)) {
            when (item) {
                is WorkspaceItem.App -> AppIcon(
                    label = label,
                    icon = icons[item.ref.key],
                    iconSize = iconSize,
                    showLabel = false,
                    pressed = true,
                    onClick = { },
                    onLongClick = { },
                    modifier = Modifier.fillMaxWidth(),
                )

                is WorkspaceItem.Folder -> FolderIcon(
                    folder = item.folder,
                    iconSize = iconSize,
                    showLabels = false,
                    pressed = true,
                    onClick = { },
                    onLongClick = { },
                    onMenuClick = { },
                )

                is WorkspaceItem.Widget -> Unit
            }
        }
        if (showLabels && item !is WorkspaceItem.Widget) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
