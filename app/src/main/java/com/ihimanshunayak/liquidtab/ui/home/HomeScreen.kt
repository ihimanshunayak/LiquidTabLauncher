/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher - Home screen.
 *
 * Name      : HomeScreen.kt
 * Version   : 1.0.0
 * Purpose   : The launcher itself: the layered background, a horizontally
 *             paged grid of shortcuts and folders, the glass dock, the page
 *             indicator, the long-press menu and the drag overlay. Also owns
 *             the app-icon resolution pass, which is the one asynchronous thing
 *             the first frame depends on.
 *
 * Notes     : The grid is measured, never assumed. There is no "tablet layout"
 *             branch - the column count is derived from the window's width and
 *             the design's cell metrics, so the same composable produces a
 *             6-column portrait grid and a 10-column landscape one with nothing
 *             that can disagree with the window it is actually in.
 *
 *             Recomposition is treated as a budget. The workspace is read once
 *             at this level and passed down as values; icon bitmaps are resolved
 *             in a single coroutine pass and cached by the repository; widgets
 *             schedule their own updates. Nothing on this screen ticks.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihimanshunayak.liquidtab.LiquidTabApp
import com.ihimanshunayak.liquidtab.data.AppEntry
import com.ihimanshunayak.liquidtab.data.AppRef
import com.ihimanshunayak.liquidtab.data.LauncherSettings
import com.ihimanshunayak.liquidtab.data.LauncherStore
import com.ihimanshunayak.liquidtab.data.Workspace
import com.ihimanshunayak.liquidtab.data.WorkspaceItem
import com.ihimanshunayak.liquidtab.data.WorkspaceOps
import com.ihimanshunayak.liquidtab.ui.glass.glassContentColor
import com.ihimanshunayak.liquidtab.ui.glass.lightweightLiquidGlass
import com.ihimanshunayak.liquidtab.ui.glass.liquidGlass
import com.ihimanshunayak.liquidtab.ui.haptics.Haptic
import com.ihimanshunayak.liquidtab.ui.haptics.rememberHaptics
import com.ihimanshunayak.liquidtab.util.launchApp
import com.ihimanshunayak.liquidtab.util.openAppInfo
import com.ihimanshunayak.liquidtab.util.toast
import kotlinx.coroutines.launch

/** Cell metrics the grid is built from. Column count is an output of these. */
private val CELL_MIN_WIDTH = 96.dp
private val CELL_SPACING = 12.dp
private val GRID_HORIZONTAL_PADDING = 24.dp
private const val MAX_COLUMNS = 10
private val DOCK_ICON_SIZE = 56.dp
private val DOCK_CELL_WIDTH = 68.dp

