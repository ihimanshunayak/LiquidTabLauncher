/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher - the Home screen root.
 *
 * Name      : HomeActivity.kt
 * Version   : 1.0.0
 * Purpose   : The CATEGORY_HOME activity. It goes edge to edge, records the
 *             backdrop layer the liquid glass surfaces sample, supplies the
 *             glass composition locals, and hosts the launcher's screens: Home,
 *             the app library, search, and the Control Center.
 *
 * Notes     : singleTask, stateNotNeeded and excludeFromRecents are the standard
 *             Home requirements - the launcher keeps its state across a Home
 *             press and never appears in the recents list.
 *
 *             The window is transparent and shows the wallpaper, so the
 *             wallpaper is visible on the very first frame rather than after
 *             Compose's first draw, which is what makes a launcher feel instant
 *             instead of merely fast.
 */

package com.ihimanshunayak.liquidtab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.ThemeMode
import com.ihimanshunayak.liquidtab.settings.SettingsActivity
import com.ihimanshunayak.liquidtab.ui.controls.ControlCenter
import com.ihimanshunayak.liquidtab.ui.glass.LocalAppBackdrop
import com.ihimanshunayak.liquidtab.ui.glass.LocalLiquidGlassEnabled
import com.ihimanshunayak.liquidtab.ui.glass.LocalReduceDynamicBlur
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.backdrops.layerBackdrop
import com.ihimanshunayak.liquidtab.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import com.ihimanshunayak.liquidtab.ui.glass.isGlassSupported
import com.ihimanshunayak.liquidtab.ui.home.HomeScreen
import com.ihimanshunayak.liquidtab.ui.library.AppLibrary
import com.ihimanshunayak.liquidtab.ui.theme.LiquidTabTheme
import com.ihimanshunayak.liquidtab.ui.theme.SystemBarIcons
import com.ihimanshunayak.liquidtab.util.launchApp

/** Which full-screen surface is currently over Home. */
private enum class Surface { NONE, LIBRARY, SEARCH }

class HomeActivity : ComponentActivity() {

    /**
     * Counts the Home presses the system delivers while the launcher is already
     * the foreground app. HomeScreen reads a change in this number as "the user
     * pressed Home", which is the only reliable way to know: a launcher that is
     * already resumed gets no lifecycle callback for a Home press.
     *
     * A counter rather than a boolean because it is the transition that carries
     * meaning - pressing Home twice must scroll twice.
     */
    private var homePressTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by LauncherSettings.themeMode.state.collectAsStateWithLifecycle()
            val glassEnabled by LauncherSettings.glassEnabled.state.collectAsStateWithLifecycle()
            val reduceDynamicBlur by LauncherSettings.reduceDynamicBlur.state.collectAsStateWithLifecycle()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LiquidTabTheme(darkTheme = darkTheme) {
                SystemBarIcons(dark = !darkTheme)

                // The window's background is the floor every page is drawn
                // against. Laying it into the recorded layer keeps the recording
                // opaque: without it a glass surface would sample straight
                // through to the wallpaper.
                val windowBackground = MaterialTheme.colorScheme.background
                val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
                    {
                        drawRect(windowBackground)
                        drawContent()
                    }
                }
                val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
                val glassActive = glassEnabled && isGlassSupported()
                val glassSamplesBackdrop = glassActive && !reduceDynamicBlur

                CompositionLocalProvider(
                    LocalLiquidGlassEnabled provides glassEnabled,
                    LocalReduceDynamicBlur provides reduceDynamicBlur,
                    LocalAppBackdrop provides appBackdrop,
                ) {
                    // The recorded layer is handed to the background, not to
                    // this root, and that placement is load-bearing: a surface
                    // that samples a layer must never be a descendant of the
                    // node that records it. The dock is the sampler here, so
                    // recording the whole window would put the dock inside the
                    // layer it draws and Android would recurse down that cycle
                    // until the render thread ran out of stack - a native crash
                    // at launch, with no Kotlin frame to point at it.
                    //
                    // Recording the background alone is also all the dock ever
                    // needs: the grid stops above the dock, so wallpaper is the
                    // entire picture behind that glass.
                    LauncherRoot(
                        backgroundModifier = if (glassSamplesBackdrop) {
                            Modifier.layerBackdrop(appBackdrop)
                        } else {
                            Modifier
                        },
                        homePressTick = homePressTick,
                    )
                }
            }
        }
    }

    /**
     * A launch that lands on the activity that is already in front.
     *
     * singleTask means a Home press resumes this instance instead of building a
     * new one, so this callback - not onCreate - is where a Home press is
     * observed. The tick is bumped only for an actual launcher launch: an
     * unrelated re-delivery of the same intent must not scroll the grid.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isHomePress = intent.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_HOME) == true
        if (isHomePress) homePressTick++
    }
}

/**
 * Home plus the surfaces that open over it.
 *
 * The overlays are drawn in the same window rather than as separate activities:
 * search and the library are a swipe away, and a launcher that starts an
 * activity to show them pays a window transition for something that should be
 * instantaneous.
 */
