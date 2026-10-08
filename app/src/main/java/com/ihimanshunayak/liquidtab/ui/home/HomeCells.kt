/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher - Home cell, folder and widget slot.
 *
 * Name      : HomeCells.kt
 * Version   : 1.0.0
 * Purpose   : One cell of the Home grid: an app shortcut, a folder, or a widget
 *             strip. Owns the drag gesture, the drop highlight, and registering
 *             its own bounds with the CellRegistry so a finger position can be
 *             resolved back to an item.
 *
 * Notes     : The drag is driven by pointerInput + detectDragGesturesAfterLongPress
 *             rather than by a reorder helper: a launcher needs the drag to
 *             begin from an icon that is already lifted, to track in root
 *             coordinates so it can cross into the dock, and to be cancelled
 *             exactly when the finger lifts. Those are three things the generic
 *             helpers do not expose.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.AppRef
import com.ihimanshunayak.liquidtab.data.FolderRef
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.WidgetKind
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.data.WorkspaceOps
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics

/**
 * One grid cell. Registers its bounds on every layout pass, drives the drag and
 * draws the drop highlight when the current target is this cell.
 */
@Composable
fun WorkspaceCell(
    item: WorkspaceItem,
    index: Int,
    containerId: String,
    apps: Map<String, AppEntry>,
    icons: Map<String, ImageBitmap>,
    iconSize: Dp,
    showLabels: Boolean,
    reduceMotion: Boolean,
    dragState: DragSessionState,
    registry: CellRegistry,
    onOpen: (AppRef) -> Unit,
    onMenu: (WorkspaceItem) -> Unit,
    onOpenFolder: (String) -> Unit,
    targetHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val session = dragState.session
    val isDragging = session?.itemKey == item.key

    // A folder answers drops anywhere on its icon, so a page cell holding a
    // folder registers a second time as a folder target. The index reported is
    // the folder's own end, not the cell's position in the grid: a dropped app
    // is appended inside the folder, which is what the icon it landed on shows.
    val folder = (item as? WorkspaceItem.Folder)?.folder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                registry.registerPageCell(
                    pageId = containerId,
                    key = item.key,
                    index = index,
                    bounds = coordinates.boundsInRoot(),
                    folderId = folder?.id,
                    folderIndex = folder?.items?.size ?: 0,
                )
            }
            .pointerInput(item.key, reduceMotion) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        haptics.play(Haptic.Expand)
                        dragState.start(item.key, containerId, start)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val current = dragState.session ?: return@detectDragGesturesAfterLongPress
                        val moved = current.position + amount
                        dragState.move(moved, registry.targetAt(moved, current.itemKey))
                    },
                    onDragEnd = {
                        dragState.end()?.let { finished ->
                            LauncherStore.update { WorkspaceOps.applyDrop(it, finished) }
                        }
                    },
                    onDragCancel = { dragState.end() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isDragging) {
            // The slot the icon left behind. Drawn as an empty plate rather than
            // by hiding the icon, so the grid does not reflow under the finger.
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(percent = 23))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            )
        } else if (targetHighlighted) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, glassContentColor().copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            )
        }

        when (item) {
            is WorkspaceItem.App -> AppIcon(
                label = apps[item.ref.key]?.label ?: item.ref.packageName,
                icon = icons[item.ref.key],
                iconSize = iconSize,
                showLabel = showLabels,
                pressed = isDragging,
                onClick = { onOpen(item.ref) },
                onLongClick = { onMenu(item) },
            )

            is WorkspaceItem.Folder -> FolderIcon(
                folder = item.folder,
                iconSize = iconSize,
                showLabels = showLabels,
                pressed = isDragging,
                // A tap opens the folder; long-press still offers the menu, so
                // renaming and removing stay one gesture away.
                onClick = { onOpenFolder(item.folder.id) },
                onLongClick = { onMenu(item) },
                onMenuClick = { onMenu(item) },
            )

            is WorkspaceItem.Widget -> WidgetSlot(
                kind = item.kind,
                onLongClick = { onMenu(item) },
                onDragStart = { position ->
                    haptics.play(Haptic.Expand)
                    dragState.start(item.key, containerId, position)
                },
                onDrag = { amount ->
                    val current = dragState.session ?: return@WidgetSlot
                    val moved = current.position + amount
                    dragState.move(moved, registry.targetAt(moved, current.itemKey))
                },
                onDragEnd = {
                    dragState.end()?.let { finished ->
                        LauncherStore.update { WorkspaceOps.applyDrop(it, finished) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )        }
    }
}

/**
 * A folder: a glass plate carrying its name and how many apps are inside.
 *
 * Deliberately not a 2x2 of the folder's own icons - miniatures on a glass tile
 * read as clutter at icon size, and the count is the thing a user actually
 * wants from the Home screen. The contents are one tap away.
 */
@Composable
fun FolderIcon(
    folder: FolderRef,
    iconSize: Dp,
    showLabels: Boolean,
    pressed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppIcon(
        label = folder.name,
        icon = null,
        iconSize = iconSize,
        showLabel = showLabels,
        pressed = pressed,
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick,
        modifier = modifier,
        background = {
            // Three soft rows, one per app up to a handful: enough to say "there
            // is more than one thing in here" without drawing miniatures.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(folder.items.size.coerceIn(2, 4)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(glassContentColor().copy(alpha = 0.20f)),
                    )
                }
            }
        },
        badge = {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = folder.items.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = glassContentColor(),
                )
            }
        },
    )
}

/**
 * A widget strip on the Home grid. The plate and the drag gesture live here;
 * what is drawn on it is the renderer's business, so a widget that changes its
 * contents never touches the grid.
 */
@Composable
fun WidgetSlot(
    kind: WidgetKind,
    onLongClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(kind) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start -> onDragStart(start) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .pointerInput(kind) { detectTapGestures(onLongPress = { onLongClick() }) },
    ) {
        com.ihimanshunayak.liquidtab.ui.widgets.WidgetContent(kind)
    }
}