/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — workspace placement rules, under test.
 *
 * Name      : WorkspaceOpsTest.kt
 * Version   : 1.0.0
 * Purpose   : The placement rules are the part of a launcher that is easy to
 *             get subtly wrong and expensive to notice late — an app that
 *             disappears when a folder is dissolved, a dock that keeps a
 *             shortcut a page also shows, a page count that drifts. They are
 *             pure functions over the model precisely so they can be checked
 *             here, without a device.
 */

package com.ihimanshunayak.liquidtab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceOpsTest {

    private val page1 = WorkspacePage(id = "page-1")
    private val page2 = WorkspacePage(id = "page-2")

    private val a = AppRef("com.example.a", "A")
    private val b = AppRef("com.example.b", "B")
    private val c = AppRef("com.example.c", "C")
    private val d = AppRef("com.example.d", "D")

    private fun workspace(
        pages: List<WorkspacePage> = listOf(page1, page2),
        dock: List<WorkspaceItem> = emptyList(),
    ) = Workspace(pages = pages, dock = dock)

    private fun apps(vararg refs: AppRef) = refs.map { WorkspaceItem.App(it) }

    private fun pageItems(workspace: Workspace, pageId: String) =
        workspace.pages.first { it.id == pageId }.items

    private fun refsOf(items: List<WorkspaceItem>) = items.map { (it as WorkspaceItem.App).ref }

    private fun idsOf(workspace: Workspace) = workspace.pages.map { it.id }

    // -- add / remove ----------------------------------------------------------

    @Test
    fun `add appends to the named page`() {
        val result = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        assertEquals(listOf(WorkspaceItem.App(a)), pageItems(result, "page-1"))
        assertTrue(pageItems(result, "page-2").isEmpty())
    }

    @Test
    fun `add to an unknown page lands on the last page`() {
        val result = WorkspaceOps.add(workspace(), "does-not-exist", WorkspaceItem.App(a))
        assertEquals(listOf(WorkspaceItem.App(a)), pageItems(result, "page-2"))
    }

    @Test
    fun `add with a null page lands on the last page`() {
        val result = WorkspaceOps.add(workspace(), null, WorkspaceItem.App(a))
        assertEquals(listOf(WorkspaceItem.App(a)), pageItems(result, "page-2"))
    }

    @Test
    fun `remove takes the item off its page`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.remove(start, "app:${a.key}")
        assertTrue(pageItems(result, "page-1").isEmpty())
    }

    @Test
    fun `remove takes the item out of the dock`() {
        val start = workspace(dock = apps(a))
        val result = WorkspaceOps.remove(start, "app:${a.key}")
        assertTrue(result.dock.isEmpty())
    }

    // -- move ------------------------------------------------------------------

    @Test
    fun `moveToPage inserts at the requested index`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(b)) }
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(c)) }

        val result = WorkspaceOps.moveToPage(start, "app:${c.key}", "page-1", 0)
        assertEquals(listOf(c, a, b), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `moveToPage across pages leaves the source page`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.moveToPage(start, "app:${a.key}", "page-2", 0)
        assertTrue(pageItems(result, "page-1").isEmpty())
        assertEquals(listOf(WorkspaceItem.App(a)), pageItems(result, "page-2"))
    }

    @Test
    fun `moveToDock lifts the item off its page`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.moveToDock(start, "app:${a.key}", 0)
        assertTrue(pageItems(result, "page-1").isEmpty())
        assertEquals(listOf(WorkspaceItem.App(a)), result.dock)
    }

    @Test
    fun `moveToDock refuses a widget`() {
        val widget = WorkspaceItem.Widget("w1", WidgetKind.CLOCK)
        val start = WorkspaceOps.add(workspace(), "page-1", widget)
        val result = WorkspaceOps.moveToDock(start, widget.key, 0)
        assertEquals(listOf(widget), pageItems(result, "page-1"))
        assertTrue(result.dock.isEmpty())
    }

    @Test
    fun `reorder within the dock preserves membership`() {
        val start = workspace(dock = apps(a, b, c))
        val result = WorkspaceOps.reorder(start, "app:${c.key}", WorkspaceOps.IN_DOCK, 0)
        assertEquals(listOf(c, a, b), refsOf(result.dock))
    }

    // -- folders ---------------------------------------------------------------

    @Test
    fun `createFolder removes the member shortcuts from their pages`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(b)) }

        val result = WorkspaceOps.createFolder(start, "page-1", "Two", listOf(a, b))
        val items = pageItems(result, "page-1")
        assertEquals(1, items.size)
        val folder = (items.first() as WorkspaceItem.Folder).folder
        assertEquals(listOf(a, b), folder.items)
        assertEquals("Two", folder.name)
    }

    @Test
    fun `createFolder with no apps changes nothing`() {
        val start = workspace()
        assertEquals(start, WorkspaceOps.createFolder(start, "page-1", "Empty", emptyList()))
    }

    @Test
    fun `removeFromFolder dissolves a folder left with fewer than two apps`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        val folder = WorkspaceOps.folder(
            start,
            (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id,
        )
        assertNotNull(folder)

        val result = WorkspaceOps.removeFromFolder(start, folder!!.id, a)
        // One app left: the folder is gone and b is a normal shortcut again.
        assertTrue(result.pages.all { page -> page.items.none { it is WorkspaceItem.Folder } })
        assertEquals(listOf(b), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `removeFromFolder leaves a folder of three intact`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Three", listOf(a, b, c))
        val folder = WorkspaceOps.folder(
            start,
            (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id,
        )!!

        val result = WorkspaceOps.removeFromFolder(start, folder.id, a)
        assertEquals(listOf(b, c), WorkspaceOps.folder(result, folder.id)?.items)
    }

    @Test
    fun `deleteFolder spills its apps rather than deleting them`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Three", listOf(a, b, c))
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id

        val result = WorkspaceOps.deleteFolder(start, folderId)
        assertEquals(listOf(a, b, c), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `deleteFolder spills into the folder's own slot, not the end`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.createFolder(it, "page-1", "Mid", listOf(b, c)) }
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(d)) }
        val folderId = (pageItems(start, "page-1")[1] as WorkspaceItem.Folder).folder.id

        val result = WorkspaceOps.deleteFolder(start, folderId)
        assertEquals(listOf(a, b, c, d), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `deleteFolder on a docked folder spills into the dock`() {
        val start = workspace(
            dock = listOf(WorkspaceItem.Folder(FolderRef("f-dock", "Docked", listOf(a, b, c)))),
        )
        val result = WorkspaceOps.deleteFolder(start, "f-dock")
        assertEquals(listOf(a, b, c), refsOf(result.dock))
        assertTrue(result.pages.all { page -> page.items.isEmpty() })
    }

    @Test
    fun `folder finds a folder that lives in the dock`() {
        val start = workspace(
            dock = listOf(WorkspaceItem.Folder(FolderRef("f-dock", "Docked", listOf(a, b)))),
        )
        assertEquals("Docked", WorkspaceOps.folder(start, "f-dock")?.name)
    }

    @Test
    fun `removeFromFolder dissolves a docked folder into the dock`() {
        val start = workspace(
            dock = listOf(WorkspaceItem.Folder(FolderRef("f-dock", "Docked", listOf(a, b)))),
        )
        val result = WorkspaceOps.removeFromFolder(start, "f-dock", a)
        assertEquals(listOf(WorkspaceItem.App(b)), result.dock)
    }

    @Test
    fun `renameFolder reaches a folder in the dock`() {
        val start = workspace(
            dock = listOf(WorkspaceItem.Folder(FolderRef("f-dock", "Old", listOf(a, b)))),
        )
        val result = WorkspaceOps.renameFolder(start, "f-dock", "New")
        assertEquals("New", WorkspaceOps.folder(result, "f-dock")?.name)
    }

    @Test
    fun `renameFolder persists a new name`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Old", listOf(a, b))
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id
        val result = WorkspaceOps.renameFolder(start, folderId, "New")
        assertEquals("New", WorkspaceOps.folder(result, folderId)?.name)
    }

    @Test
    fun `renameFolder ignores a blank name`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Kept", listOf(a, b))
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id
        val result = WorkspaceOps.renameFolder(start, folderId, "   ")
        assertEquals("Kept", WorkspaceOps.folder(result, folderId)?.name)
    }

    @Test
    fun `moveIntoFolder lifts the shortcut from its old home`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
            .let { WorkspaceOps.add(it, "page-2", WorkspaceItem.App(c)) }
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id

        val result = WorkspaceOps.moveIntoFolder(start, folderId, c, 0)
        assertTrue(pageItems(result, "page-2").isEmpty())
        assertEquals(listOf(c, a, b), WorkspaceOps.folder(result, folderId)?.items)
    }

    @Test
    fun `moveIntoFolder lifts the shortcut out of the dock`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
            .let { it.copy(dock = apps(c)) }
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id

        val result = WorkspaceOps.moveIntoFolder(start, folderId, c, 0)
        assertTrue(result.dock.isEmpty())
        assertEquals(listOf(c, a, b), WorkspaceOps.folder(result, folderId)?.items)
    }

    // -- prune -----------------------------------------------------------------

    @Test
    fun `pruneMissing drops shortcuts whose app is gone`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(b)) }

        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        assertEquals(listOf(a), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `pruneMissing drops a folder that loses every member`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertTrue(result.pages.all { page -> page.items.isEmpty() })
    }

    @Test
    fun `pruneMissing keeps a folder that still has members`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        val folder = (pageItems(result, "page-1").first() as WorkspaceItem.Folder).folder
        assertEquals(listOf(a), folder.items)
    }

    @Test
    fun `pruneMissing drops dock shortcuts whose app is gone`() {
        val start = workspace(dock = apps(a, b))
        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        assertEquals(listOf(WorkspaceItem.App(a)), result.dock)
    }

    @Test
    fun `pruneMissing never removes the last page`() {
        val start = workspace(pages = listOf(page1))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertEquals(1, result.pages.size)
    }

    @Test
    fun `pruneMissing keeps a page the user left blank`() {
        // A blank page is a canvas, not a page this pass emptied — the two are
        // indistinguishable by content and completely different to the user.
        val start = WorkspaceOps.add(workspace(), "page-2", WorkspaceItem.App(a))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertEquals(listOf("page-1", "page-2"), result.pages.map { it.id })
    }

    @Test
    fun `pruneMissing drops an emptied page that is not the last`() {
        val three = WorkspaceOps.addPage(workspace())
        val start = WorkspaceOps.add(three, "page-2", WorkspaceItem.App(a))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertEquals(listOf("page-1", three.pages.last().id), result.pages.map { it.id })
    }

    @Test
    fun `pruneMissing leaves an already-correct workspace untouched`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        assertEquals(start, result)
    }

    @Test
    fun `pruneMissing keeps an empty trailing page while dropping an earlier one`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertTrue(result.pages.size >= 1)
        assertEquals("page-2", result.pages.first().id)
    }

    @Test
    fun `pruneMissing drops a page it emptied even when the canvas rule would keep it`() {
        // A page that arrived empty and a page this pass emptied both end up
        // empty; only the second is a correction. The distinction is the whole
        // reason the pass compares against the incoming workspace.
        val three = WorkspaceOps.addPage(workspace())
        val start = WorkspaceOps.add(three, "page-2", WorkspaceItem.App(a))
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertFalse(result.pages.any { it.id == "page-2" })
        assertTrue(result.pages.any { it.id == "page-1" })
    }

    @Test
    fun `pruneMissing trims a folder rather than deleting it`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id
        val result = WorkspaceOps.pruneMissing(start, setOf(b.key))
        assertEquals(listOf(b), WorkspaceOps.folder(result, folderId)?.items)
        assertEquals(WorkspaceItem.Folder(WorkspaceOps.folder(result, folderId)!!), pageItems(result, "page-1").first())
    }

    @Test
    fun `pruneMissing never touches a widget`() {
        val widget = WorkspaceItem.Widget("clock-1", WidgetKind.CLOCK)
        val start = WorkspaceOps.add(workspace(), "page-1", widget)
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertTrue(pageItems(result, "page-1").contains(widget))
    }

    @Test
    fun `pruneMissing prunes a folder inside the dock`() {
        val start = workspace(
            dock = listOf(WorkspaceItem.Folder(FolderRef("f-dock", "Docked", listOf(a, b)))),
        )
        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        val folder = (result.dock.first() as WorkspaceItem.Folder).folder
        assertEquals(listOf(a), folder.items)
    }

    // -- lookups ---------------------------------------------------------------

    @Test
    fun `contains finds an app inside a folder`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        assertTrue(WorkspaceOps.contains(start, a))
        assertFalse(WorkspaceOps.contains(start, c))
    }

    @Test
    fun `contains finds an app in the dock`() {
        assertTrue(WorkspaceOps.contains(workspace(dock = apps(d)), d))
    }

    @Test
    fun `pageOf returns null for a dock item`() {
        val start = workspace(dock = apps(a))
        assertNull(WorkspaceOps.pageOf(start, "app:${a.key}"))
    }

    @Test
    fun `locationOf names the page that holds an item`() {
        val start = WorkspaceOps.add(workspace(), "page-2", WorkspaceItem.App(a))
        assertEquals("page-2", WorkspaceOps.locationOf(start, "app:${a.key}"))
    }

    // -- drops -----------------------------------------------------------------

    @Test
    fun `applyDrop with no target changes nothing`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        assertEquals(start, WorkspaceOps.applyDrop(start, RecordingDrag("app:${a.key}", null)))
    }

    @Test
    fun `applyDrop onto a page slot moves the app there`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(b)) }

        val result = WorkspaceOps.applyDrop(
            start,
            RecordingDrag("app:${b.key}", WorkspaceOps.DropTarget.Page("page-1", 0)),
        )
        assertEquals(listOf(b, a), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `applyDrop onto the dock docks the app`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
        val result = WorkspaceOps.applyDrop(
            start,
            RecordingDrag("app:${a.key}", WorkspaceOps.DropTarget.Dock(0)),
        )
        assertTrue(pageItems(result, "page-1").isEmpty())
        assertEquals(listOf(WorkspaceItem.App(a)), result.dock)
    }

    @Test
    fun `applyDrop into a folder moves the app in`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
            .let { WorkspaceOps.add(it, "page-2", WorkspaceItem.App(c)) }
        val folderId = (pageItems(start, "page-1").first() as WorkspaceItem.Folder).folder.id

        val result = WorkspaceOps.applyDrop(
            start,
            RecordingDrag("app:${c.key}", WorkspaceOps.DropTarget.Folder(folderId, 0)),
        )
        assertEquals(listOf(c, a, b), WorkspaceOps.folder(result, folderId)?.items)
    }

    @Test
    fun `applyDrop of a folder onto a folder is a no-op`() {
        val start = WorkspaceOps.createFolder(workspace(), "page-1", "Two", listOf(a, b))
        val folderItem = pageItems(start, "page-1").first() as WorkspaceItem.Folder
        val result = WorkspaceOps.applyDrop(
            start,
            RecordingDrag(folderItem.key, WorkspaceOps.DropTarget.Folder(folderItem.folder.id, 0)),
        )
        assertEquals(start, result)
    }

    // -- first run -------------------------------------------------------------

    @Test
    fun `firstRun seeds the dock only with installed apps`() {
        val entries = listOf(
            AppEntry("com.android.chrome", "Chrome", "Chrome"),
            AppEntry("com.other.app", "Other", "Other"),
        )
        val result = WorkspaceOps.firstRun(entries, dockMax = 6)
        assertEquals(listOf(AppRef("com.android.chrome", "Chrome")), refsOf(result.dock))
    }

    @Test
    fun `firstRun never repeats a dock app on a page`() {
        val entries = listOf(
            AppEntry("com.android.chrome", "Chrome", "Chrome"),
            AppEntry("com.other.app", "Other", "Other"),
        )
        val result = WorkspaceOps.firstRun(entries, dockMax = 6)
        val docked = result.dock.mapTo(HashSet()) { (it as WorkspaceItem.App).ref.key }
        val onPages = result.pages.flatMap { it.items }.map { (it as WorkspaceItem.App).ref.key }
        assertTrue(docked.intersect(onPages.toSet()).isEmpty())
    }

    @Test
    fun `firstRun respects the dock limit`() {
        val entries = WorkspaceOps.DEFAULT_DOCK_PACKAGES.map { AppEntry(it, it, it) }
        val result = WorkspaceOps.firstRun(entries, dockMax = 2)
        assertEquals(2, result.dock.size)
    }

    @Test
    fun `firstRun always produces exactly one page`() {
        val result = WorkspaceOps.firstRun(emptyList(), dockMax = 6)
        assertEquals(1, result.pages.size)
        assertTrue(result.pages.first().items.isEmpty())
        assertTrue(result.dock.isEmpty())
    }

    // -- pages -----------------------------------------------------------------

    @Test
    fun `addPage with no anchor appends an empty page`() {
        val start = workspace()
        val result = WorkspaceOps.addPage(start)
        assertEquals(3, result.pages.size)
        assertTrue(result.pages.last().items.isEmpty())
        assertEquals(listOf("page-1", "page-2"), result.pages.dropLast(1).map { it.id })
    }

    @Test
    fun `addPage inserts directly after the named page`() {
        val result = WorkspaceOps.addPage(workspace(), afterPageId = "page-1")
        assertEquals(3, result.pages.size)
        assertEquals("page-1", result.pages[0].id)
        assertEquals("page-2", result.pages[2].id)
        assertTrue(result.pages[1].items.isEmpty())
    }

    @Test
    fun `addPage with an unknown anchor appends rather than guessing`() {
        val result = WorkspaceOps.addPage(workspace(), afterPageId = "does-not-exist")
        assertEquals(3, result.pages.size)
        assertEquals("page-2", result.pages[1].id)
    }

    @Test
    fun `addPage produces an id that is not already in use`() {
        val first = WorkspaceOps.addPage(workspace())
        val second = WorkspaceOps.addPage(first)
        val ids = second.pages.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `removePage spills its items onto the page before it`() {
        val start = WorkspaceOps.add(workspace(), "page-2", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-2", WorkspaceItem.App(b)) }
            .let { WorkspaceOps.add(it, "page-1", WorkspaceItem.App(c)) }
        val result = WorkspaceOps.removePage(start, "page-2")
        assertEquals(listOf("page-1"), result.pages.map { it.id })
        assertEquals(listOf(c, a, b), refsOf(pageItems(result, "page-1")))
    }

    @Test
    fun `removePage of the first page spills onto the page after it`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.add(it, "page-2", WorkspaceItem.App(b)) }
        val result = WorkspaceOps.removePage(start, "page-1")
        assertEquals(listOf("page-2"), result.pages.map { it.id })
        assertEquals(listOf(a, b), refsOf(pageItems(result, "page-2")))
    }

    @Test
    fun `removePage keeps the last page however empty`() {
        val start = workspace(pages = listOf(page1))
        val result = WorkspaceOps.removePage(start, "page-1")
        assertEquals(start, result)
    }

    @Test
    fun `removePage of an unknown id changes nothing`() {
        val start = workspace()
        val result = WorkspaceOps.removePage(start, "does-not-exist")
        assertEquals(start, result)
    }

    @Test
    fun `removePage clears a Home-page nomination that pointed at it`() {
        val start = WorkspaceOps.setDefaultPage(workspace(), "page-2")
        val result = WorkspaceOps.removePage(start, "page-2")
        assertNull(result.defaultPageId)
    }

    @Test
    fun `removePage leaves a Home-page nomination that survives`() {
        val start = WorkspaceOps.setDefaultPage(workspace(), "page-1")
        val result = WorkspaceOps.removePage(start, "page-2")
        assertEquals("page-1", result.defaultPageId)
    }

    @Test
    fun `movePage reorders in both directions`() {
        val three = WorkspaceOps.addPage(workspace())
        val ids = idsOf(three)
        val forward = WorkspaceOps.movePage(three, ids[0], 2)
        assertEquals(listOf(ids[1], ids[2], ids[0]), idsOf(forward))
        val back = WorkspaceOps.movePage(forward, ids[0], 0)
        assertEquals(ids, idsOf(back))
    }

    @Test
    fun `movePage clamps a destination past the end`() {
        val result = WorkspaceOps.movePage(workspace(), "page-1", 99)
        assertEquals(listOf("page-2", "page-1"), idsOf(result))
    }

    @Test
    fun `movePage to the same index is a no-op`() {
        val start = workspace()
        assertEquals(start, WorkspaceOps.movePage(start, "page-1", 0))
    }

    @Test
    fun `movePage of an unknown id changes nothing`() {
        val start = workspace()
        assertEquals(start, WorkspaceOps.movePage(start, "does-not-exist", 0))
    }

    @Test
    fun `movePage keeps the Home-page nomination with its page`() {
        // The nomination is an id, so reordering pages cannot move Home to a
        // page the user never chose.
        val three = WorkspaceOps.addPage(workspace())
        val newPageId = idsOf(three).last()
        val nominated = WorkspaceOps.setDefaultPage(three, newPageId)
        val moved = WorkspaceOps.movePage(nominated, newPageId, 0)
        assertEquals(newPageId, idsOf(moved).first())
        assertEquals(newPageId, moved.defaultPageId)
    }

    @Test
    fun `setDefaultPage accepts a page that exists`() {
        assertEquals("page-2", WorkspaceOps.setDefaultPage(workspace(), "page-2").defaultPageId)
    }

    @Test
    fun `setDefaultPage clears on an unknown id rather than storing it`() {
        val nominated = WorkspaceOps.setDefaultPage(workspace(), "page-1")
        assertNull(WorkspaceOps.setDefaultPage(nominated, "does-not-exist").defaultPageId)
    }

    @Test
    fun `setDefaultPage accepts null as the first-page default`() {
        val nominated = WorkspaceOps.setDefaultPage(workspace(), "page-2")
        assertNull(WorkspaceOps.setDefaultPage(nominated, null).defaultPageId)
    }

    @Test
    fun `pruneMissing keeps a page the user created and has not filled yet`() {
        val start = WorkspaceOps.add(workspace(), "page-3", WorkspaceItem.App(a))
            .let { WorkspaceOps.addPage(it, afterPageId = null) }
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        // The page that held `a` is emptied and goes; the untouched new page is
        // a canvas the user still has, so it stays.
        assertEquals(2, result.pages.size)
    }

    @Test
    fun `pruneMissing drops a Home-page nomination left dangling by a removal`() {
        // A nomination is only ever stored for a page that exists, so the only
        // way it can dangle is this pass removing its page: the middle page is
        // dropped because pruning emptied it, and Home must fall back rather
        // than keep pointing at a page that is gone.
        val three = WorkspaceOps.addPage(workspace())
        val middle = idsOf(three)[1]
        val start = WorkspaceOps.add(three, middle, WorkspaceItem.App(a))
            .let { WorkspaceOps.setDefaultPage(it, middle) }
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertFalse(result.pages.any { it.id == middle })
        assertNull(result.defaultPageId)
    }

    @Test
    fun `pruneMissing keeps the last page and so its nomination too`() {
        // The last page is never removed by a prune, so a nomination on it
        // cannot dangle - the two rules have to agree or Home moves by itself.
        val start = WorkspaceOps.add(workspace(), "page-2", WorkspaceItem.App(a))
            .let { WorkspaceOps.setDefaultPage(it, "page-2") }
        val result = WorkspaceOps.pruneMissing(start, emptySet())
        assertEquals("page-2", result.defaultPageId)
    }

    @Test
    fun `pruneMissing keeps a Home-page nomination that still resolves`() {
        val start = WorkspaceOps.add(workspace(), "page-1", WorkspaceItem.App(a))
            .let { WorkspaceOps.setDefaultPage(it, "page-1") }
        val result = WorkspaceOps.pruneMissing(start, setOf(a.key))
        assertEquals("page-1", result.defaultPageId)
        assertEquals(start, result)
    }

    // -- model -----------------------------------------------------------------

    @Test
    fun `app keys are stable whether or not a class is recorded`() {
        assertEquals("com.example.a", AppRef("com.example.a").key)
        assertEquals("com.example.a/A", AppRef("com.example.a", "A").key)
    }

    @Test
    fun `item keys never collide across kinds`() {
        val app = WorkspaceItem.App(a)
        val folder = WorkspaceItem.Folder(FolderRef("x", "X", listOf(a)))
        val widget = WorkspaceItem.Widget("x", WidgetKind.CLOCK)
        val keys = setOf(app.key, folder.key, widget.key)
        assertEquals(3, keys.size)
    }

    /** A drag record built by hand, so the rules can be tested without a finger. */
    private class RecordingDrag(
        override val itemKey: String,
        override val target: WorkspaceOps.DropTarget?,
    ) : WorkspaceOps.DragTarget
}
