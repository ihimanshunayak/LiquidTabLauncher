/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — launch helpers.
 *
 * Name      : AppLauncher.kt
 * Version   : 1.0.0
 * Purpose   : Starts apps from a stored [AppRef], with the two things a Home
 *             screen has to survive that a normal caller does not: the app may
 *             have been uninstalled or disabled since the layout was saved, and
 *             the launcher must not crash or hang onto a dead shortcut when
 *             that happens. Returns whether a launch actually started so the
 *             caller can clean the shortcut up.
 */

package com.ihimanshunayak.liquidtab.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.ihimanshunayak.liquidtab.data.AppRef

private const val TAG = "AppLauncher"

/**
 * Launches [ref]. Returns false when the app is gone or refuses to start, in
 * which case the caller should offer to remove the shortcut rather than retry.
 *
 * [sourceBounds] is the icon's screen rectangle, so the system's launch
 * animation grows out of the icon the user actually touched instead of the
 * screen centre — the difference between a launcher that feels native and one
 * that feels like it is guessing.
 */
fun launchApp(
    context: Context,
    ref: AppRef,
    sourceBounds: android.graphics.Rect? = null,
): Boolean {
    val first = ref.className ?: resolveActivity(context, ref)
    if (first != null && startActivity(context, ref.packageName, first, sourceBounds)) return true

    // The recorded class did not work — most often because an app update
    // renamed its launcher activity. Ask the system what the class is now
    // before giving up on the shortcut.
    val resolved = resolveActivity(context, ref)
    if (resolved != null && resolved != first &&
        startActivity(context, ref.packageName, resolved, sourceBounds)
    ) {
        return true
    }
    Log.w(TAG, "No launchable activity for $ref")
    return false
}

/** Starts [packageName]/[className]. Returns false when Android refuses. */
private fun startActivity(
    context: Context,
    packageName: String,
    className: String,
    sourceBounds: android.graphics.Rect?,
): Boolean = try {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(packageName, className))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    sourceBounds?.let { intent.sourceBounds = it }
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Log.w(TAG, "No activity for $packageName/$className", e)
    false
} catch (e: SecurityException) {
    // A work-profile app can become inaccessible between the scan and the tap.
    Log.w(TAG, "Launch denied for $packageName/$className", e)
    false
} catch (t: Throwable) {
    Log.e(TAG, "Launch failed for $packageName/$className", t)
    false
}

/**
 * The package's current launcher activity.
 *
 * Read fresh at launch rather than trusted from the saved shortcut: the class
 * recorded when the user dropped the icon may since have been renamed by an
 * app update.
 */
private fun resolveActivity(context: Context, ref: AppRef): String? = try {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    launcherApps?.getActivityList(ref.packageName, Process.myUserHandle())
        ?.firstOrNull()
        ?.name
        ?: context.packageManager
            .getLaunchIntentForPackage(ref.packageName)
            ?.component
            ?.className
} catch (t: Throwable) {
    Log.w(TAG, "Could not resolve launcher activity for $ref", t)
    null
}

/** The label for a package, or the package name when the app is gone. */
fun appLabel(context: Context, packageName: String): String = try {
    val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(packageName, 0)
    }
    context.packageManager.getApplicationLabel(info).toString()
} catch (t: Throwable) {
    packageName
}

/** Opens the system's app-info page, the only place a user can uninstall one. */
fun openAppInfo(context: Context, packageName: String): Boolean = try {
    context.startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (t: Throwable) {
    Log.e(TAG, "Could not open app info for $packageName", t)
    false
}

/** A short, non-fatal message. Used where the alternative is silence. */
fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
