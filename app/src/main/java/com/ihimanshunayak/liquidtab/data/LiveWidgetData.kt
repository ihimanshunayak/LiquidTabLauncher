/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — live sources for the Home widgets.
 *
 * Name      : LiveWidgetData.kt
 * Version   : 1.0.0
 * Purpose   : Turns the widgets that need the outside world into state the grid
 *             can read. Weather refreshes on its own cadence, the calendar
 *             re-reads on the provider's content-change notification and at
 *             midnight, and now-playing follows the media session FreeMusic
 *             owns — see [FreeMusicBridge], which this file only consumes.
 *
 *             The point of the shape here is that the Home screen never polls
 *             and never asks a widget to redraw. Each source announces its own
 *             value, and the widget reads a StateFlow the same way it reads a
 *             setting.
 *
 * Notes     : Every source degrades to a stated reason rather than an error: no
 *             location set, no calendar permission, no session running. A widget
 *             that says "Set a city in Settings" is doing its job; one that
 *             shows a spinner forever is not.
 *
 *             Nothing here is persisted. These are views of system state, and a
 *             launcher that cached a temperature across a reboot would show
 *             yesterday's weather as today's.
 */

package com.ihimanshunayak.liquidtab.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.ihimanshunayak.liquidtab.media.FreeMusicBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

/**
 * Where a widget's data stands, as the widget needs to render it.
 *
 * Four states, not two: "loading" and "unavailable" are different things to a
 * user, and collapsing them is what produces a widget that looks broken when it
 * is merely waiting.
 */
sealed interface WidgetSource<out T> {
    /** Nothing asked for yet — the widget shows its resting state. */
    data object Idle : WidgetSource<Nothing>

    /** A request is in flight and there is nothing older to show. */
    data object Loading : WidgetSource<Nothing>

    /** A value, with [stale] true when it could not be renewed this round. */
    data class Ready<T>(val value: T, val stale: Boolean = false) : WidgetSource<T>

    /**
     * Nothing can be shown, and [reason] says what the user would have to change
     * — a permission, a setting, or an absent app.
     */
    data class Unavailable(val reason: String) : WidgetSource<Nothing>
}

/**
 * Weather for the configured city, refreshed on its own cadence.
 *
 * [start] is idempotent: a widget that re-enters composition reuses the running
 * refresher rather than starting a second one, which is what keeps a page swipe
 * from stacking requests.
 */
object WeatherSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<WidgetSource<Weather>>(WidgetSource.Idle)
    val state: StateFlow<WidgetSource<Weather>> = _state.asStateFlow()

    private var job: Job? = null
    private var lastKey: Pair<Float, Float>? = null

    /** How long a forecast is treated as current. */
    private const val REFRESH_MS = 30 * 60 * 1000L

    /** Starts refreshing for [latitude], [longitude]; a no-op when already running for them. */
    fun start(latitude: Float, longitude: Float) {
        if (latitude == 0f && longitude == 0f) {
            _state.value = WidgetSource.Unavailable("Set a city in Settings")
            return
        }
        val key = latitude to longitude
        if (job?.isActive == true && lastKey == key) return
        lastKey = key
        job?.cancel()
        job = scope.launch {
            while (true) {
                val held = (_state.value as? WidgetSource.Ready)?.value
                if (held == null) _state.value = WidgetSource.Loading
                val weather = WeatherRepository.current(latitude, longitude)
                _state.value = when {
                    weather != null -> WidgetSource.Ready(weather)
                    // A refresh that fails keeps the last reading and marks it,
                    // rather than blanking a widget the user is looking at.
                    held != null -> WidgetSource.Ready(held, stale = true)
                    else -> WidgetSource.Unavailable("Forecast unavailable offline")
                }
                delay(REFRESH_MS)
            }
        }
    }
}

/** Today's events, kept current by the calendar provider's own notifications. */
object CalendarSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<WidgetSource<List<CalendarEvent>>>(WidgetSource.Idle)
    val state: StateFlow<WidgetSource<List<CalendarEvent>>> = _state.asStateFlow()

    private var observer: ContentObserver? = null

    /**
     * Begins watching today's events. [context] is only ever the application
     * context: a launcher is a long-lived process and holding an Activity here
     * would be a leak.
     *
     * The provider is observed rather than polled — adding an event anywhere on
     * the device should correct the widget without a timer.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        if (observer != null) return
        if (!CalendarRepository.hasPermission(appContext)) {
            _state.value = WidgetSource.Unavailable("Grant calendar access in Settings")
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val registered = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                refresh(appContext)
            }
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(
                CalendarContract.Instances.CONTENT_URI,
                true,
                registered,
            )
        }.onSuccess { observer = registered }
        refresh(appContext)
        scope.launch {
            while (true) {
                delay(millisToNextMidnight())
                refresh(appContext)
            }
        }
    }

    /** Re-reads today's events. Called on a provider change, at midnight, and after a grant. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        if (!CalendarRepository.hasPermission(appContext)) {
            _state.value = WidgetSource.Unavailable("Grant calendar access in Settings")
            return
        }
        scope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching { CalendarRepository.eventsToday(appContext) }.getOrDefault(emptyList())
            }
            _state.value = WidgetSource.Ready(events)
        }
    }

    /** Stops observing; called when the process is done with the widget. */
    fun stop(context: Context) {
        val registered = observer ?: return
        observer = null
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(registered) }
    }

    /**
     * Time until the next midnight, so a Home screen left on overnight shows the
     * new day's events without any redraw trigger.
     */
    private fun millisToNextMidnight(): Long {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(60_000L)
    }
}

/**
 * Watches package changes, so anything a widget draws from an installed app can
 * correct itself when that app is installed, updated or removed.
 */
internal object PackageWatch {

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _changes.value = System.currentTimeMillis()
            }
        }
        runCatching {
            appContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                },
            )
        }
    }
}

/** Keeps the widget sources in step with the launcher's own lifecycle. */
object WidgetSupport {

    private var started = false

    /**
     * Warms the package watch and the calendar watch. Called once from
     * [com.ihimanshunayak.liquidtab.LiquidTabApp]; the weather source starts on
     * its own when a widget that needs it appears, and the FreeMusic session is
     * acquired and released by that widget's own lifetime.
     */
    fun install(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        PackageWatch.register(appContext)
        if (CalendarRepository.hasPermission(appContext)) {
            CalendarSource.start(appContext)
        }
    }

    /** True when the calendar permission is held, so Settings can say so. */
    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, CalendarRepository.PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Re-reads everything whose answer could have changed while the app was
     * away — what happens on a permission result or on a return to Home.
     */
    fun refresh(context: Context) {
        if (CalendarRepository.hasPermission(context)) CalendarSource.refresh(context)
    }
}
