/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — page management.
 *
 * Name      : PageManager.kt
 * Version   : 1.0.0
 * Purpose   : The sheet that treats pages as objects rather than as somewhere
 *             the finger happens to be: reorder them, delete them, and choose
 *             which one a Home press returns to. Every other launcher gesture
 *             works on the page you are standing on; a page you arranged three
 *             screens ago needs a place that shows all of them at once.
 *
 * Notes     : Each card previews the page as a miniature of the real grid —
 *             the same row-major flow, in the same column count — so the cards
 *             read as a film-strip of screens rather than as a list of page
 *             names. Items that do not fit the tallest card are counted rather
 *             than dropped, because "and four more" is information while a
 *             silent overflow is a lie about what the page holds.
 *
 *             The sheet never edits the workspace itself; it calls the store,
 *             so the placement rules stay in [WorkspaceOps] where they are
 *             unit-tested and the UI stays a caller.
 */

package com.ihimanshunayak.liquidtab.ui.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.Workspace
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.data.WorkspaceOps
import com.ihimanshunayak.liquidtab.data.WorkspacePage
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics

/** How many miniature cells a card previews; the rest are counted. */
private const val PREVIEW_CELLS = 12
private const val PREVIEW_COLUMNS = 4

/**
 * The page manager sheet.
 *
 * [labels] resolves an item key to the text its cell shows, and
 * [visiblePageId] — the page the pager is on — is marked so the sheet starts
 * from where the user already is.
 */
@Composable
fun PageManager(
    workspace: Workspace,
    labels: Map<String, String>,
    visiblePageId: String?,
    onDismiss: () -> Unit,
    onCreatePage: () -> Unit,
    onOpenPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
                // Consumes taps so acting on a card does not also dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Pages",
                        style = MaterialTheme.typography.titleMedium,
                        color = glassContentColor(),
                    )
                    Text(
                        text = "Long-press a page to make it the Home page",
                        style = MaterialTheme.typography.labelMedium,
                        color = glassContentColor().copy(alpha = 0.6f),
                    )
                }
                val addShape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .clip(addShape)
                        .background(glassContentColor().copy(alpha = 0.12f))
                        .clickable {
                            haptics.play(Haptic.ToggleOn)
                            onCreatePage()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = glassContentColor(),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Add page",
                        style = MaterialTheme.typography.labelLarge,
                        color = glassContentColor(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                workspace.pages.forEachIndexed { index, page ->
                    PageCard(
                        page = page,
                        pageNumber = index + 1,
                        labels = labels,
                        isVisible = page.id == visiblePageId,
                        isDefault = page.id == workspace.defaultPageId,
                        canDelete = workspace.pages.size > 1,
                        canMoveLeft = index > 0,
                        canMoveRight = index < workspace.pages.lastIndex,
                        onOpen = { onOpenPage(page.id) },
                        onMoveLeft = {
                            haptics.play(Haptic.Tick)
                            LauncherStore.update { WorkspaceOps.movePage(it, page.id, index - 1) }
                        },
                        onMoveRight = {
                            haptics.play(Haptic.Tick)
                            LauncherStore.update { WorkspaceOps.movePage(it, page.id, index + 1) }
                        },
                        onSetDefault = {
                            haptics.play(Haptic.Select)
                            LauncherStore.update { WorkspaceOps.setDefaultPage(it, page.id) }
                        },
                        onDelete = {
                            haptics.play(Haptic.ToggleOff)
                            LauncherStore.update { WorkspaceOps.removePage(it, page.id) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * One page: its mini grid, its number, and its controls.
 *
 * The two halves are separate tap targets on purpose. Tapping the preview is
 * the gesture that opens a page, which is what a user expects from a
 * thumbnail; the buttons underneath are the destructive and structural edits
 * and must never be one stray tap away from the preview.
 */
@Composable
private fun PageCard(
    page: WorkspacePage,
    pageNumber: Int,
    labels: Map<String, String>,
    isVisible: Boolean,
    isDefault: Boolean,
    canDelete: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onOpen: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(page.id) {
                    detectTapGestures(
                        onTap = { onOpen() },
                        onLongPress = { onSetDefault() },
                    )
                },
        ) {
            PagePreview(page = page, labels = labels)
            if (isDefault) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(50))
                        .background(glassContentColor().copy(alpha = 0.16f))
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Home page",
                        tint = glassContentColor(),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageCardAction(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                label = "Move page $pageNumber left",
                enabled = canMoveLeft,
                onClick = onMoveLeft,
            )
            Text(
                text = if (isVisible) "$pageNumber •" else "$pageNumber",
                style = MaterialTheme.typography.labelMedium,
                color = glassContentColor().copy(alpha = if (isVisible) 1f else 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PageCardAction(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                label = "Move page $pageNumber right",
                enabled = canMoveRight,
                onClick = onMoveRight,
            )
            PageCardAction(
                icon = Icons.Rounded.Delete,
                label = "Delete page $pageNumber",
                enabled = canDelete,
                onClick = onDelete,
            )
        }

        if (isDefault) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = glassContentColor().copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "Home page",
                    style = MaterialTheme.typography.labelSmall,
                    color = glassContentColor().copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PageCardAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = glassContentColor().copy(alpha = if (enabled) 0.85f else 0.22f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The miniature of the page: the real grid, shrunk to fit a card. Shared with
 * the Home sheet's page strip so a page looks the same wherever it is previewed.
 */
@Composable
internal fun PagePreview(
    page: WorkspacePage,
    labels: Map<String, String>,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glassContentColor().copy(alpha = 0.07f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val shown = page.items.take(PREVIEW_CELLS)
        shown.chunked(PREVIEW_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    MiniCell(
                        item = item,
                        label = labels[item.key] ?: "",
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row aligned with the cells above it.
                repeat(PREVIEW_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (page.items.size > PREVIEW_CELLS) {
            Text(
                text = "+${page.items.size - PREVIEW_CELLS} more",
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
        if (page.items.isEmpty()) {
            Text(
                text = "Empty",
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

/** One miniature cell: the item's first letter, or a bar when it is a widget. */
@Composable
private fun MiniCell(
    item: WorkspaceItem,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(if (item is WorkspaceItem.Widget) 10.dp else 22.dp)
            .clip(RoundedCornerShape(if (item is WorkspaceItem.Widget) 3.dp else 7.dp))
            .background(glassContentColor().copy(alpha = if (item is WorkspaceItem.Widget) 0.10f else 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item !is WorkspaceItem.Widget) {
            Text(
                text = label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}
