/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — long-press menu.
 *
 * Name      : ItemMenu.kt
 * Version   : 1.0.0
 * Purpose   : What a press-and-hold on a shortcut offers: open, App info, and
 *             Remove from Home. Deliberately four items, not a context menu —
 *             a launcher's long-press is a quick correction, and every action
 *             here is one a user reaches for immediately after a mis-tap.
 *
 * Notes     : Drawn as a glass sheet rather than a dropdown anchored to the
 *             icon. Anchoring looks precise but a Home cell near the screen
 *             edge forces the menu to flip and jump; a centred sheet is always
 *             in the same place, which is what makes it fast to use twice.
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass

/**
 * The press-and-hold sheet. [onDismiss] fires on a tap outside it, which is the
 * only way out besides choosing an item — a menu that can only be dismissed by
 * choosing something is a trap.
 */
@Composable
fun ItemMenu(
    item: WorkspaceItem,
    label: String,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
    onAddWidget: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
                // Consumes taps so choosing an item does not also dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = glassContentColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )

            if (item is WorkspaceItem.App) {
                MenuRow(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = stringResource(R.string.action_open),
                    onClick = onOpen,
                )
                MenuRow(
                    icon = Icons.Rounded.Info,
                    label = stringResource(R.string.app_info),
                    onClick = onAppInfo,
                )
            }

            if (canRemove) {
                MenuRow(
                    icon = Icons.Rounded.Delete,
                    label = if (item is WorkspaceItem.Folder) {
                        stringResource(R.string.remove_folder)
                    } else {
                        stringResource(R.string.remove_from_home)
                    },
                    onClick = onRemove,
                )
            }

            Spacer(Modifier.height(4.dp))
            MenuRow(
                icon = Icons.Rounded.Widgets,
                label = stringResource(R.string.add_widget),
                onClick = onAddWidget,
            )
            MenuRow(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.reset_layout),
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glassContentColor(),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = glassContentColor(),
        )
    }
}
