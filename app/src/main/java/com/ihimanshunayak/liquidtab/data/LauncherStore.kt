/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — workspace persistence.
 *
 * Name      : LauncherStore.kt
 * Version   : 1.0.0
 * Purpose   : Loads and saves the [Workspace] as JSON in SharedPreferences, and
 *             is the only writer of it. Writes are debounced off the main
 *             thread and serialised through a single actor-style channel, so a
 *             drag that fires twenty position updates still lands one file
 *             write — and the file can never be half-written by two writers.
 *             The launcher is a Home app; a settings write must never show up
 *             as a dropped frame.
 *
 * Recovery  : A payload that fails to decode (corruption, or a version this
 *             build does not understand) is copied aside under [KEY_BAD] and
 *             the launcher starts from a sane workspace rather than crashing or
 *             silently losing the user's layout forever.
 */

package com.ihimanshunayak.liquidtab.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object LauncherStore {

    private const val TAG = "LauncherStore"
    private const val STORE = "liquid_tab_workspace"
    private const val KEY_WORKSPACE = "workspace_json"
    private const val KEY_BAD = "workspace_json_bad"

    /** Trailing debounce for drag storms; one write per settle. */
    private const val WRITE_DEBOUNCE_MS = 250L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Workspace>(Channel.CONFLATED)

    private val _workspace = MutableStateFlow(Workspace.empty())
    val workspace: StateFlow<Workspace> = _workspace.asStateFlow()

    /**
     * Reads the workspace (or a fresh one) and starts the writer loop. Safe to
     * call more than once; later calls re-read.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        _workspace.value = read()

        scope.launch {
            for (workspace in writes) {
                delay(WRITE_DEBOUNCE_MS)
                // Drain to the latest value so a burst becomes one write.
                var latest = workspace
                while (true) {
                    val next = writes.tryReceive().getOrNull() ?: break
                    latest = next
                }
                write(latest)
            }
        }
    }

    /**
     * Applies [transform] to the current workspace, publishes the result to
     * Compose, and schedules the write. All mutations — drags, folder edits,
     * widget placement — come through here so there is exactly one path to
     * disk.
     */
    fun update(transform: (Workspace) -> Workspace) {
        val next = transform(_workspace.value)
        _workspace.value = next
        writes.trySend(next)
    }

    /** Replaces the workspace wholesale (used by "Reset Home layout"). */
    fun reset() {
        update { Workspace.empty() }
    }

    /** Forces the pending write out immediately; used before process death. */
    fun flush() {
        write(_workspace.value)
    }

    private fun read(): Workspace {
        val raw = prefs.getString(KEY_WORKSPACE, null)
        if (raw.isNullOrBlank()) return Workspace.empty()
        return try {
            val workspace = json.decodeFromString<Workspace>(raw)
            if (workspace.version > Workspace.CURRENT_VERSION) {
                // Written by a newer build. Do not guess at its meaning: keep it
                // aside so nothing is lost, and run this build's model.
                Log.w(TAG, "Workspace version ${workspace.version} is newer than this build; starting fresh")
                prefs.edit().putString(KEY_BAD, raw).apply()
                Workspace.empty()
            } else {
                workspace
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Workspace payload failed to decode; starting fresh", t)
            prefs.edit().putString(KEY_BAD, raw).apply()
            Workspace.empty()
        }
    }

    private fun write(workspace: Workspace) {
        try {
            prefs.edit()
                .putString(KEY_WORKSPACE, json.encodeToString(Workspace.serializer(), workspace))
                .apply()
        } catch (t: Throwable) {
            // Persisting a layout is never worth crashing the Home app over.
            Log.e(TAG, "Workspace write failed", t)
        }
    }
}
