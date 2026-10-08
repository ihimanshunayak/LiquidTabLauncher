/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — app library and search.
 *
 * Name      : AppLibrary.kt
 * Version   : 1.0.0
 * Purpose   : Every installed app in one place, with a search field that filters
 *             as you type and a style the user picks (list or grid).
 *
 * Notes     : The filter runs over an index built once per app-list change
 *             rather than over the list itself. Typing a character into a
 *             launcher should cost a lookup, not a `lowercase()` of four
 *             hundred labels — which is what makes it possible to keep the
 *             result list honest as the query grows.
 *
 *             Matching is prefix-first: a query that starts an app's name ranks
 *             above one that appears inside it, so "ca" offers Calculator before
 *             it offers a calendar app's subtitle. That ordering is the whole
 *             reason a launcher's search feels different from a file manager's.
 */

package com.ihimanshunayak.liquidtab.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LibraryStyle
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import java.util.Locale

/**
 * The library. [query] is hoisted so the search field and the result list share
 * one source of truth, and so a caller can open the library already filtered —
 * which is how the Control Center's search affordance works.
 *
 * [autofocus] raises the keyboard as soon as the library appears, which is what
 * makes it usable as a search palette rather than only as a list.
 */
@Composable
fun AppLibrary(
    apps: List<AppEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    autofocus: Boolean = false,
) {
    val style by LauncherSettings.libraryStyle.state.collectAsStateWithLifecycle()
    val index = remember(apps) { SearchIndex(apps) }
    val results = remember(index, query) { index.search(query) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autofocus) {
        if (autofocus) runCatching { focusRequester.requestFocus() }
    }

    Column(modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isBlank()) {
                        stringResource(R.string.library_empty)
                    } else {
                        stringResource(R.string.library_no_match, query)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassContentColor().copy(alpha = 0.6f),
                )
            }
            return@Column
        }

        when (style) {
            LibraryStyle.LIST -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results, key = { it.key }) { entry ->
                    LibraryRow(
                        entry = entry,
                        onClick = { onOpen(entry) },
                        onLongClick = { onLongPress(entry) },
                    )
                }
            }

            LibraryStyle.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.key }) { entry ->
                    LibraryTile(
                        entry = entry,
                        onClick = { onOpen(entry) },
                        onLongClick = { onLongPress(entry) },
                    )
                }
            }
        }
    }
}

// ── Search field ──────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = glassContentColor().copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassContentColor().copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = glassContentColor()),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (focusRequester != null) base.focusRequester(focusRequester) else base
                    },
            )
        }
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    entry: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rowDescription = stringResource(
        R.string.a11y_app_and_package,
        entry.label,
        entry.packageName,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            // One node per row: the plate, label and package name are one tap
            // target, so a reader should stop once and announce them together.
            // The description is resolved before the block: a semantics block is
            // a snapshot lambda, not a composable scope.
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            }
            .combinedClickable(
                onClickLabel = stringResource(R.string.a11y_open_app, entry.label),
                onLongClickLabel = stringResource(R.string.a11y_app_options, entry.label),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The plate stands in for the icon: this list is drawn before the icon
        // pass completes, and a row that appears a beat later than its label
        // reads as a stutter.
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(percent = 23))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = glassContentColor().copy(alpha = 0.7f),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = glassContentColor(),
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = glassContentColor().copy(alpha = 0.45f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(
    entry: AppEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { contentDescription = entry.label }
            .combinedClickable(
                onClickLabel = stringResource(R.string.a11y_open_app, entry.label),
                onLongClickLabel = stringResource(R.string.a11y_app_options, entry.label),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(percent = 23))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = glassContentColor().copy(alpha = 0.7f),
            )
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = glassContentColor(),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ── Index ─────────────────────────────────────────────────────────────────────

/**
 * A prepared view of the app list for searching.
 *
 * Holds each app's label lowercased once, so a keystroke does a lookup rather
 * than a case fold of the whole catalogue, and keeps the original list so
 * results come back as the values the caller passed in.
 */
private class SearchIndex(private val apps: List<AppEntry>) {

    private class Indexed(
        val entry: AppEntry,
        val label: String,
        val packageName: String,
    )

    private val indexed = apps.map {
        Indexed(
            entry = it,
            label = it.label.lowercase(Locale.getDefault()),
            packageName = it.packageName.lowercase(Locale.getDefault()),
        )
    }

    /**
     * Prefix matches first, then substring matches, then package-name matches.
     * A blank query returns everything, in the list's own order.
     */
    fun search(rawQuery: String): List<AppEntry> {
        val query = rawQuery.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) return apps

        val prefix = ArrayList<AppEntry>(8)
        val contains = ArrayList<AppEntry>(16)
        val byPackage = ArrayList<AppEntry>(4)

        for (item in indexed) {
            when {
                item.label.startsWith(query) -> prefix += item.entry
                item.label.contains(query) -> contains += item.entry
                item.packageName.contains(query) -> byPackage += item.entry
            }
        }
        return prefix + contains + byPackage
    }
}
