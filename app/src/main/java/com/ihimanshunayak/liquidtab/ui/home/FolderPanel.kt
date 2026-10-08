/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — folder contents.
 *
 * Name      : FolderPanel.kt
 * Version   : 1.0.0
 * Purpose   : What opens when a folder is tapped: its apps, its name, and the
 *             three things a user wants to do with them — launch one, take one
 *             out, or rename the folder.
 *
 *             A folder that cannot be opened is a folder that only collects
 *             icons, so this is the other half of the folder feature: the create
 *             path lives in [WorkspaceOps.createFolder], and everything after
 *             that happens here.
 *
 * Notes     : Apps are added to a folder by dragging onto it on the Home screen;
 *             this panel's job is removal and launch. Adding an app from inside
 *             the panel would need a second picker surface for a gesture that
 *             already works, so it is deliberately absent.
 *
 *             The panel reads the folder out of the live workspace on every
 *             recomposition rather than holding a copy: a folder that loses its
 *             last two apps dissolves itself, and the panel has to see that
 *             happen rather than draw a folder that no longer exists.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.AppRef
import com.ihimanshunayak.liquidtab.data.FolderRef
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics

/**
 * The folder sheet.
 *
 * [onRemoveApp] is the only mutation this panel performs; rename goes through
 * [onRename] so the caller owns the workspace write, which keeps every edit in
 * one place.
 */
@Composable
fun FolderPanel(
    folder: FolderRef,
    apps: Map<String, AppEntry>,
    icons: Map<String, ImageBitmap>,
    iconSize: androidx.compose.ui.unit.Dp,
    onOpen: (AppRef) -> Unit,
    onRemoveApp: (AppRef) -> Unit,
    onRename: (String) -> Unit,
    onOpenAppInfo: (AppRef) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingName by remember(folder.id) { mutableStateOf(false) }
    var draftName by remember(folder.id) { mutableStateOf(folder.name) }
    val haptics = rememberHaptics()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(max = 520.dp)
                .clip(shape)
                .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
                // Consumes taps so a stray tap inside does not dismiss the panel
                // the user is aiming at.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            FolderHeader(
                name = folder.name,
                editing = editingName,
                draft = draftName,
                onDraftChange = { draftName = it },
                onStartEditing = {
                    draftName = folder.name
                    editingName = true
                },
                onCommit = {
                    val cleaned = draftName.trim()
                    if (cleaned.isNotEmpty() && cleaned != folder.name) onRename(cleaned)
                    editingName = false
                },
                onCancel = { editingName = false },
                onClose = onDismiss,
                count = folder.items.size,
            )

            Spacer(Modifier.size(16.dp))

            if (folder.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.folder_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassContentColor().copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(
                        items = folder.items,
                        key = { _, ref -> ref.key },
                    ) { _, ref ->
                        FolderAppTile(
                            label = apps[ref.key]?.label ?: ref.className ?: ref.packageName,
                            icon = icons[ref.key],
                            iconSize = iconSize,
                            onClick = {
                                haptics.play(Haptic.Tap)
                                onOpen(ref)
                            },
                            onLongClick = {
                                haptics.play(Haptic.Expand)
                                onRemoveApp(ref)
                            },
                            onInfo = { onOpenAppInfo(ref) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.folder_long_press_hint),
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Name, edit affordance and close button for the panel. */
@Composable
private fun FolderHeader(
    name: String,
    editing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    count: Int,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = glassContentColor(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glassContentColor().copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            TextAction(label = stringResource(R.string.save), onClick = {
                keyboard?.hide()
                onCommit()
            })
            TextAction(label = stringResource(R.string.cancel), onClick = {
                keyboard?.hide()
                onCancel()
            })
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = glassContentColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.folder_app_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = glassContentColor().copy(alpha = 0.65f),
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .combinedClickable(onClick = onStartEditing),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.rename_folder),
                    tint = glassContentColor().copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .combinedClickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close_folder),
                tint = glassContentColor().copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One app inside the folder: icon, label, and long-press to remove. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FolderAppTile(
    label: String,
    icon: ImageBitmap?,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            // The tile, its label and its Info action are one target; the Info
            // row stays its own node so it can still be reached on its own.
            .semantics(mergeDescendants = true) { contentDescription = label }
            .combinedClickable(
                onClickLabel = stringResource(R.string.a11y_open_app, label),
                onLongClickLabel = stringResource(R.string.a11y_remove_from_folder, label),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(percent = 23))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(iconSize * 0.06f),
                )
            } else {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = (iconSize.value * 0.36f).sp,
                    ),
                    color = glassContentColor().copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = glassContentColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(2.dp))
        TextAction(label = stringResource(R.string.app_info), onClick = onInfo)
    }
}

/** A small text button, sized for a glass sheet rather than a form. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
