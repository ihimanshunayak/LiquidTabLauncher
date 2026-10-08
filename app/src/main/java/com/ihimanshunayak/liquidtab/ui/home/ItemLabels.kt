/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — item labels.
 *
 * Name      : ItemLabels.kt
 * Version   : 1.0.0
 * Purpose   : Turns a workspace item into the text a screen shows for it. Two
 *             places need this — the Home grid and the page manager's previews
 *             — and a preview whose labels disagree with the grid it previews is
 *             worse than no preview, so the rule lives in exactly one function.
 *
 * Notes     : The label resolution order matters and is deliberate: the installed
 *             app's own label wins, because an app can rename itself in an
 *             update and the user should see the name they will find in the
 *             library. The package name is the last resort and only appears for
 *             a shortcut whose app is gone.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.Workspace
import com.ihimanshunayak.liquidtab.data.WorkspaceItem

/**
 * The text for [item]: an app's name, a folder's name, or a widget's own name
 * spelled for a screen rather than for an enum.
 */
@Composable
fun itemLabel(item: WorkspaceItem, apps: Map<String, AppEntry>): String = when (item) {
    is WorkspaceItem.App -> apps[item.ref.key]?.label ?: item.ref.packageName
    is WorkspaceItem.Folder -> item.folder.name
    is WorkspaceItem.Widget -> stringResource(item.kind.nameRes)
}

/**
 * Every page item's label, keyed by [WorkspaceItem.key] so a caller holding only
 * a key — a drag session, a preview cell — can look one up.
 *
 * Dock items are included: a page preview does not show the dock, but a caller
 * that does (a folder sheet, a future overview) should not need a second map.
 *
 * Composable because a widget's label is a resource; the app and folder labels
 * are data and cost nothing extra to resolve here.
 */
@Composable
fun workspaceLabels(workspace: Workspace, apps: Map<String, AppEntry>): Map<String, String> {
    val labels = HashMap<String, String>()
    workspace.pages.forEach { page ->
        page.items.forEach { item -> labels[item.key] = itemLabel(item, apps) }
    }
    workspace.dock.forEach { item -> labels[item.key] = itemLabel(item, apps) }
    return labels
}