@Composable
fun HomeScreen(
    windowWidth: Dp,
    windowHeight: Dp,
    onOpenLibrary: () -> Unit = {},
    homePressTick: Int = 0,
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiquidTabApp
    val repository = app.appRepository

    val workspace by LauncherStore.workspace.collectAsStateWithLifecycle()
    val apps by repository.apps.collectAsStateWithLifecycle()
    val installedApps = remember(apps) { apps.associateBy { it.key } }

    val wallpaperMode by LauncherSettings.wallpaperMode.state.collectAsStateWithLifecycle()
    val gridColumnsSetting by LauncherSettings.gridColumns.state.collectAsStateWithLifecycle()
    val showLabels by LauncherSettings.showLabels.state.collectAsStateWithLifecycle()
    val dockMax by LauncherSettings.dockMaxItems.state.collectAsStateWithLifecycle()
    val reduceMotion by LauncherSettings.reduceMotion.state.collectAsStateWithLifecycle()
    val showPageIndicator by LauncherSettings.showPageIndicator.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // One scan per composition of the screen. The repository's own mutex
    // collapses a duplicate call, so stating this plainly is safe.
    LaunchedEffect(Unit) { repository.refresh() }

    val columns = resolveColumns(windowWidth, gridColumnsSetting)
    val iconSize = iconSizeFor(resolveCellWidth(windowWidth, columns))

    // Icons for everything currently laid out, resolved in one pass and cached
    // by the repository. Re-runs when the set of shortcuts or the icon box
    // changes - never per frame.
    var icons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val wantedKeys = remember(workspace) { workspaceAppKeys(workspace) }
    val density = LocalDensity.current

    LaunchedEffect(wantedKeys, apps, iconSize, density) {
        if (apps.isEmpty()) return@LaunchedEffect
        // `iconSize` is a Dp and a bitmap is measured in pixels. Passing the dp
        // value straight through under-samples the drawable by the density
        // factor, which is invisible at mdpi and visibly soft from xhdpi up —
        // and every modern tablet is xhdpi or denser.
        val sizePx = with(density) { iconSize.roundToPx() }.coerceAtLeast(1)
        val byKey = apps.associateBy { it.key }
        val resolved = HashMap<String, ImageBitmap>(wantedKeys.size)
        for (key in wantedKeys) {
            val entry = byKey[key] ?: continue
            repository.icon(entry, sizePx)?.let { resolved[key] = it }
        }
        icons = resolved
    }

    // Seed a first-run workspace once apps are known, and only when the
    // workspace is genuinely empty: seeding from an empty app list would write
    // an empty Home screen and then never try again.
    //
    // Guarded by a persisted flag as well as by emptiness. Emptiness alone is
    // not evidence the user has never had a Home screen — a user who deletes
    // every shortcut has an empty Home *on purpose*, and re-seeding it would be
    // the launcher overruling them.
    LaunchedEffect(apps, dockMax) {
        if (apps.isEmpty() || LauncherSettings.homeSeeded.value) return@LaunchedEffect
        LauncherSettings.homeSeeded.value = true
        LauncherStore.update { current ->
            if (current.pages.all { it.items.isEmpty() } && current.dock.isEmpty()) {
                WorkspaceOps.firstRun(apps, dockMax)
            } else {
                current
            }
        }
    }

    // Drop shortcuts to apps that no longer exist, and any page left empty by
    // that. Only ever removes.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val installed = apps.mapTo(HashSet()) { it.packageName }
        LauncherStore.update { current -> WorkspaceOps.pruneMissing(current, installed) }
    }

    val dragState = rememberDragSessionState()
    val registry = rememberCellRegistry()
    var menuTarget by remember { mutableStateOf<WorkspaceItem?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showPageManager by remember { mutableStateOf(false) }

    // The folder currently open, read from the live workspace rather than held
    // as a copy: a folder that dissolves itself must close its own panel.
    val openFolder = remember(workspace, openFolderId) {
        openFolderId?.let { id -> WorkspaceOps.folder(workspace, id) }
    }
    LaunchedEffect(openFolderId, openFolder) {
        if (openFolderId != null && openFolder == null) openFolderId = null
    }

    val pagerState = rememberPagerState(
        initialPage = workspace.defaultPageIndex(),
        pageCount = { workspace.pages.size.coerceAtLeast(1) },
    )

    // A Home press while the launcher is already on screen comes back to the
    // page the user nominated. The tick changes only when a press actually
    // happens, so nothing scrolls merely because the workspace recomposed.
    LaunchedEffect(homePressTick, workspace.defaultPageId) {
        if (homePressTick == 0) return@LaunchedEffect
        val target = workspace.defaultPageIndex()
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    BackHandler(
        enabled = menuTarget != null || dragState.session != null || openFolder != null ||
            showWidgetPicker || showPageManager,
    ) {
        when {
            showPageManager -> showPageManager = false
            showWidgetPicker -> showWidgetPicker = false
            openFolder != null -> openFolderId = null
            menuTarget != null -> menuTarget = null
            else -> dragState.end()
        }
    }

    /** Starts an app, and corrects the layout when the app is gone. */
    fun open(ref: AppRef) {
        if (launchApp(context, ref)) return
        haptics.play(Haptic.ToggleOff)
        toast(context, "That app is no longer installed")
        // A dead shortcut that survives a relaunch is worse than one that is
        // gone, so the correction is immediate rather than deferred to the next
        // package broadcast.
        LauncherStore.update { WorkspaceOps.remove(it, WorkspaceItem.App(ref).key) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(registry) {
                // A long press that lands on an icon belongs to that icon — the
                // press-and-hold drag picks it up. Only a press on empty space
                // opens the page manager, and the registry is what tells the two
                // apart, in the same root coordinates the drag already uses.
                detectTapGestures(
                    onLongPress = { position ->
                        if (registry.targetAt(position, "") == null) {
                            haptics.play(Haptic.Expand)
                            showPageManager = true
                        }
                    },
                )
            },
    ) {
        dragState.bounds = androidx.compose.ui.unit.IntSize(
            constraints.maxWidth,
            constraints.maxHeight,
        )
        val cellWidth = resolveCellWidth(windowWidth, columns)

        // The recorded backdrop layer lives here, on the background alone.
        //
        // The dock below is the one surface that samples this layer, and a
        // surface must never be a descendant of the node that records it:
        // putting this modifier on the whole window instead - which is what a
        // full-screen glass dock invites - nested the dock inside its own
        // backdrop. Android then walked that cycle in RenderNode::prepareTreeImpl
        // until the render thread ran out of stack and the process died with a
        // SIGSEGV in libhwui, pointing at nothing in this code.
        //
        // Nothing is lost by recording only the wallpaper: the grid stops above
        // the dock, so the wallpaper is the entire picture behind that glass.
        HomeBackground(
            wallpaperMode = wallpaperMode,
            artworkUri = null,
            parallaxFraction = pagerScrollFraction(pagerState),
            modifier = backgroundModifier,
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
        ) {
            WorkspacePager(
                modifier = Modifier.weight(1f),
                workspace = workspace,
                pagerState = pagerState,
                columns = columns,
                cellWidth = cellWidth,
                iconSize = iconSize,
                spacing = CELL_SPACING,
                contentPadding = PaddingValues(horizontal = GRID_HORIZONTAL_PADDING),
                installedApps = installedApps,
                icons = icons,
                showLabels = showLabels,
                reduceMotion = reduceMotion,
                dragState = dragState,
                registry = registry,
                onOpen = ::open,
                onMenu = { menuTarget = it },
                onOpenFolder = { folderId -> openFolderId = folderId },
            )

            PageIndicator(
                visible = showPageIndicator,
                pageCount = pagerState.pageCount,
                currentPage = pagerState.currentPage,
                onGoToPage = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
            )

            // The way out of an empty Home screen. A user who removed every
            // shortcut has no icon left to press, so the gesture and a sentence
            // saying so are the only route back to their apps.
            if (workspace.pages.all { it.items.isEmpty() } && workspace.dock.isEmpty()) {
                EmptyHomeHint(
                    onOpenLibrary = onOpenLibrary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 6.dp),
                )
            }

            Dock(
                workspace = workspace,
                installedApps = installedApps,
                icons = icons,
                maxItems = dockMax,
                dragState = dragState,
                registry = registry,
                onOpen = ::open,
                onMenu = { menuTarget = it },
                onOpenFolder = { folderId -> openFolderId = folderId },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GRID_HORIZONTAL_PADDING, vertical = 10.dp),
            )
        }

        dragState.session?.let { session ->
            DragOverlay(
                session = session,
                workspace = workspace,
                icons = icons,
                installedApps = installedApps,
            )
        }

        val folder = openFolder
        if (folder != null) {            FolderPanel(
                folder = folder,
                apps = installedApps,
                icons = icons,
                iconSize = iconSize,
                onOpen = { ref ->
                    // Launch, and close the panel only when the app really
                    // opened — a dead shortcut shows its toast with the folder
                    // still open, so the user is not dropped back to Home.
                    if (launchApp(context, ref)) {
                        openFolderId = null
                    } else {
                        haptics.play(Haptic.ToggleOff)
                        toast(context, "That app is no longer installed")
                    }
                },
                onRemoveApp = { ref ->
                    haptics.play(Haptic.ToggleOff)
                    LauncherStore.update { current ->
                        WorkspaceOps.removeFromFolder(current, folder.id, ref)
                    }
                },
                onRename = { name ->
                    LauncherStore.update { current ->
                        WorkspaceOps.renameFolder(current, folder.id, WorkspaceOps.sanitizeFolderName(name))
                    }
                },
                onOpenAppInfo = { ref -> openAppInfo(context, ref.packageName) },
                onDismiss = { openFolderId = null },
            )
        }

        if (showWidgetPicker) {
            WidgetPicker(
                present = remember(workspace) {
                    workspace.pages
                        .flatMap { page -> page.items }
                        .filterIsInstance<WorkspaceItem.Widget>()
                        .mapTo(HashSet()) { it.kind }
                },
                onAdd = { kind ->
                    // Added to the last page, which is where a new widget is
                    // least likely to displace something the user arranged.
                    LauncherStore.update { current ->
                        val lastPage = current.pages.lastOrNull() ?: return@update current
                        WorkspaceOps.add(
                            current,
                            lastPage.id,
                            WorkspaceItem.Widget(id = "${kind.name.lowercase()}-${System.currentTimeMillis()}", kind = kind),
                        )
                    }
                    haptics.play(Haptic.ToggleOn)
                },
                onRemove = { kind ->
                    val victim = workspace.pages
                        .flatMap { page -> page.items }
                        .filterIsInstance<WorkspaceItem.Widget>()
                        .firstOrNull { it.kind == kind }
                    if (victim != null) {
                        haptics.play(Haptic.ToggleOff)
                        LauncherStore.update { current -> WorkspaceOps.remove(current, victim.key) }
                    }
                },
                onDismiss = { showWidgetPicker = false },
            )
        }

        if (showPageManager) {
            val labels = remember(workspace, installedApps) { workspaceLabels(workspace, installedApps) }
            PageManager(
                workspace = workspace,
                labels = labels,
                visiblePageId = workspace.pages.getOrNull(pagerState.currentPage)?.id,
                onDismiss = { showPageManager = false },
                onCreatePage = {
                    // Appended, so the new page's index is the size before the
                    // add — captured now rather than read back from a workspace
                    // the store has not published yet.
                    val newIndex = workspace.pages.size
                    LauncherStore.update { current -> WorkspaceOps.addPage(current) }
                    // Land on the page just made: it is empty, and the only
                    // reason to have made it is to fill it.
                    scope.launch { pagerState.animateScrollToPage(newIndex) }
                },
                onOpenPage = { pageId ->
                    showPageManager = false
                    val index = workspace.pages.indexOfFirst { it.id == pageId }
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }

        val target = menuTarget
        if (target != null) {
            ItemMenu(
                item = target,
                label = itemLabel(target, installedApps),
                canRemove = true,
                onDismiss = { menuTarget = null },
                onOpen = {
                    menuTarget = null
                    (target as? WorkspaceItem.App)?.let { open(it.ref) }
                },
                onAppInfo = {
                    menuTarget = null
                    (target as? WorkspaceItem.App)?.let { openAppInfo(context, it.ref.packageName) }
                },
                onRemove = {
                    menuTarget = null
                    haptics.play(Haptic.ToggleOff)
                    LauncherStore.update { current ->
                        if (target is WorkspaceItem.Folder) {
                            // Removing a folder from Home spills its apps back
                            // out rather than deleting them.
                            WorkspaceOps.deleteFolder(current, target.folder.id)
                        } else {
                            WorkspaceOps.remove(current, target.key)
                        }
                    }
                },
                onReset = {
                    menuTarget = null
                    // A Home screen with nothing on it and no way back is the one
                    // state a launcher must never reach; this is the exit.
                    LauncherStore.reset()
                    scope.launch { repository.refresh() }
                },
                onAddWidget = {
                    menuTarget = null
                    showWidgetPicker = true
                },
            )
        }
    }
}

// -- Empty Home ----------------------------------------------------------------

/**
 * The escape hatch from an empty Home screen: a sentence and a button, because
 * a user who removed their last shortcut has no icon left to press and the
 * swipe-up gesture is not discoverable from nothing.
 */
@Composable
private fun EmptyHomeHint(
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .lightweightLiquidGlass(shape, fallbackColor = MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenLibrary)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            tint = glassContentColor(),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Open your apps",
            style = MaterialTheme.typography.labelLarge,
            color = glassContentColor(),
        )
    }
}

