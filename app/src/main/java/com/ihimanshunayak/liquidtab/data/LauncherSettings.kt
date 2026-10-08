/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — settings store.
 *
 * Name      : LauncherSettings.kt
 * Version   : 1.0.0
 * Purpose   : Every user-facing preference the launcher ships, persisted in
 *             SharedPreferences and exposed as StateFlows so Compose reads them
 *             reactively. This is the same storage approach FreeMusic uses
 *             (plain SharedPreferences + MutableStateFlow), and the store is
 *             distinct from any playback or playlist data as the design
 *             requires.
 *
 * Notes     : Only settings that are actually implemented are declared.
 *             Widget presence is deliberately NOT a setting — the workspace is
 *             the single source of truth, so a toggle can never disagree with
 *             the Home screen.
 */

package com.ihimanshunayak.liquidtab.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the launcher picks its light/dark scheme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the Home background comes from. */
enum class WallpaperMode {
    /** A generated mesh gradient derived from the theme. */
    GRADIENT,

    /** A user-picked image from the photo picker. */
    IMAGE,

    /** The now-playing artwork, blurred — the FreeMusic-identity background. */
    ARTWORK,
}

/** How the app library lays out its apps when no search is active. */
enum class LibraryStyle { LIST, GRID }

object LauncherSettings {

    private const val STORE = "liquid_tab_settings"

    private lateinit var prefs: SharedPreferences

    private fun key(name: String) = name

    // ── Appearance ────────────────────────────────────────────────────────────

    val themeMode = EnumSetting(
        "theme_mode",
        ThemeMode.SYSTEM,
        ThemeMode.entries.map { it.name to it }.toMap(),
    )

    val glassEnabled = BoolSetting("glass_enabled", default = true)

    val reduceDynamicBlur = BoolSetting("reduce_dynamic_blur", default = false)

    /** 0 means "derive from window width"; anything else is a fixed column count. */
    val gridColumns = IntSetting("grid_columns", default = 0)

    /** 1.0 is the design's own icon size; clamped by the UI when read. */
    val iconScale = FloatSetting("icon_scale", default = 1.0f)

    val showLabels = BoolSetting("show_labels", default = true)

    /**
     * Whether the dots under the grid are drawn. On by default — with more than
     * one page the dots are the only thing that says so — but a user who reads
     * their layout from memory can reclaim the strip.
     */
    val showPageIndicator = BoolSetting("show_page_indicator", default = true)

    // ── Dock ──────────────────────────────────────────────────────────────────

    val dockMaxItems = IntSetting("dock_max_items", default = 6)

    // ── Background ────────────────────────────────────────────────────────────

    val wallpaperMode = EnumSetting(
        "wallpaper_mode",
        WallpaperMode.GRADIENT,
        WallpaperMode.entries.map { it.name to it }.toMap(),
    )

    val wallpaperUri = StringSetting("wallpaper_uri", default = "")

    val parallax = BoolSetting("parallax", default = false)

    val wallpaperParallaxAmount = FloatSetting("wallpaper_parallax_amount", default = 0.18f)

    // ── Behaviour ─────────────────────────────────────────────────────────────

    val reduceMotion = BoolSetting("reduce_motion", default = false)

    val libraryStyle = EnumSetting(
        "library_style",
        LibraryStyle.LIST,
        LibraryStyle.entries.map { it.name to it }.toMap(),
    )

    // ── Weather ───────────────────────────────────────────────────────────────

    val weatherCity = StringSetting("weather_city", default = "")

    val weatherLatitude = FloatSetting("weather_latitude", default = 0.0f)

    val weatherLongitude = FloatSetting("weather_longitude", default = 0.0f)

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Creates the store and loads every value. Called once from
     * [com.ihimanshunayak.liquidtab.LiquidTabApp]; reading a setting before
     * this has run is a programming error and throws rather than silently
     * returning defaults.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        themeMode.load()
        glassEnabled.load()
        reduceDynamicBlur.load()
        gridColumns.load()
        iconScale.load()
        showLabels.load()
        showPageIndicator.load()
        dockMaxItems.load()
        wallpaperMode.load()
        wallpaperUri.load()
        parallax.load()
        wallpaperParallaxAmount.load()
        reduceMotion.load()
        libraryStyle.load()
        weatherCity.load()
        weatherLatitude.load()
        weatherLongitude.load()
    }

    // ── Backing types ─────────────────────────────────────────────────────────

    /**
     * The storage contract each setting type implements. Values are written with
     * `apply()` so a setting change never blocks the frame that made it.
     */
    interface Setting<T> {
        val state: StateFlow<T>
        var value: T
        fun load()
    }

    class BoolSetting(private val name: String, private val default: Boolean) : Setting<Boolean> {
        private val _state = MutableStateFlow(default)
        override val state: StateFlow<Boolean> = _state.asStateFlow()
        override var value: Boolean
            get() = _state.value
            set(v) {
                if (_state.value == v) return
                _state.value = v
                prefs.edit().putBoolean(name, v).apply()
            }

        override fun load() {
            _state.value = prefs.getBoolean(name, default)
        }
    }

    class IntSetting(private val name: String, private val default: Int) : Setting<Int> {
        private val _state = MutableStateFlow(default)
        override val state: StateFlow<Int> = _state.asStateFlow()
        override var value: Int
            get() = _state.value
            set(v) {
                if (_state.value == v) return
                _state.value = v
                prefs.edit().putInt(name, v).apply()
            }

        override fun load() {
            _state.value = prefs.getInt(name, default)
        }
    }

    class FloatSetting(private val name: String, private val default: Float) : Setting<Float> {
        private val _state = MutableStateFlow(default)
        override val state: StateFlow<Float> = _state.asStateFlow()
        override var value: Float
            get() = _state.value
            set(v) {
                if (_state.value == v) return
                _state.value = v
                prefs.edit().putFloat(name, v).apply()
            }

        override fun load() {
            _state.value = prefs.getFloat(name, default)
        }
    }

    class StringSetting(private val name: String, private val default: String) : Setting<String> {
        private val _state = MutableStateFlow(default)
        override val state: StateFlow<String> = _state.asStateFlow()
        override var value: String
            get() = _state.value
            set(v) {
                if (_state.value == v) return
                _state.value = v
                prefs.edit().putString(name, v).apply()
            }

        override fun load() {
            _state.value = prefs.getString(name, default) ?: default
        }
    }

    /**
     * Enum stored by name. An unrecognised name — a downgrade, or a value
     * removed in a later build — loads the default rather than throwing.
     */
    class EnumSetting<T : Enum<T>>(
        private val name: String,
        private val default: T,
        private val byName: Map<String, T>,
    ) : Setting<T> {
        private val _state = MutableStateFlow(default)
        override val state: StateFlow<T> = _state.asStateFlow()
        override var value: T
            get() = _state.value
            set(v) {
                if (_state.value == v) return
                _state.value = v
                prefs.edit().putString(name, v.name).apply()
            }

        override fun load() {
            _state.value = prefs.getString(name, null)?.let(byName::get) ?: default
        }
    }
}
