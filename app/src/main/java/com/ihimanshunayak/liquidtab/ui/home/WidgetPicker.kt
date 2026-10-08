/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — the widget picker.
 *
 * Name      : WidgetPicker.kt
 * Version   : 1.0.0
 * Purpose   : Adds a widget to Home, and lists the ones already there so they
 *             can be taken away again. Every widget the launcher ships is
 *             describable here without a second registry: the catalogue is
 *             derived from [WidgetKind], so adding a widget means adding a case
 *             to the enum and a renderer — nothing in this file.
 *
 * Notes     : A widget is a Home item like any other, so adding one is
 *             [WorkspaceOps.add] and removing one is [WorkspaceOps.remove]. The
 *             picker never edits the workspace itself; it reports intent and the
 *             caller writes, which keeps every edit going through one place.
 *
 *             Each entry previews what the widget actually is rather than
 *             showing a generic tile: the catalogue is eight items and a user
 *             choosing a clock should see a clock.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.data.WidgetKind
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass

/**
 * The widget catalogue.
 *
 * [present] is the set of widget kinds already on any Home page, so an entry
 * that is in use offers removal instead of a second copy — two clocks on one
 * page is always a mistake, and the launcher should not make it easy.
 */
@Composable
fun WidgetPicker(
    present: Set<WidgetKind>,
    onAdd: (WidgetKind) -> Unit,
    onRemove: (WidgetKind) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .heightIn(max = 560.dp)
                .clip(shape)
                .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.widget_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = glassContentColor(),
                    )
                    Text(
                        text = stringResource(R.string.widget_picker_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = glassContentColor().copy(alpha = 0.65f),
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            onClickLabel = stringResource(R.string.a11y_close_widget_picker),
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.a11y_close_widget_picker),
                        tint = glassContentColor().copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.size(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = WidgetKind.entries, key = { it.name }) { kind ->
                    WidgetPickerRow(
                        kind = kind,
                        added = kind in present,
                        onAdd = { onAdd(kind) },
                        onRemove = { onRemove(kind) },
                    )
                }
            }
        }
    }
}

/** One catalogue entry: what the widget is, and whether it is already on Home. */
@Composable
private fun WidgetPickerRow(
    kind: WidgetKind,
    added: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glassContentColor().copy(alpha = 0.07f))
            .clickable(
                onClickLabel = if (added) {
                    stringResource(R.string.a11y_remove_widget, stringResource(kind.nameRes))
                } else {
                    stringResource(R.string.a11y_add_widget, stringResource(kind.nameRes))
                },
            ) { if (added) onRemove() else onAdd() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(kind.nameRes),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = glassContentColor(),
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(kind.summaryRes),
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.7f),
            )
        }

        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (added) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (added) Icons.Rounded.Remove else Icons.Rounded.Add,
                contentDescription = if (added) {
                    stringResource(R.string.a11y_remove_widget_short, stringResource(kind.nameRes))
                } else {
                    stringResource(R.string.a11y_add_widget_short, stringResource(kind.nameRes))
                },
                tint = if (added) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

