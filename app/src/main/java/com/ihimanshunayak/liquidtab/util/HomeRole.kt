/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — default Home role helpers.
 *
 * Name      : HomeRole.kt
 * Version   : 1.0.0
 * Purpose   : Answers the two questions the About screen and the Settings row
 *             ask about this app's Home role — "am I the default Home app right
 *             now?" and "open the screen where the user can make me one" — and
 *             keeps the API-level split in one place instead of in the UI.
 *
 * Notes     : Android moved Home selection from a broadcast-driven chooser to
 *             the RoleManager in API 29. A launcher must speak both: tablets
 *             running API 26-28 are exactly the devices this app targets, and
 *             the older path is not a fallback for a broken newer one — it is
 *             the correct answer there.
 *
 *             Nothing here decides anything about the launcher's own state.
 *             Callers ask; this file only reports.
 */

package com.ihimanshunayak.liquidtab.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log

private const val TAG = "HomeRole"

/** `Settings.ACTION_HOME_SETTINGS`, which the SDK marks `@hide`. */
private const val LEGACY_HOME_SETTINGS_ACTION = "android.settings.HOME_SETTINGS"

/**
 * Whether this package currently resolves the system's Home intent.
 *
 * The check is the one Android itself uses to decide which app draws Home, so
 * it cannot disagree with the behaviour the user is seeing: a launcher that
 * claimed otherwise would be telling the user something the next Home press
 * contradicts.
 */
fun isDefaultHome(context: Context): Boolean = try {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.resolveActivity(
            home,
            android.content.pm.PackageManager.ResolveInfoFlags.of(
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong(),
            ),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(
            home,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
    }
    resolved?.activityInfo?.packageName == context.packageName
} catch (t: Throwable) {
    Log.w(TAG, "Could not resolve the system Home activity", t)
    false
}

/**
 * Opens the system's own Home-app chooser. On API 29+ this is the RoleManager's
 * Home-role screen, which is where the user can also revoke the role; below
 * that it is the legacy "Home" settings page. Returns false when the device
 * offers neither, so the caller can say so instead of failing silently.
 */
fun openDefaultHomeSettings(context: Context): Boolean = try {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            settingsHomeIntent()
        }
    } else {
        settingsHomeIntent()
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (t: Throwable) {
    Log.e(TAG, "Could not open the Home-role settings", t)
    false
}

/**
 * The legacy Home settings page, spelled as a literal because
 * `Settings.ACTION_HOME_SETTINGS` is `@hide` in the SDK and therefore not
 * referenceable from an app. The string is the platform's own public contract
 * for pre-29 devices — every launcher that predates the RoleManager uses it,
 * and it is still resolved on current releases.
 */
private fun settingsHomeIntent(): Intent = Intent(LEGACY_HOME_SETTINGS_ACTION)

/** The user's handle for this app, used by the About screen's diagnostic line. */
fun userHandleLabel(): String = Process.myUserHandle().toString()
