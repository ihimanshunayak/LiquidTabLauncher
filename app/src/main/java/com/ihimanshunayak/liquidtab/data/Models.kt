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

import android.os.UserHandle
import androidx.annotation.StringRes
import com.ihimanshunayak.liquidtab.R
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
    ;

    /**
     * The widget's name as a screen spells it, and the one line saying what it
     * shows. Held here rather than in the picker so a widget's name cannot
     * disagree between the catalogue, a page item's label and the About notice.
     */
    @get:StringRes
    val nameRes: Int
        get() = when (this) {
            CLOCK -> R.string.widget_clock
            DATE -> R.string.widget_date
            WEATHER -> R.string.widget_weather
            CALENDAR -> R.string.widget_calendar
            BATTERY -> R.string.widget_battery
            NOW_PLAYING -> R.string.widget_now_playing
            FAVORITES -> R.string.widget_favorites
            QUICK_ACTIONS -> R.string.widget_quick_actions
        }

    @get:StringRes
    val summaryRes: Int
        get() = when (this) {
            CLOCK -> R.string.widget_clock_summary
            DATE -> R.string.widget_date_summary
            WEATHER -> R.string.widget_weather_summary
            CALENDAR -> R.string.widget_calendar_summary
            BATTERY -> R.string.widget_battery_summary
            NOW_PLAYING -> R.string.widget_now_playing_summary
            FAVORITES -> R.string.widget_favorites_summary
            QUICK_ACTIONS -> R.string.widget_quick_actions_summary
        }
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
 *
 * [userHandle] is carried because the same package can be installed in more
 * than one profile, and each profile's copy has its own label and icon. Icon
 * lookups that assume the personal profile silently fail for a work app, so
 * the handle travels with the entry rather than being assumed at draw time.
 *
 * It is nullable, and null means the personal profile, rather than defaulting
 * to `Process.myUserHandle()`: that call is framework code, and evaluating it
 * as a default argument makes the data class unusable off-device.
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val userHandle: android.os.UserHandle? = null,
) {
    val ref: AppRef get() = AppRef(packageName, activityName)

    val key: String get() = ref.key

    /** The profile to resolve this entry against, resolved lazily on-device. */
    val profile: android.os.UserHandle get() = userHandle ?: android.os.Process.myUserHandle()

    /**
     * Identity for the icon cache: same package in two profiles are two
     * different icons, so the profile is part of the key.
     */
    val iconKey: String get() = "${userHandle?.hashCode() ?: 0}:$packageName"
}
