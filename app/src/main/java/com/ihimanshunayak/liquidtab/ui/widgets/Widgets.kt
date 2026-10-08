/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — widget renderers.
 *
 * Name      : Widgets.kt
 * Version   : 1.0.0
 * Purpose   : The eight Home widgets, each reading a real system source and
 *             each owning its own update cadence.
 *
 *             The update model is the point of this file. A launcher that
 *             recomposes on a timer burns battery for a clock the system
 *             already ticks, so nothing here polls: the clock drives itself
 *             from a delay to the next minute boundary, the battery listens for
 *             the sticky ACTION_BATTERY_CHANGED broadcast, the calendar reads
 *             through its ContentObserver, and the now-playing widget follows
 *             the MediaSession that FreeMusic owns. The Home screen is never
 *             asked to redraw for any of them.
 *
 * Notes     : Every source that can be unavailable degrades to a stated
 *             fallback rather than an error or a spinner: weather without a
 *             location offers a lock-screen-style hint, the now-playing widget
 *             says nothing is playing. A widget that looks broken is worse than
 *             one that is honestly empty.
 */

package com.ihimanshunayak.liquidtab.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.CalendarRepository
import com.ihimanshunayak.liquidtab.data.CalendarSource
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.WeatherSource
import com.ihimanshunayak.liquidtab.data.WidgetKind
import com.ihimanshunayak.liquidtab.data.WidgetSource
import com.ihimanshunayak.liquidtab.data.WidgetSupport
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.media.FreeMusicBridge
import com.ihimanshunayak.liquidtab.ui.controls.rememberQuickControlsState
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.util.launchApp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The plate every widget shares, so a grid of different widgets still reads as
 * one product. [height] is per kind: a clock wants room to breathe, a battery
 * row does not.
 */
@Composable
fun WidgetPlate(
    height: Int = 104,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Dispatches a [WidgetKind] to its renderer. */
@Composable
fun WidgetContent(kind: WidgetKind) {
    when (kind) {
        WidgetKind.CLOCK -> ClockWidget()
        WidgetKind.DATE -> DateWidget()
        WidgetKind.WEATHER -> WeatherWidget()
        WidgetKind.CALENDAR -> CalendarWidget()
        WidgetKind.BATTERY -> BatteryWidget()
        WidgetKind.NOW_PLAYING -> NowPlayingWidget()
        WidgetKind.FAVORITES -> FavoritesWidget()
        WidgetKind.QUICK_ACTIONS -> QuickActionsWidget()
    }
}

// ── Clock ─────────────────────────────────────────────────────────────────────

/**
 * The time. Ticks itself to the next minute boundary rather than every second:
 * the design's clock shows hours and minutes, and a delay that lines up with the
 * boundary means it changes at the moment the minute does, drift-free.
 */
@Composable
fun ClockWidget() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            val millisToNextMinute = 60_000L - (now.second * 1000L + now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceAtLeast(1_000L))
        }
    }
    WidgetPlate(height = 116) {
        Text(
            text = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) },
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
            color = glassContentColor(),
        )
    }
}

// ── Date ──────────────────────────────────────────────────────────────────────

@Composable
fun DateWidget() {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            // Re-read just after midnight, computed from the clock rather than
            // assumed to be exactly 24 hours away.
            val now = LocalTime.now()
            val millisLeft = ((24 * 60 * 60) - (now.hour * 3600 + now.minute * 60 + now.second)) * 1000L
            delay(millisLeft.coerceAtLeast(60_000L))
        }
    }
    WidgetPlate(height = 116) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remember(today) { today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                style = MaterialTheme.typography.titleMedium,
                color = glassContentColor().copy(alpha = 0.75f),
            )
            Text(
                text = remember(today) { today.dayOfMonth.toString() },
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp),
                color = glassContentColor(),
            )
            Text(
                text = remember(today) { today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                style = MaterialTheme.typography.labelMedium,
                color = glassContentColor().copy(alpha = 0.7f),
            )
        }
    }
}

// ── Battery ───────────────────────────────────────────────────────────────────

