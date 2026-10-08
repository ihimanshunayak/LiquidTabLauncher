/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — the FreeMusic connection.
 *
 * Name      : FreeMusicBridge.kt
 * Version   : 2.0.0
 * Purpose   : Connects to the FreeMusic app's MediaSessionService and exposes
 *             what the launcher's Now Playing and Favourites widgets need:
 *             the current track, its playback state, and transport controls.
 *
 *             This is the launcher's entire relationship with FreeMusic — it
 *             does not play anything, own a queue, or read the app's database.
 *             A launcher that shipped its own copy of the player's state would
 *             be a second source of truth for "what is playing", and the two
 *             would disagree the first time either one changed.
 *
 * Notes     : A process-wide singleton, reference counted by [acquire] and
 *             [release]: the session is opened when the first widget that needs
 *             it appears and closed when the last one leaves, so a Home screen
 *             with no music widget never opens a service connection at all.
 *
 *             Every failure path is a state, not an exception. FreeMusic not
 *             installed, not enabled, or running with nothing loaded all resolve
 *             to a stated reason the widget can show.
 */

package com.ihimanshunayak.liquidtab.media

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ihimanshunayak.liquidtab.data.WidgetSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is playing, as the widget needs it. */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
) {
    val hasArtwork: Boolean get() = !artworkUri.isNullOrBlank()

    /** Where the track is, as a 0..1 fraction; 0 when the duration is unknown. */
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * A live view of the FreeMusic session.
 *
 * [state] is a StateFlow so a widget reads it the same way it reads any other
 * source and never polls. Everything below is guarded: a controller that
 * disconnects drops the state to a stated reason instead of holding a stale
 * track on screen.
 */
object FreeMusicBridge {

    private val _state = MutableStateFlow<WidgetSource<NowPlaying>>(WidgetSource.Idle)
    val state: StateFlow<WidgetSource<NowPlaying>> = _state.asStateFlow()

    /** False once a lookup proves no FreeMusic build is installed. */
    private val _installed = MutableStateFlow(true)
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private var consumers = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    /**
     * Registers a consumer, opening the session if this is the first one. Safe
     * to call repeatedly and from a composable's `LaunchedEffect`, which is how
     * the widget starts it.
     */
    @Synchronized
    fun acquire(context: Context) {
        consumers++
        if (consumers > 1 || controller != null || connecting) return
        val appContext = context.applicationContext
        val token = sessionToken(appContext)
        if (token == null) {
            _installed.value = false
            _state.value = WidgetSource.Unavailable("FreeMusic is not installed")
            return
        }
        connecting = true
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val built = runCatching { future.get() }.getOrNull()
                if (built == null) {
                    // No session running: the ordinary state for a user who has
                    // not opened FreeMusic yet, not a failure to report.
                    _state.value = WidgetSource.Unavailable("Open FreeMusic to start playing")
                    return@addListener
                }
                // The widget can be gone by now: the session build is a service
                // bind, and the user may have removed the widget or left Home in
                // the meantime. Assigning a controller that nothing will ever
                // release would keep the service connection for the life of the
                // process, so a late arrival is released instead.
                if (consumers <= 0) {
                    runCatching { built.release() }
                    return@addListener
                }
                controller = built
                built.addListener(listener)
                publish()
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /** Releases a consumer, closing the session when the last one leaves. */
    @Synchronized
    fun release() {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers > 0) return
        val current = controller ?: return
        controller = null
        runCatching { current.removeListener(listener) }
        runCatching { current.release() }
        _state.value = WidgetSource.Idle
    }

    /** The current position, asked for at draw time rather than held and aged. */
    fun currentPositionMs(): Long = runCatching { controller?.currentPosition ?: 0L }.getOrDefault(0L)

    /** Play or pause the current track. Does nothing when there is no session. */
    fun playPause() {
        val current = controller ?: return
        runCatching { if (current.isPlaying) current.pause() else current.play() }
    }

    fun next() {
        runCatching { controller?.seekToNextMediaItem() }
    }

    fun previous() {
        runCatching { controller?.seekToPreviousMediaItem() }
    }

    /** True while a session is open, so a caller can tell "connected" from "idle". */
    val isConnected: Boolean get() = controller != null

    private fun publish() {
        val current = controller
        if (current == null) {
            _state.value = WidgetSource.Unavailable("Open FreeMusic to start playing")
            return
        }
        val metadata = runCatching { current.mediaMetadata }.getOrNull()
        val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() }
        if (title == null) {
            // A session with no item loaded is "nothing playing", not an empty
            // widget with a title of "".
            _state.value = WidgetSource.Unavailable("Nothing playing")
            return
        }
        _state.value = WidgetSource.Ready(
            NowPlaying(
                title = title,
                artist = metadata.artist?.toString(),
                album = metadata.albumTitle?.toString(),
                artworkUri = metadata.artworkUri?.toString(),
                isPlaying = runCatching { current.isPlaying }.getOrDefault(false),
                durationMs = runCatching { current.duration }.getOrDefault(0L).coerceAtLeast(0L),
                positionMs = currentPositionMs(),
            ),
        )
    }

    /**
     * The packages FreeMusic may be installed as, in priority order: the release
     * build first, then the debug variant with its own id.
     */
    val PACKAGES = listOf(
        "com.ihimanshunayak.freemusic",
        "com.ihimanshunayak.freemusic.dev",
    )

    /** The service FreeMusic declares for its playback session. */
    const val SERVICE = "com.ihimanshunayak.freemusic.playback.PlaybackService"

    /**
     * The session token for whichever FreeMusic build is present, or null when
     * neither is installed — in which case there is nothing to connect to and
     * the widgets say so.
     */
    private fun sessionToken(context: Context): SessionToken? {
        for (pkg in PACKAGES) {
            val component = ComponentName(pkg, SERVICE)
            val installed = runCatching {
                context.packageManager.getServiceInfo(component, 0)
            }.isSuccess
            if (installed) return SessionToken(context, component)
        }
        return null
    }
}
