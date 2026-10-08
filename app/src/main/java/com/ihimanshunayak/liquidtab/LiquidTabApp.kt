/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — application entry point.
 *
 * Name      : LiquidTabApp.kt
 * Version   : 1.0.0
 * Purpose   : Owns the two process-wide stores — settings and the workspace —
 *             and the app repository, and hands them to the UI. Creating them
 *             here rather than in an Activity is deliberate: the launcher is a
 *             Home app, so its Activity is recreated far more often than the
 *             process, and nothing user-visible should depend on which
 *             Activity instance happened to be first.
 */

package com.ihimanshunayak.liquidtab

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.ihimanshunayak.liquidtab.data.AppRepository
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.WidgetSupport

class LiquidTabApp : Application() {

    /**
     * Installed apps and their icons. One instance for the process: the icon
     * cache is the single most valuable thing to keep warm across Activity
     * recreations.
     */
    lateinit var appRepository: AppRepository
        private set

    private var trimRegistered = false

    override fun onCreate() {
        super.onCreate()
        LauncherSettings.init(this)
        LauncherStore.init(this)
        appRepository = AppRepository(this)
        // Starts the watches the Home widgets read from — package changes and
        // the calendar provider. The weather and music sources start on their
        // own when a widget that needs them appears.
        WidgetSupport.install(this)
        registerTrimCallback()
    }

    /**
     * A Home app is never stopped by the user, so Android reclaims it from the
     * background instead. Under that pressure the process can be killed without
     * another frame, so the pending workspace write is forced out here rather
     * than waiting for the debounce — a layout the user just arranged must not
     * be the thing that a low-memory kill loses.
     */
    private fun registerTrimCallback() {
        if (trimRegistered) return
        trimRegistered = true
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= TRIM_MEMORY_BACKGROUND) {
                    LauncherStore.flush()
                    appRepository.trim()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            // Deprecated in favour of onTrimMemory, but a launcher still has to
            // answer it: on API 26-33 no TRIM_MEMORY_* level is guaranteed at
            // all, so this is the only low-memory signal those devices send.
            // OVERRIDE_DEPRECATION is the warning about the override itself, which
            // is the point of this method; DEPRECATION covers calling into it.
            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onLowMemory() {
                LauncherStore.flush()
                appRepository.trim()
            }
        })
    }
}
