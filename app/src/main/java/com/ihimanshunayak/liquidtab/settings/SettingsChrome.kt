/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — shared settings chrome.
 *
 * Name      : SettingsChrome.kt
 * Version   : 1.0.0
 * Purpose   : The plate, the rows and the divider that both the Settings screen
 *             and the About screen are built from. They live here rather than in
 *             either screen because the two screens are the same visual object
 *             with different contents: a change to the plate belongs in one
 *             place, and a row that looks right on one screen and wrong on the
 *             other is a bug that only exists because the markup was copied.
 *
 * Notes     : Rows take their content as parameters and do not read any store,
 *             which is what lets the Settings screen give each row its own
 *             `collectAsStateWithLifecycle` and re-compose one row rather than
 *             the whole list.
 */

package com.ihimanshunayak.liquidtab.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass

/**
 * A titled group of rows in one glass plate.
 *
 * Rows arrive as a [ColumnScope] block rather than as a list of lambdas, which
 * is what lets each row read its own setting and only that setting.
 */
@Composable
internal fun SettingsSection(
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

/**
 * The hairline between two rows.
 *
 * Inset from the left so it starts where the text does rather than at the
 * plate's edge — the plate is a surface, and a rule that crosses all of it reads
 * as a border rather than as a separator.
 */
@Composable
internal fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    )
}

/**
 * A row whose value is a fact: label on the left, value on the right.
 *
 * The value is right-aligned so a column of them lines up and can be read as a
 * column, which is the whole reason to lay facts out this way.
 */
@Composable
internal fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A row that goes somewhere. The whole row is the target, not a chevron: a
 * chevron is a hint, and a hint is not a hit area.
 */
@Composable
internal fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
}

/** A [FactRow] whose value is a link, marked with the glyph that says so. */
@Composable
internal fun LinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "$label: $value", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
