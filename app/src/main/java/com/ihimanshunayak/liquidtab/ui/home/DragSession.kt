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

    private class Cell(val key: String, val bounds: Rect, val index: Int)

    private val pageCells = HashMap<String, Cell>()
    private val dockCells = HashMap<String, Cell>()
    private val folderCells = HashMap<String, Cell>()
    private val pageOwner = HashMap<String, String>()

    /** The dock plate's own rectangle, so a miss inside it can still dock. */
    var dockBounds: Rect? = null

    /** The page currently laid out; a drag with no better answer lands here. */
    var visiblePageId: String = ""

    fun registerPageCell(pageId: String, key: String, index: Int, bounds: Rect) {
        visiblePageId = pageId
        pageOwner[key] = pageId
        pageCells[key] = Cell(key, bounds, index)
    }

    fun registerDockCell(key: String, index: Int, bounds: Rect) {
        dockCells[key] = Cell(key, bounds, index)
    }

    fun registerFolderCell(folderId: String, key: String, index: Int, bounds: Rect) {
        folderCells[key] = Cell(key, bounds, index)
    }

    /** Forgets a page's cells when it leaves composition. */
    fun forgetPageCells(pageId: String) {
        val gone = pageOwner.filterValues { it == pageId }.keys
        gone.forEach { pageCells.remove(it); pageOwner.remove(it) }
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
        folderCells.values.firstOrNull { it.bounds.contains(position) && it.key != draggedKey }?.let {
            return WorkspaceOps.DropTarget.Folder(folderId = it.key, index = it.index)
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
            return WorkspaceOps.DropTarget.Page(pageId = visiblePageId, index = it.index)
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