/**
 * Charge and charging state, from the sticky battery broadcast.
 *
 * Registers a receiver rather than polling `BatteryManager` on a timer: the
 * system already broadcasts exactly when the number changes, and a sticky
 * intent delivers the current value immediately so the first frame is correct
 * without waiting for a change.
 */
@Composable
fun BatteryWidget() {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val raw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (raw >= 0 && scale > 0) {
                    level = (raw * 100) / scale
                }
                charging = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING,
                    BatteryManager.BATTERY_STATUS_FULL,
                    -> true

                    else -> false
                }
            }
        }
        // The sticky broadcast delivers its current value as soon as this
        // returns, so no initial poll is needed.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    WidgetPlate(height = 92) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (level in 0..100) "$level%" else "—",
                style = MaterialTheme.typography.headlineMedium,
                color = glassContentColor(),
            )
            Text(
                text = when {
                    level !in 0..100 -> "Battery unavailable"
                    charging -> "Charging"
                    else -> "On battery"
                },
                style = MaterialTheme.typography.labelMedium,
                color = glassContentColor().copy(alpha = 0.72f),
            )
        }
    }
}



// ── Weather ───────────────────────────────────────────────────────────────────

/**
 * Current conditions for the configured city.
 *
 * The widget does not fetch. It reads [WeatherSource], which owns the refresh
 * cadence and the caching, so a Home screen with two weather widgets makes one
 * request and a swipe between pages makes none. The city is a setting rather
 * than a location request: a launcher asking for background location on first
 * run is a launcher that gets denied, and a typed city works offline.
 */