// -- Layout maths --------------------------------------------------------------

/** The column count for this window: an explicit setting, or derived from width. */
private fun resolveColumns(windowWidth: Dp, configured: Int): Int {
    if (configured > 0) return configured
    val usable = windowWidth - GRID_HORIZONTAL_PADDING * 2
    return ((usable + CELL_SPACING) / (CELL_MIN_WIDTH + CELL_SPACING))
        .toInt()
        .coerceIn(3, MAX_COLUMNS)
}

private fun resolveCellWidth(windowWidth: Dp, columns: Int): Dp =
    (windowWidth - GRID_HORIZONTAL_PADDING * 2 - CELL_SPACING * (columns - 1)) / columns

private fun pagerScrollFraction(state: PagerState): Float {
    val count = state.pageCount.coerceAtLeast(1)
    return ((state.currentPage + state.currentPageOffsetFraction) / count.toFloat()).coerceIn(0f, 1f)
}

/**
 * The page a Home press should land on, as a pager index. A workspace whose
 * nominated page no longer exists — or which never had one — reads as the first
 * page, which is also what a fresh layout means.
 */
private fun Workspace.defaultPageIndex(): Int {
    val index = pages.indexOfFirst { it.id == defaultPageId }
    return if (index >= 0) index else 0
}

/** Every app key the workspace refers to, pages and dock together. */
private fun workspaceAppKeys(workspace: Workspace): Set<String> = buildSet {
    fun collect(item: WorkspaceItem) {
        when (item) {
            is WorkspaceItem.App -> add(item.ref.key)
            is WorkspaceItem.Folder -> item.folder.items.forEach { add(it.key) }
            is WorkspaceItem.Widget -> Unit
        }
    }
    workspace.pages.forEach { page -> page.items.forEach(::collect) }
    workspace.dock.forEach(::collect)
}

