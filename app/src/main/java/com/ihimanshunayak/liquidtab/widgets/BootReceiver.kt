/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — boot and time-change receiver.
 *
 * Name      : BootReceiver.kt
 * Version   : 1.0.0
 * Purpose   : Refreshes the cached app list and the clock-dependent widgets
 *             after a reboot, a time change or a locale change. Android kills
 *             a Home app's process on reboot, so anything derived rather than
 *             stored has to be rebuilt on the way back up — and a launcher
 *             whose clock is showing yesterday's time looks broken.
 *
 * Notes     : Declared in the manifest for RECEIVE_BOOT_COMPLETED,
 *             TIME_SET, TIMEZONE_CHANGED and DATE_CHANGED. It never starts an
 *             Activity: the system launches the Home screen itself, and
 *             forcing it here would fight that.
 */

package com.ihimanshunayak.liquidtab.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ihimanshunayak.liquidtab.LiquidTabApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> refreshAppList(context)

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> {
                // Nothing to write: the clock widgets read the system clock on
                // every tick they schedule, so there is no cached time to
                // correct. Logged so the receiver is not silently a no-op.
                Log.d(TAG, "Time context changed: ${intent.action}")
            }
        }
    }

    /**
     * Rebuilds the app list off the main thread. `goAsync()` keeps the process
     * alive long enough for the scan to finish, which a plain receiver must
     * announce explicitly.
     */
    private fun refreshAppList(context: Context) {
        val app = context.applicationContext as? LiquidTabApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.appRepository.refresh()
            } catch (t: Throwable) {
                Log.e(TAG, "Post-boot app refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
