/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — installed-app discovery and icon cache.
 *
 * Name      : AppRepository.kt
 * Version   : 1.0.0
 * Purpose   : Lists the device's launchable apps and resolves their icons,
 *             once per boot and then only when a package actually changes.
 *             A launcher cannot afford a PackageManager scan on a
 *             recomposition, so the scan is a suspend function guarded by a
 *             mutex, its result is a StateFlow, and icons are decoded lazily
 *             and held in a bounded cache keyed by package.
 *
 * Notes     : Uses LauncherApps when the API allows it — it is the launcher's
 *             own API, and it sees work-profile apps that getLaunchIntentForPackage
 *             does not — and falls back to the PackageManager otherwise.
 *             Both paths read only the `launcher` visibility declared in the
 *             manifest; the launcher never asks for QUERY_ALL_PACKAGES.
 */

package com.ihimanshunayak.liquidtab.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private val scanMutex = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /**
     * Icons by profile+package, so the same app in a work profile and the
     * personal profile keeps its own icon. The map is bounded at
     * [ICON_CACHE_LIMIT]; beyond that the least recently used entry is dropped,
     * which in practice is an app the user has not looked at in weeks.
     */
    private val iconCache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) =
            size > ICON_CACHE_LIMIT
    }

    /** Icons that failed or resolved to null, so the drawable work is not repeated. */
    private val iconMisses = ConcurrentHashMap.newKeySet<String>()

    private var lastIconDensity = 0

    /**
     * Re-reads the app list. Cheap enough to call on every package broadcast and
     * on resume, but guarded so concurrent callers share one scan.
     */
    suspend fun refresh() {
        scanMutex.withLock {
            val entries = withContext(Dispatchers.IO) {
                queryLaunchableApps()
            }
            _apps.value = entries
        }
    }

    /**
     * The installed apps whose main activity is launchable, sorted
     * case-insensitively by label. Work-profile apps are included when the API
     * exposes them: a launcher that hides a user's work apps looks broken.
     */
    private fun queryLaunchableApps(): List<AppEntry> {
        val entries = mutableListOf<AppEntry>()
        val seen = HashSet<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && launcherApps != null) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            val profiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && userManager != null) {
                userManager.userProfiles
            } else {
                emptyList()
            }
            for (profile in profiles.ifEmpty { listOf(android.os.Process.myUserHandle()) }) {
                val activities = try {
                    launcherApps.getActivityList(null, profile)
                } catch (t: Throwable) {
                    Log.w(TAG, "LauncherApps query failed for $profile", t)
                    emptyList()
                }
                for (activity in activities) {
                    // The profile is part of the identity, not just the package:
                    // the same app in two profiles is two entries, each with its
                    // own label and icon.
                    val key = "${profile.hashCode()}:${activity.applicationInfo.packageName}/${activity.name}"
                    if (!seen.add(key)) continue
                    entries += AppEntry(
                        packageName = activity.applicationInfo.packageName,
                        activityName = activity.name,
                        label = activity.label?.toString()
                            ?: activity.applicationInfo.loadLabel(packageManager).toString(),
                        userHandle = profile,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            // Fallback for devices or profiles where LauncherApps returns
            // nothing — the same visibility rules the manifest declares make
            // this safe and equally scoped.
            @Suppress("DEPRECATION")
            val intents = packageManager.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                0,
            )
            for (info in intents) {
                val packageName = info.activityInfo.packageName
                val className = info.activityInfo.name
                val key = "$packageName/$className"
                if (!seen.add(key)) continue
                entries += AppEntry(
                    packageName = packageName,
                    activityName = className,
                    label = info.loadLabel(packageManager).toString(),
                )
            }
        }

        return entries.sortedBy { it.label.lowercase(java.util.Locale.getDefault()) }
    }

    /**
     * The icon for [entry], drawn to a bitmap at [sizePx] and cached.
     *
     * Runs on the IO dispatcher and is safe to call from a composable's
     * `LaunchedEffect`; repeated calls are served from the cache. Returns null
     * only for an app whose icon genuinely cannot be loaded — callers draw a
     * neutral placeholder in that case and never crash.
     */
    suspend fun icon(entry: AppEntry, sizePx: Int): ImageBitmap? {
        val density = context.resources.displayMetrics.densityDpi
        if (density != lastIconDensity) {
            synchronized(iconCache) {
                iconCache.clear()
                iconMisses.clear()
            }
            lastIconDensity = density
        }
        val cacheKey = entry.iconKey
        synchronized(iconCache) { iconCache[cacheKey] }?.let { return it }
        if (cacheKey in iconMisses) return null

        val bitmap = withContext(Dispatchers.IO) {
            try {
                val drawable: Drawable? = if (launcherApps != null) {
                    launcherApps.getApplicationInfo(entry.packageName, 0, entry.profile)
                        ?.loadIcon(packageManager)
                } else {
                    packageManager.getApplicationIcon(entry.packageName)
                }
                drawable?.toImageBitmap(sizePx)
            } catch (t: Throwable) {
                Log.w(TAG, "Icon load failed for ${entry.packageName}", t)
                null
            }
        }

        if (bitmap == null) {
            iconMisses += cacheKey
            return null
        }
        synchronized(iconCache) { iconCache[cacheKey] = bitmap }
        return bitmap
    }

    /** Drops every cached icon; used on a theme or density change. */
    fun invalidateIcons() {
        synchronized(iconCache) { iconCache.clear() }
        iconMisses.clear()
    }

    /**
     * Releases what the launcher can afford to lose under memory pressure.
     *
     * A Home app is the process Android trims first, and it is also the one
     * process whose whole point is to repaint instantly — so this deliberately
     * does *not* call [invalidateIcons]. Throwing every icon away under
     * pressure would make the very next frame the most expensive one, at the
     * exact moment the device is short of memory. Instead the cache is shrunk
     * to [ICON_CACHE_FLOOR], which drops the icons the user has not looked at
     * while keeping the visible Home screen warm. The miss set is left alone:
     * it holds no bitmaps, and forgetting it would only make the launcher
     * re-attempt icons that are already known to be unavailable.
     */
    fun trim() {
        synchronized(iconCache) {
            // Snapshot as plain pairs first: clearing the map must not be able
            // to invalidate the entries we are about to put back.
            val kept = iconCache.entries
                .toList()
                .takeLast(ICON_CACHE_FLOOR)
                .map { it.key to it.value }
            iconCache.clear()
            kept.forEach { (key, bitmap) -> iconCache[key] = bitmap }
        }
    }

    private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
        if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
            val scaled = if (bitmap.width == sizePx) bitmap else Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
            return scaled.asImageBitmap()
        }
        val safeSize = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, safeSize, safeSize)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    /** True when [ref] still resolves to an installed, launchable app. */
    fun isInstalled(ref: AppRef): Boolean =
        apps.value.any { it.packageName == ref.packageName && (ref.className.isNullOrEmpty() || it.activityName == ref.className) }

    /**
     * A stable identity for the current scan, so the workspace can drop
     * shortcuts to uninstalled apps without rescanning per item.
     *
     * Keyed by package rather than package+activity: an app that renames its
     * launcher activity is still installed, and pruning on the exact key would
     * remove a shortcut that still launches.
     */
    fun installedPackages(): Set<String> = apps.value.mapTo(HashSet()) { it.packageName }

    /** The current configuration, used to detect a size change cheaply. */
    fun configuration(): Configuration = context.resources.configuration

    private companion object {
        const val TAG = "AppRepository"
        const val ICON_CACHE_LIMIT = 192

        /** What [trim] shrinks the cache to; roughly two Home pages of icons. */
        const val ICON_CACHE_FLOOR = 64
    }
}
