/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — drop-target resolution, under test.
 *
 * Name      : CellRegistryTest.kt
 * Version   : 1.0.0
 * Purpose   : CellRegistry answers one question — "where would this finger
 *             position drop the item?" — and getting it wrong moves a user's
 *             app somewhere they did not ask for. The registry's inputs and
 *             outputs are plain geometry and data types, so the answers can be
 *             checked here rather than by dragging icons on a device.
 *
 * Notes     : Rect and Offset are Compose geometry, but they are value types
 *             with no Android dependency, so they construct in a JVM test.
 */

package com.ihimanshunayak.liquidtab.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ihimanshunayak.liquidtab.data.WorkspaceOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellRegistryTest {

    private val cell = Rect(left = 0f, top = 0f, right = 100f, bottom = 100f)
    private val second = Rect(left = 100f, top = 0f, right = 200f, bottom = 100f)
    private val inside = Offset(50f, 50f)
    private val insideSecond = Offset(150f, 50f)

    // -- pages -----------------------------------------------------------------

    @Test
    fun `a page cell resolves to its own page`() {
        val registry = CellRegistry()
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = cell)

        assertEquals(
            WorkspaceOps.DropTarget.Page(pageId = "page-1", index = 0),
            registry.targetAt(inside, draggedKey = "app:z"),
        )
    }

    @Test
    fun `a cell resolves to its own page even when a neighbour laid out later`() {
        // `beyondViewportPageCount` keeps the neighbouring pages composed, so
        // both register cells. Resolving through the cell is what keeps a drop
        // on page 1 from landing on page 2 merely because page 2 was laid out
        // most recently.
        val registry = CellRegistry()
        registry.registerPageCell("page-2", "app:b", index = 0, bounds = cell)
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = second)

        assertEquals(
            WorkspaceOps.DropTarget.Page(pageId = "page-2", index = 0),
            registry.targetAt(inside, draggedKey = "app:z"),
        )
        assertEquals(
            WorkspaceOps.DropTarget.Page(pageId = "page-1", index = 0),
            registry.targetAt(insideSecond, draggedKey = "app:z"),
        )
    }

    @Test
    fun `a forgotten page stops answering`() {
        val registry = CellRegistry()
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = cell)
        registry.forgetPageCells("page-1")

        assertNull(registry.targetAt(inside, draggedKey = "app:z"))
    }

    @Test
    fun `forgetting one page leaves another answering`() {
        val registry = CellRegistry()
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = cell)
        registry.registerPageCell("page-2", "app:b", index = 0, bounds = second)
        registry.forgetPageCells("page-1")

        assertNull(registry.targetAt(inside, draggedKey = "app:z"))
        assertEquals(
            WorkspaceOps.DropTarget.Page(pageId = "page-2", index = 0),
            registry.targetAt(insideSecond, draggedKey = "app:z"),
        )
    }

    // -- folders ---------------------------------------------------------------

    @Test
    fun `a folder cell files the dragged app into that folder`() {
        val registry = CellRegistry()
        registry.registerPageCell(
            pageId = "page-1",
            key = "folder:f1",
            index = 0,
            bounds = cell,
            folderId = "f1",
            folderIndex = 2,
        )

        assertEquals(
            WorkspaceOps.DropTarget.Folder(folderId = "f1", index = 2),
            registry.targetAt(inside, draggedKey = "app:z"),
        )
    }

    @Test
    fun `a folder docked in the dock is a drop target too`() {
        val registry = CellRegistry()
        registry.dockBounds = cell
        registry.registerDockCell("folder:f1", index = 0, bounds = cell)
        registry.registerDockFolderCell(
            key = "folder:f1",
            index = 0,
            bounds = cell,
            folderId = "f1",
            folderIndex = 3,
        )

        // A folder wins over the dock slot it sits in: dropping an app on a
        // docked folder must file it, not push the folder along the row.
        assertEquals(
            WorkspaceOps.DropTarget.Folder(folderId = "f1", index = 3),
            registry.targetAt(inside, draggedKey = "app:z"),
        )
    }

    @Test
    fun `a folder does not swallow the item it is being dragged from`() {
        val registry = CellRegistry()
        registry.registerPageCell(
            pageId = "page-1",
            key = "folder:f1",
            index = 0,
            bounds = cell,
            folderId = "f1",
        )

        // The folder's own key is excluded, so a folder dropped on itself
        // resolves to nothing rather than to a folder nested in itself.
        assertNull(registry.targetAt(inside, draggedKey = "folder:f1"))
    }

    // -- dock ------------------------------------------------------------------

    @Test
    fun `a miss inside the dock plate docks at the end`() {
        val registry = CellRegistry()
        registry.dockBounds = Rect(left = 0f, top = 0f, right = 400f, bottom = 60f)
        registry.registerDockCell("app:a", index = 0, bounds = Rect(0f, 0f, 50f, 60f))
        registry.registerDockCell("app:b", index = 1, bounds = Rect(50f, 0f, 100f, 60f))

        assertEquals(
            WorkspaceOps.DropTarget.Dock(index = 2),
            registry.targetAt(Offset(300f, 30f), draggedKey = "app:z"),
        )
    }

    @Test
    fun `the dock wins over the page it overlaps`() {
        val registry = CellRegistry()
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = Rect(0f, 0f, 400f, 200f))
        registry.dockBounds = Rect(0f, 0f, 400f, 60f)
        registry.registerDockCell("app:b", index = 0, bounds = Rect(0f, 0f, 50f, 60f))

        assertEquals(
            WorkspaceOps.DropTarget.Dock(index = 0),
            registry.targetAt(Offset(25f, 30f), draggedKey = "app:z"),
        )
    }

    // -- nothing ---------------------------------------------------------------

    @Test
    fun `empty space resolves to no target`() {
        val registry = CellRegistry()
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = cell)

        // Releasing over a gap must leave the layout alone rather than guess.
        assertNull(registry.targetAt(Offset(900f, 900f), draggedKey = "app:z"))
    }

    @Test
    fun `clear forgets everything`() {
        val registry = CellRegistry()
        registry.dockBounds = cell
        registry.registerPageCell("page-1", "app:a", index = 0, bounds = cell)
        registry.registerDockCell("app:b", index = 0, bounds = cell)
        registry.clear()

        assertNull(registry.dockBounds)
        assertNull(registry.targetAt(inside, draggedKey = "app:z"))
    }
}
