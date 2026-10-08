/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — workspace model.
 *
 * Name      : Models.kt
 * Version   : 1.0.0
 * Purpose   : The persisted shape of the Home screen: pages of items where an
 *             item is an app shortcut, a folder, or a widget. Serialised as
 *             JSON by [LauncherStore]; nothing here touches Android APIs, which
 *             is what lets the workspace's placement rules be unit-tested on
 *             the JVM.
 */

package com.ihimanshunayak.liquidtab.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The widgets the launcher ships. Adding one means adding it to the picker. */
@Serializable
enum class WidgetKind {
    CLOCK,
    DATE,
    WEATHER,
    CALENDAR,
    BATTERY,
    NOW_PLAYING,
    FAVORITES,
    QUICK_ACTIONS,
}

/** A launchable activity: the app's package and its main activity class. */
@Serializable
data class AppRef(
    val packageName: String,
    val className: String? = null,
) {
    /** Stable identity across saves, drags and comparisons. */
    val key: String get() = if (className.isNullOrEmpty()) packageName else "$packageName/$className"
}

/** A folder of app shortcuts. Folders hold apps only — never widget strips. */
@Serializable
data class FolderRef(
    val id: String,
    val name: String,
    val items: List<AppRef> = emptyList(),
)

/**
 * Something on a Home page or in the dock.
 *
 * Widgets are full-width strips in the same ordered list as everything else, so
 * ordering, dragging and persistence work uniformly.
 */
@Serializable
sealed interface WorkspaceItem {
    /** Stable identity for drag, animation keys and de-duplication. */
    val key: String

    @Serializable
    @SerialName("app")
    data class App(val ref: AppRef) : WorkspaceItem {
        override val key: String get() = "app:${ref.key}"
    }

    @Serializable
    @SerialName("folder")
    data class Folder(val folder: FolderRef) : WorkspaceItem {
        override val key: String get() = "folder:${folder.id}"
    }

    @Serializable
    @SerialName("widget")
    data class Widget(val id: String, val kind: WidgetKind) : WorkspaceItem {
        override val key: String get() = "widget:$id"
    }
}

/** One Home page: an ordered row-major flow of items. */
@Serializable
data class WorkspacePage(
    val id: String,
    val items: List<WorkspaceItem> = emptyList(),
)

/**
 * The whole Home layout: pages plus the dock, which is a single ordered row.
 *
 * [version] exists so a future model change can migrate rather than reset; a
 * payload with a version this build does not understand is set aside by
 * [LauncherStore] instead of being guessed at.
 */
@Serializable
data class Workspace(
    val version: Int = CURRENT_VERSION,
    val pages: List<WorkspacePage> = emptyList(),
    val dock: List<WorkspaceItem> = emptyList(),
    /**
     * The page a Home press returns to, or null for the first page.
     *
     * Stored by id rather than index so the choice survives reordering pages,
     * and nullable so a layout written before this field existed — or one whose
     * choice has since been deleted — reads as "first page" rather than as an
     * error the launcher would have to recover from.
     */
    val defaultPageId: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun empty(): Workspace = Workspace(pages = listOf(WorkspacePage(id = "page-1")))
    }
}

/**
 * A runtime view of one installed app — never persisted, rebuilt from the
 * PackageManager. The icon is loaded separately and cached by [AppRepository]
 * because it is the expensive part.
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
) {
    val ref: AppRef get() = AppRef(packageName, activityName)

    val key: String get() = ref.key
}