@Composable
private fun LauncherRoot(
    backgroundModifier: Modifier,
    homePressTick: Int,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiquidTabApp
    val apps by app.appRepository.apps.collectAsStateWithLifecycle()

    var surface by remember { mutableStateOf(Surface.NONE) }
    var showControls by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // The Home press the grid reacts to, as opposed to the press the activity
    // counted. A press that has a surface to close is spent doing that - the
    // same order the system launcher uses, so a stray Home press in the library
    // comes back to Home rather than rearranging it underneath.
    var gridTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(homePressTick) {
        if (homePressTick == 0) return@LaunchedEffect
        when {
            showControls -> showControls = false
            surface != Surface.NONE -> {
                surface = Surface.NONE
                query = ""
            }
            else -> gridTick++
        }
    }

    LaunchedEffect(Unit) { app.appRepository.refresh() }

    // An app installed or uninstalled while the launcher was not on screen must
    // appear without the user having to restart it, so the list is re-read every
    // time Home comes back to the front. The repository's mutex collapses this
    // with any scan already in flight, and a launcher that hides a freshly
    // installed app until the process dies is indistinguishable from one that
    // lost it.
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeScope.launch { app.appRepository.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back closes the topmost surface before the system handles it.
    BackHandler(enabled = showControls || surface != Surface.NONE) {
        when {
            showControls -> showControls = false
            surface != Surface.NONE -> {
                surface = Surface.NONE
                query = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val measuredWidth = maxWidth
            val measuredHeight = maxHeight

            HomeScreen(
                windowWidth = measuredWidth,
                windowHeight = measuredHeight,
                onOpenLibrary = { surface = Surface.LIBRARY },
                homePressTick = gridTick,
                backgroundModifier = backgroundModifier,
                // A drag from the top-right corner or downwards from the top
                // edge is the Control Center; a swipe up from the bottom edge is
                // the app library. Both are measured against the window's own
                // size, so they land where a hand expects on a tablet held in
                // either orientation.
                //
                // Only these two gestures are claimed. Android's own navigation
                // gestures are left alone: the detector watches the areas the
                // system does not use, and a drag anywhere else reaches the grid
                // beneath it.
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(measuredWidth, measuredHeight) {
                        var travel = 0f
                        var startedAtY = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                travel = 0f
                                startedAtY = offset.y
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                travel += amount.y
                                val fromTop = startedAtY < 96f
                                val fromBottom = startedAtY > size.height - 96f
                                when {
                                    travel > 90f && fromTop && !showControls -> showControls = true
                                    travel < -90f && fromBottom && surface == Surface.NONE ->
                                        surface = Surface.LIBRARY
                                }
                            },
                        )
                    },
            )
        }

        // ── App library ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = surface == Surface.LIBRARY,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            LibrarySurface(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    surface = Surface.NONE
                    query = ""
                },
            )
        }

        // ── Search ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = surface == Surface.SEARCH,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            LibrarySurface(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    surface = Surface.NONE
                    query = ""
                },
                autofocus = true,
            )
        }

        // ── Control Center ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ControlCenter(
                onDismiss = { showControls = false },
                onOpenLibrary = {
                    showControls = false
                    surface = Surface.LIBRARY
                },
                onOpenSearch = {
                    showControls = false
                    surface = Surface.SEARCH
                },
                onOpenSettings = {
                    showControls = false
                    context.startActivity(
                        android.content.Intent(context, SettingsActivity::class.java),
                    )
                },
            )
        }
    }
}

/**
 * A full-screen surface showing the app library.
 *
 * Search and the library are the same view with different framing: search opens
 * with the field focused and the list already filtered, the library opens with
 * everything listed. Building them as one surface is why "search" here can offer
 * the full catalogue the moment the query is cleared.
 */
@Composable
private fun LibrarySurface(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autofocus: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiquidTabApp
    val apps by app.appRepository.apps.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
    ) {
        AppLibrary(
            apps = apps,
            query = query,
            onQueryChange = onQueryChange,
            autofocus = autofocus,
            onOpen = { entry ->
                if (launchApp(context, entry.ref)) {
                    onClose()
                } else {
                    com.ihimanshunayak.liquidtab.util.toast(
                        context,
                        context.getString(R.string.toast_could_not_open, entry.label),
                    )
                }
            },
            onLongPress = { entry ->
                com.ihimanshunayak.liquidtab.util.openAppInfo(context, entry.packageName)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}