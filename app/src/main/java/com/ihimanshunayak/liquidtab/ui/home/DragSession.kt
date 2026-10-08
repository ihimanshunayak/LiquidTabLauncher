/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher - drag session and layout registry.
 *
 * Name      : DragSession.kt
 * Version   : 1.0.0
 * Purpose   : The state a finger drag needs while it is happening, and the
 *             registry of where the laid-out cells actually are. Kept out of
 *             HomeScreen so the geometry can be read on its own, and so the
 *             registry's lifetime rules are stated in one place.
 *
 * Notes     : The registry is an ordinary mutable map written from
 *             onGloballyPositioned and read only while a drag is in flight.
 *             It is deliberately not snapshot state: nothing draws from it, so
 *             making every cell's position observable would recompose the grid
 *             on every scroll for no visible gain.
 *
 *             Drop targets are the data layer's own types, so the placement
 *             rules never see a Compose type.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.ihimanshunayak.liquidtab.data.WorkspaceOps

/**
 * An in-progress drag. Satisfies the data layer's [WorkspaceOps.DragTarget] so
 * the placement rules never see a Compose type.
 */
data class DragSession(
    override val itemKey: String,
    val containerId: String,
    val position: Offset,
    override val target: WorkspaceOps.DropTarget? = null,
) : WorkspaceOps.DragTarget

/**
 * Cell geometry, in root coordinates, for the page and dock currently laid out.
 *
 * Both containers stay registered for as long as they are composed, which is
 * what lets a drag cross from a page into the dock - the dock is composed the
 * whole time, so its cells are always answerable.
 */
class CellRegistry {

    private class Cell(
        val key: String,
        val bounds: Rect,
        val index: Int,
        /** Set when this cell is a folder icon and may receive a filed app. */
        val folderId: String? = null,
        /** Where a filed app lands inside that folder. */
        val folderIndex: Int = 0,
    )

    private val pageCells = HashMap<String, Cell>()
    private val dockCells = HashMap<String, Cell>()
    private val folderCells = HashMap<String, Cell>()
    private val pageOwner = HashMap<String, String>()

    /** The dock plate's own rectangle, so a miss inside it can still dock. */
    var dockBounds: Rect? = null

    /**
     * Registers one grid cell. A cell whose item is a folder also answers as a
     * folder target, which is what makes "drop an app onto a folder icon" work.
     *
     * The page each cell belongs to is recorded here rather than being read
     * from "the page that laid out last". `beyondViewportPageCount` keeps the
     * neighbouring pages composed, so they register cells too and the last
     * writer is whichever page happened to lay out most recently - not the one
     * the user is looking at. A drop resolved through a cell always knows its
     * own page, so resolving it that way is both simpler and correct while a
     * swipe is still settling.
     */
    fun registerPageCell(
        pageId: String,
        key: String,
        index: Int,
        bounds: Rect,
        folderId: String? = null,
        folderIndex: Int = 0,
    ) {
        pageOwner[key] = pageId
        pageCells[key] = Cell(key, bounds, index)
        if (folderId != null) {
            folderCells[key] = Cell(key, bounds, index, folderId, folderIndex)
        } else {
            folderCells.remove(key)
        }
    }

    /**
     * Registers a folder docked in the dock. Dock folders are drop targets for
     * the same reason page folders are; the registry is shared, so a folder's
     * own rect cannot shadow the page cell underneath it - folders are checked
     * first on purpose.
     */
    fun registerDockFolderCell(key: String, index: Int, bounds: Rect, folderId: String, folderIndex: Int) {
        folderCells[key] = Cell(key, bounds, index, folderId, folderIndex)
    }

    fun registerDockCell(key: String, index: Int, bounds: Rect) {
        dockCells[key] = Cell(key, bounds, index)
    }

    /**
     * Forgets a page's cells when it leaves composition. Without this a page
     * the pager has since dropped still answers for the space it used to
     * occupy, and a drag resolves to an item that is no longer drawn.
     */
    fun forgetPageCells(pageId: String) {
        val gone = pageOwner.filterValues { it == pageId }.keys.toList()
        gone.forEach { key ->
            pageCells.remove(key)
            folderCells.remove(key)
            pageOwner.remove(key)
        }
    }

    fun clear() {
        pageCells.clear()
        dockCells.clear()
        folderCells.clear()
        pageOwner.clear()
        dockBounds = null
    }

    /**
     * Resolves [position] to a drop target, or null when the finger is over
     * nothing droppable - which is what makes "release over empty space" leave
     * the layout alone instead of guessing at an index.
     *
     * Folders win over the page underneath them, and the dock wins over the
     * page it overlaps.
     */
    fun targetAt(position: Offset, draggedKey: String): WorkspaceOps.DropTarget? {
        // A folder is a folder wherever it lives, so both registries are
        // searched before anything that would move the item to a page or the
        // dock: dropping an app on a folder must file it, not displace it.
        folderCells.values.firstOrNull { it.bounds.contains(position) && it.key != draggedKey }?.let {
            val folderId = it.folderId
            if (folderId != null) {
                return WorkspaceOps.DropTarget.Folder(folderId = folderId, index = it.folderIndex)
            }
        }

        val dock = dockBounds
        if (dock != null && dock.contains(position)) {
            dockCells.values.firstOrNull { it.bounds.contains(position) && it.key != draggedKey }?.let {
                return WorkspaceOps.DropTarget.Dock(index = it.index)
            }
            // A miss inside the dock plate docks at the end: the plate is small
            // and its gutters are narrower than any finger.
            return WorkspaceOps.DropTarget.Dock(index = dockCells.size)
        }

        pageCells.values.firstOrNull { it.bounds.contains(position) && it.key != draggedKey }?.let {
            // The cell's own page, not "the page laid out last": while a swipe
            // is settling both pages are composed and only the cell knows which
            // one it belongs to.
            val owner = pageOwner[it.key] ?: return null
            return WorkspaceOps.DropTarget.Page(pageId = owner, index = it.index)
        }

        return null
    }
}

@Composable
fun rememberCellRegistry(): CellRegistry = remember { CellRegistry() }

/**
 * Mutable holder for the current drag and the root size it happens in, so the
 * overlay can clamp the lifted icon to the window without the screen
 * recomposing on every move.
 */
class DragSessionState {

    var session by mutableStateOf<DragSession?>(null)
        private set

    var bounds by mutableStateOf(IntSize.Zero)

    fun start(itemKey: String, containerId: String, position: Offset) {
        session = DragSession(itemKey, containerId, position)
    }

    fun move(position: Offset, target: WorkspaceOps.DropTarget?) {
        session = session?.copy(position = position, target = target)
    }

    fun end(): DragSession? {
        val finished = session
        session = null
        return finished
    }

    /** Keeps the finger position inside the window so the lifted icon stays visible. */
    fun clamp(position: Offset, iconRadius: Float): Offset {
        if (bounds == IntSize.Zero) return position
        return Offset(
            x = position.x.coerceIn(iconRadius, (bounds.width - iconRadius).coerceAtLeast(iconRadius)),
            y = position.y.coerceIn(iconRadius, (bounds.height - iconRadius).coerceAtLeast(iconRadius)),
        )
    }
}

@Composable
fun rememberDragSessionState(): DragSessionState = remember { DragSessionState() }