// -- Pager ---------------------------------------------------------------------

@Composable
private fun WorkspacePager(
    workspace: Workspace,
    pagerState: PagerState,
    columns: Int,
    cellWidth: Dp,
    iconSize: Dp,
    spacing: Dp,
    contentPadding: PaddingValues,
    installedApps: Map<String, AppEntry>,
    icons: Map<String, ImageBitmap>,
    showLabels: Boolean,
    reduceMotion: Boolean,
    dragState: DragSessionState,
    registry: CellRegistry,
    onOpen: (AppRef) -> Unit,
    onMenu: (WorkspaceItem) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (workspace.pages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Home is empty",
                style = MaterialTheme.typography.titleMedium,
                color = glassContentColor().copy(alpha = 0.6f),
            )
        }
        return
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
    ) { pageIndex ->
        val page = workspace.pages[pageIndex]

        // Cells of a page the pager has dropped must stop answering, or a drag
        // resolves to an item that is no longer drawn.
        DisposableEffect(page.id, registry) {
            onDispose { registry.forgetPageCells(page.id) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            itemsIndexed(
                items = page.items,
                key = { _, item -> item.key },
                span = { _, item ->
                    // A widget strip is a full-width band; apps and folders are
                    // one cell each. This is the whole reason widgets can live
                    // in the same ordered list as everything else.
                    if (item is WorkspaceItem.Widget) {
                        GridItemSpan(maxLineSpan)
                    } else {
                        GridItemSpan(1)
                    }
                },
            ) { index, item ->
                val target = dragState.session?.target
                WorkspaceCell(
                    item = item,
                    index = index,
                    containerId = page.id,
                    apps = installedApps,
                    icons = icons,
                    iconSize = iconSize,
                    showLabels = showLabels,
                    reduceMotion = reduceMotion,
                    dragState = dragState,
                    registry = registry,
                    onOpen = onOpen,
                    onMenu = onMenu,
                    onOpenFolder = onOpenFolder,
                    targetHighlighted = target is WorkspaceOps.DropTarget.Page &&
                        target.pageId == page.id &&
                        target.index == index,
                )
            }
        }
    }
}