@Composable
fun WeatherWidget() {
    val context = LocalContext.current
    val latitude by LauncherSettings.weatherLatitude.state.collectAsStateWithLifecycle()
    val longitude by LauncherSettings.weatherLongitude.state.collectAsStateWithLifecycle()
    val city by LauncherSettings.weatherCity.state.collectAsStateWithLifecycle()

    // One refresher per coordinate pair; re-entering composition for the same
    // city is a no-op rather than a second request loop.
    LaunchedEffect(latitude, longitude) {
        WeatherSource.start(latitude, longitude)
    }

    val state by WeatherSource.state.collectAsStateWithLifecycle()

    WidgetPlate(height = 124) {
        when (val current = state) {
            is WidgetSource.Ready -> {
                val weather = current.value
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${weather.temperatureC.roundToInt()}°",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                            color = glassContentColor(),
                        )
                        Text(
                            text = if (city.isBlank()) "Current location" else city,
                            style = MaterialTheme.typography.labelMedium,
                            color = glassContentColor().copy(alpha = 0.72f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = weather.description,
                            style = MaterialTheme.typography.titleSmall,
                            color = glassContentColor(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "H ${weather.highC.roundToInt()}°   L ${weather.lowC.roundToInt()}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = glassContentColor().copy(alpha = 0.7f),
                        )
                        if (current.stale) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Last known reading",
                                style = MaterialTheme.typography.labelSmall,
                                color = glassContentColor().copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }

            is WidgetSource.Loading -> WidgetMessage("Weather", "Fetching forecast…")

            is WidgetSource.Unavailable -> WidgetMessage(
                title = if (city.isBlank()) "Weather" else city,
                body = current.reason,
                action = if (city.isNotBlank() || latitude == 0f) "Open Settings" to {
                    context.startActivity(
                        android.content.Intent(
                            context,
                            com.ihimanshunayak.liquidtab.settings.SettingsActivity::class.java,
                        ),
                    )
                } else {
                    null
                },
            )

            is WidgetSource.Idle -> WidgetMessage("Weather", "Checking conditions…")
        }
    }
}

// ── Calendar ──────────────────────────────────────────────────────────────────

/**
 * Today's events. Reads [CalendarSource], which observes the calendar provider
 * rather than polling it — an event added anywhere on the device appears here
 * without a timer.
 *
 * The permission is offered in place: a launcher that demands calendar access at
 * first run is a launcher that gets denied, and the widget's honest alternative
 * is to say what it needs.
 */
@Composable
fun CalendarWidget() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { CalendarSource.start(context) }
    val state by CalendarSource.state.collectAsStateWithLifecycle()

    WidgetPlate(height = 120) {
        when (val current = state) {
            is WidgetSource.Ready -> {
                val events = current.value
                if (events.isEmpty()) {
                    WidgetMessage("Calendar", "Nothing scheduled today")
                } else {
                    val next = events.first()
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = glassContentColor().copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (next.allDay) "All day" else next.startTime(java.time.ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.titleSmall,
                                color = glassContentColor(),
                            )
                            Text(
                                text = next.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassContentColor(),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        if (events.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (events.size == 2) "1 more today" else "${events.size - 1} more today",
                                style = MaterialTheme.typography.labelSmall,
                                color = glassContentColor().copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            is WidgetSource.Unavailable -> {
                // A permission launcher rather than a bare requestPermissions
                // call: the result is what re-reads the calendar, and without it
                // a granted permission would leave the widget showing its empty
                // state until the process restarted.
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        CalendarSource.start(context)
                        WidgetSupport.refresh(context)
                    }
                }
                WidgetMessage(
                    title = "Calendar",
                    body = current.reason,
                    action = "Grant access" to {
                        permission.launch(CalendarRepository.PERMISSION)
                    },
                )
            }

            else -> WidgetMessage("Calendar", "Reading your schedule…")
        }
    }
}

// ── Now playing ───────────────────────────────────────────────────────────────

/**
 * What FreeMusic is playing, with transport controls.
 *
 * The session is acquired while this widget is on screen and released when it
 * leaves, so a Home screen without it never opens a service connection. The
 * progress line is drawn from a position read at draw time rather than a stored
 * one, which is what keeps a paused track from ticking.
 */
@Composable
fun NowPlayingWidget() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        FreeMusicBridge.acquire(context)
        onDispose { FreeMusicBridge.release() }
    }

    val state by FreeMusicBridge.state.collectAsStateWithLifecycle()

    WidgetPlate(height = 132) {
        when (val current = state) {
            is WidgetSource.Ready -> {
                val track = current.value
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkPlate(track.artworkUri)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = glassContentColor(),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            track.artist?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glassContentColor().copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TransportControls(
                            isPlaying = track.isPlaying,
                            onPrevious = { FreeMusicBridge.previous() },
                            onPlayPause = { FreeMusicBridge.playPause() },
                            onNext = { FreeMusicBridge.next() },
                        )
                    }
                    if (track.durationMs > 0L) {
                        Spacer(Modifier.height(10.dp))
                        ProgressLine(fraction = track.progress)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(track.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = glassContentColor().copy(alpha = 0.65f),
                            )
                            Text(
                                text = formatTime(track.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = glassContentColor().copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            is WidgetSource.Unavailable -> WidgetMessage(
                title = "Now playing",
                body = current.reason,
                action = "Open FreeMusic" to {
                    val packageName = FreeMusicBridge.PACKAGES.firstOrNull { pkg ->
                        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }
                            .getOrNull() != null
                    }
                    if (packageName != null) {
                        runCatching {
                            context.startActivity(context.packageManager.getLaunchIntentForPackage(packageName))
                        }
                    }
                },
            )

            else -> WidgetMessage("Now playing", "Looking for a session…")
        }
    }
}

// ── Favourites ────────────────────────────────────────────────────────────────

/**
 * The user's most-used apps, which is what "favourites" means to a launcher —
 * FreeMusic's favourite tracks belong to FreeMusic, and duplicating that list
 * here would be a second source of truth for it.
 *
 * The set is derived, not configured: the dock plus the first page's apps, in
 * their own order. That means it is always correct without a setting to keep in
 * sync.
 */
@Composable
fun FavoritesWidget() {
    val context = LocalContext.current
    val app = context.applicationContext as com.ihimanshunayak.liquidtab.LiquidTabApp
    val workspace by LauncherStore.workspace.collectAsStateWithLifecycle()
    val apps by app.appRepository.apps.collectAsStateWithLifecycle()

    val favorites = remember(workspace, apps) {
        val byKey = apps.associateBy { it.key }
        val docked = workspace.dock.mapNotNull { item ->
            (item as? WorkspaceItem.App)?.ref?.let { byKey[it.key] }
        }
        val onFirstPage = workspace.pages.firstOrNull()
            ?.items
            ?.mapNotNull { item -> (item as? WorkspaceItem.App)?.ref?.let { byKey[it.key] } }
            .orEmpty()
        (docked + onFirstPage).distinctBy { it.key }.take(6)
    }

    WidgetPlate(height = 116) {
        if (favorites.isEmpty()) {
            WidgetMessage("Favourites", "Pin a few apps to the dock to see them here")
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Favourites",
                    style = MaterialTheme.typography.labelMedium,
                    color = glassContentColor().copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    favorites.forEach { entry ->
                        FavoriteTile(entry = entry, onClick = { launchApp(context, entry.ref) })
                    }
                }
            }
        }
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────

/**
 * The Control Center's primary toggles as a Home strip.
 *
 * Each one calls the same [QuickControlsState] the Control Center uses, so the
 * two surfaces can never disagree about what Wi-Fi or the torch is doing. The
 * tiles open the system panel where Android no longer allows a silent toggle —
 * see [QuickControlsState] for which those are.
 */
@Composable
fun QuickActionsWidget() {
    val controls = rememberQuickControlsState()

    WidgetPlate(height = 96) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickActionChip(
                label = "Wi-Fi",
                active = controls.wifiEnabled,
                onClick = { controls.openWifiSettings() },
            )
            QuickActionChip(
                label = "Bluetooth",
                active = controls.bluetoothEnabled,
                onClick = { controls.openBluetoothSettings() },
            )
            QuickActionChip(
                label = "Torch",
                active = controls.torchEnabled,
                onClick = { controls.toggleTorch() },
            )
            QuickActionChip(
                label = "Focus",
                active = controls.dndEnabled,
                onClick = { controls.toggleDnd() },
            )
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

/**
 * The resting form of a widget: a title, a sentence, and an optional action.
 *
 * Used for every state that has nothing to show, which is what keeps the widgets
 * honest — a widget says what it is waiting for or what the user could change,
 * rather than drawing a zero.
 */
@Composable
private fun WidgetMessage(
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = glassContentColor(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.labelMedium,
            color = glassContentColor().copy(alpha = 0.7f),
        )
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Album artwork, or a music-note plate when the track carries none. */
@Composable
private fun ArtworkPlate(artworkUri: String?) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = glassContentColor().copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = glassContentColor().copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = glassContentColor().copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Previous, play/pause and next, sized for a finger on a glass plate. */
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
        TransportButton(
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
        )
        TransportButton(Icons.Rounded.SkipNext, "Next", onNext)
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 36 dp glyph, 44 dp touch area: small enough to sit three-across inside the
    // widget, large enough to hit without aiming.
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(glassContentColor().copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = glassContentColor(),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A thin progress track with a filled head, matching the plates around it. */
@Composable
private fun ProgressLine(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(glassContentColor().copy(alpha = 0.22f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(glassContentColor().copy(alpha = 0.85f)),
        )
    }
}

/** A favourite app, as an icon with its label underneath. */
@Composable
private fun FavoriteTile(entry: AppEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as com.ihimanshunayak.liquidtab.LiquidTabApp
    var icon by remember(entry.key) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(entry.key) {
        icon = app.appRepository.icon(entry, 96)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = entry.label, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(percent = 23))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val artwork = icon
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                )
            } else {
                Text(
                    text = entry.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = glassContentColor().copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = glassContentColor().copy(alpha = 0.8f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** One quick action, lit when the thing it controls is on. */
@Composable
private fun QuickActionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                } else {
                    glassContentColor().copy(alpha = 0.10f)
                },
            )
            .clickable(onClickLabel = label, onClick = onClick)
            // 12 dp vertical padding around a labelSmall line lands just under
            // the 48 dp guidance, so the chip claims the minimum height itself
            // rather than relying on the row's own content size.
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        glassContentColor().copy(alpha = 0.4f)
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600),
            color = glassContentColor().copy(alpha = if (active) 0.95f else 0.8f),
        )
    }
}

/** mm:ss for a duration in milliseconds. */
private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