// -- Page indicator ------------------------------------------------------------

@Composable
private fun PageIndicator(
    visible: Boolean,
    pageCount: Int,
    currentPage: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1 || !visible) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                // The visible dot is 6-7 dp. The touch target is not: it is a
                // 28 dp square around it, because the dot itself is far below
                // what a finger can reliably hit and a miss would land on the
                // Home grid behind the strip.
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Go to page ${index + 1}",
                        onClick = { onGoToPage(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (selected) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(glassContentColor().copy(alpha = if (selected) 0.95f else 0.35f)),
                )
            }
        }
    }
}

// -- Dock ----------------------------------------------------------------------

@Composable
private fun Dock(
    workspace: Workspace,
    installedApps: Map<String, AppEntry>,
    icons: Map<String, ImageBitmap>,
    maxItems: Int,
    dragState: DragSessionState,
    registry: CellRegistry,
    onOpen: (AppRef) -> Unit,
    onMenu: (WorkspaceItem) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(workspace.dock, maxItems) { workspace.dock.take(maxItems) }
    val shape = RoundedCornerShape(30.dp)
    val haptics = rememberHaptics()

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            registry.dockBounds = coordinates.boundsInRoot()
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .liquidGlass(shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val isDragging = dragState.session?.itemKey == item.key
                    val target = dragState.session?.target
                    Box(
                        Modifier
                            .width(DOCK_CELL_WIDTH)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInRoot()
                                registry.registerDockCell(item.key, index, bounds)
                                // A docked folder is a drop target too, so the
                                // same fill-the-folder gesture works on the dock
                                // as on a page.
                                (item as? WorkspaceItem.Folder)?.let { folder ->
                                    registry.registerDockFolderCell(
                                        key = item.key,
                                        index = index,
                                        bounds = bounds,
                                        folderId = folder.folder.id,
                                        folderIndex = folder.folder.items.size,
                                    )
                                }
                            },
                    ) {
                        when (item) {
                            is WorkspaceItem.App -> AppIcon(
                                label = installedApps[item.ref.key]?.label ?: item.ref.packageName,
                                icon = icons[item.ref.key],
                                iconSize = DOCK_ICON_SIZE,
                                showLabel = false,
                                pressed = isDragging,
                                onClick = { onOpen(item.ref) },
                                onLongClick = {
                                    haptics.play(Haptic.Expand)
                                    dragState.start(item.key, WorkspaceOps.IN_DOCK, Offset.Zero)
                                },
                                onMenuClick = { onMenu(item) },
                            )

                            is WorkspaceItem.Folder -> FolderIcon(
                                folder = item.folder,
                                iconSize = DOCK_ICON_SIZE,
                                showLabels = false,
                                pressed = isDragging,
                                onClick = { onOpenFolder(item.folder.id) },
                                onLongClick = {
                                    haptics.play(Haptic.Expand)
                                    dragState.start(item.key, WorkspaceOps.IN_DOCK, Offset.Zero)
                                },
                                onMenuClick = { onMenu(item) },
                            )

                            is WorkspaceItem.Widget -> Unit
                        }

                        if (target is WorkspaceOps.DropTarget.Dock && target.index == index) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        width = 2.dp,
                                        color = glassContentColor().copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(18.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}