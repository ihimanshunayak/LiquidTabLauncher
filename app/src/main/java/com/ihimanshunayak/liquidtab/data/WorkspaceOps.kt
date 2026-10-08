/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — workspace mutations.
 *
 * Name      : WorkspaceOps.kt
 * Version   : 1.0.0
 * Purpose   : Every edit the workspace supports — add, move, remove, folder
 *             create/rename/delete — expressed as pure functions over
 *             [Workspace]. Drag-and-drop, the picker, search and settings all
 *             call these, and persistence never sees an invalid workspace
 *             because an edit either returns a new valid one or null (no-op).
 *
 * Notes     : No Android types, no Compose types, no I/O. This is the layer the
 *             unit tests exercise.
 */

package com.ihimanshunayak.liquidtab.data

object WorkspaceOps {

    // ── Lookups ───────────────────────────────────────────────────────────────

    /** The page holding [itemKey], or null when the key is in the dock. */
    fun pageOf(workspace: Workspace, itemKey: String): WorkspacePage? =
        workspace.pages.firstOrNull { page -> page.items.any { it.key == itemKey } }

    /** Finds an item anywhere — pages first, then the dock. */
    fun item(workspace: Workspace, itemKey: String): WorkspaceItem? =
        workspace.pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.key == itemKey } }
            ?: workspace.dock.firstOrNull { it.key == itemKey }

    /**
     * Finds a folder by the folder's own id (not the item key). Folders can be
     * docked like apps, so the dock is searched after the pages.
     */
    fun folder(workspace: Workspace, folderId: String): FolderRef? =
        workspace.pages.firstNotNullOfOrNull { page ->
            page.items.filterIsInstance<WorkspaceItem.Folder>()
                .firstOrNull { it.folder.id == folderId }
                ?.folder
        } ?: workspace.dock.filterIsInstance<WorkspaceItem.Folder>()
            .firstOrNull { it.folder.id == folderId }
            ?.folder

    /** Where [itemKey] currently lives: a page id, or null for the dock. */
    fun locationOf(workspace: Workspace, itemKey: String): String? =
        pageOf(workspace, itemKey)?.id

    /** True when [ref] appears anywhere — page, folder or dock. */
    fun contains(workspace: Workspace, ref: AppRef): Boolean =
        workspace.pages.any { page ->
            page.items.any { item ->
                when (item) {
                    is WorkspaceItem.App -> item.ref.key == ref.key
                    is WorkspaceItem.Folder -> item.folder.items.any { it.key == ref.key }
                    is WorkspaceItem.Widget -> false
                }
            }
        } || workspace.dock.any { item ->
            when (item) {
                is WorkspaceItem.App -> item.ref.key == ref.key
                is WorkspaceItem.Folder -> item.folder.items.any { it.key == ref.key }
                is WorkspaceItem.Widget -> false
            }
        }

    // ── Add / remove ──────────────────────────────────────────────────────────

    /**
     * Adds [item] to the end of page [pageId] (or the last page when the id is
     * unknown). Returns the workspace untouched when the item is already on the
     * Home screen — apps are never listed twice.
     */
    fun add(workspace: Workspace, pageId: String?, item: WorkspaceItem): Workspace {
        val targetsLastPage = pageId == null || workspace.pages.none { it.id == pageId }
        val index = if (targetsLastPage) workspace.pages.lastIndex else workspace.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return workspace
        val page = workspace.pages[index]
        return workspace.copy(
            pages = workspace.pages.toMutableList().also { pages ->
                pages[index] = page.copy(items = page.items + item)
            },
        )
    }

    /** Removes [itemKey] from wherever it is; folders are removed wholesale. */
    fun remove(workspace: Workspace, itemKey: String): Workspace {
        val pages = workspace.pages.map { page ->
            page.copy(items = page.items.filterNot { it.key == itemKey })
        }
        val dock = workspace.dock.filterNot { it.key == itemKey }
        return workspace.copy(pages = pages, dock = dock)
    }

    /** Removes an app from a folder, dissolving the folder below two apps. */
    fun removeFromFolder(workspace: Workspace, folderId: String, ref: AppRef): Workspace =
        updateFolder(workspace, folderId) { folder ->
            folder.copy(items = folder.items.filterNot { it.key == ref.key })
        }

    // ── Move ──────────────────────────────────────────────────────────────────

    /**
     * Moves [itemKey] to sit at [index] of page [toPageId]. The item leaves its
     * old position first, so the index is understood against the destination as
     * it looks after the lift — the same mental model a finger drag has.
     */
    fun moveToPage(workspace: Workspace, itemKey: String, toPageId: String, index: Int): Workspace {
        val moving = item(workspace, itemKey) ?: return workspace
        val lifted = remove(workspace, itemKey)
        val targetIndex = lifted.pages.indexOfFirst { it.id == toPageId }
        if (targetIndex < 0) return workspace
        val page = lifted.pages[targetIndex]
        val at = index.coerceIn(0, page.items.size)
        return lifted.copy(
            pages = lifted.pages.toMutableList().also { pages ->
                pages[targetIndex] = page.copy(items = page.items.toMutableList().also { it.add(at, moving) })
            },
        )
    }

    /** Moves [itemKey] into the dock at [index]. */
    fun moveToDock(workspace: Workspace, itemKey: String, index: Int): Workspace {
        val moving = item(workspace, itemKey) ?: return workspace
        // The dock holds apps and folders of apps; a widget strip has no place
        // in a one-row dock.
        if (moving is WorkspaceItem.Widget) return workspace
        val lifted = remove(workspace, itemKey)
        val at = index.coerceIn(0, lifted.dock.size)
        return lifted.copy(dock = lifted.dock.toMutableList().also { it.add(at, moving) })
    }

    /**
     * Moves [ref] into folder [folderId] at [index]. The app's shortcut leaves
     * wherever it was first, so a drop from the dock or another page behaves
     * identically to a drop from the same page.
     */
    fun moveIntoFolder(workspace: Workspace, folderId: String, ref: AppRef, index: Int): Workspace {
        val withoutShortcut = remove(workspace, WorkspaceItem.App(ref).key)
        return updateFolder(withoutShortcut, folderId) { folder ->
            val at = index.coerceIn(0, folder.items.size)
            if (folder.items.any { it.key == ref.key }) return@updateFolder folder
            folder.copy(items = folder.items.toMutableList().also { it.add(at, ref) })
        }
    }

    /**
     * Reorders an item within its own container. When [itemKey] is on a page,
     * [targetPageId] is that page; when it is in the dock, pass [IN_DOCK].
     */
    fun reorder(workspace: Workspace, itemKey: String, targetContainer: String, index: Int): Workspace {
        val source = item(workspace, itemKey) ?: return workspace
        return if (targetContainer == IN_DOCK) {
            // A reorder inside the dock must preserve dock membership; lift the
            // item out of its slot, then insert it at the new one.
            val lifted = workspace.copy(dock = workspace.dock.filterNot { it.key == itemKey })
            val at = index.coerceIn(0, lifted.dock.size)
            lifted.copy(dock = lifted.dock.toMutableList().also { it.add(at, source) })
        } else {
            moveToPage(workspace, itemKey, targetContainer, index)
        }
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    /**
     * Creates a folder holding [refs] on page [pageId], replacing any of those
     * shortcuts elsewhere on the Home screen so an app is never both in a
     * folder and loose on a page.
     */
    fun createFolder(workspace: Workspace, pageId: String, name: String?, refs: List<AppRef>): Workspace {
        if (refs.isEmpty()) return workspace
        val id = "folder-${System.currentTimeMillis()}-${(0..0xFFFF).random()}"
        val folder = FolderRef(
            id = id,
            name = name ?: defaultFolderName(refs.size),
            items = refs.distinctBy { it.key },
        )
        val withoutShortcuts = refs.fold(workspace) { acc, ref -> remove(acc, WorkspaceItem.App(ref).key) }
        val item = WorkspaceItem.Folder(folder)
        return add(withoutShortcuts, pageId, item)
    }

    /** Renames a folder; the name persists even for a single-app folder. */
    fun renameFolder(workspace: Workspace, folderId: String, name: String): Workspace =
        updateFolder(workspace, folderId) { it.copy(name = name.trim().ifEmpty { it.name }) }

    /**
     * Deletes a folder. Its apps are spilled back into the slot the folder
     * occupied — on its page, or in the dock when it was docked — in order,
     * rather than being silently lost.
     */
    fun deleteFolder(workspace: Workspace, folderId: String): Workspace {
        val folderItemKey = "folder:$folderId"
        val folder = folder(workspace, folderId) ?: return workspace
        val spilled = folder.items.map { WorkspaceItem.App(it) }
        val page = pageOf(workspace, folderItemKey)
        if (page == null) {
            val at = workspace.dock.indexOfFirst { it.key == folderItemKey }
            if (at < 0) return workspace
            return workspace.copy(
                dock = workspace.dock.toMutableList().also { dock ->
                    dock.removeAt(at)
                    dock.addAll(at, spilled)
                },
            )
        }
        val without = remove(workspace, folderItemKey)
        val targetIndex = without.pages.indexOfFirst { it.id == page.id }
        if (targetIndex < 0) return without
        val pageNow = without.pages[targetIndex]
        // The folder's own slot, clamped: the page is one item shorter after the
        // lift, so a folder that sat last lands at the end again.
        val insertAt = page.items.indexOfFirst { it.key == folderItemKey }.coerceIn(0, pageNow.items.size)
        return without.copy(
            pages = without.pages.toMutableList().also { pages ->
                pages[targetIndex] = pageNow.copy(
                    items = pageNow.items.toMutableList().also { items ->
                        items.addAll(insertAt, spilled)
                    },
                )
            },
        )
    }

    /**
     * Applies [transform] to a folder and dissolves it when it holds fewer than
     * two apps — a one-app folder is an app with extra steps.
     */
    private fun updateFolder(workspace: Workspace, folderId: String, transform: (FolderRef) -> FolderRef): Workspace {
        val folder = folder(workspace, folderId) ?: return workspace
        val updated = transform(folder)
        // Swap the folder in first: a dissolve below must spill the folder as it
        // now stands, not as it stood before the edit.
        val replaced = workspace.copy(
            pages = workspace.pages.map { page ->
                page.copy(items = page.items.map { item ->
                    if (item is WorkspaceItem.Folder && item.folder.id == folderId) {
                        WorkspaceItem.Folder(updated)
                    } else {
                        item
                    }
                })
            },
            dock = workspace.dock.map { item ->
                if (item is WorkspaceItem.Folder && item.folder.id == folderId) {
                    WorkspaceItem.Folder(updated)
                } else {
                    item
                }
            },
        )
        return if (updated.items.size < 2) deleteFolder(replaced, folderId) else replaced
    }

    /** The conventional name for a new folder, derived from what it holds. */
    private fun defaultFolderName(size: Int): String = "Folder ($size)"

    /** A usable folder name from a user-typed string. */
    fun sanitizeFolderName(name: String): String = name.trim().take(24)

    // ── Pages ─────────────────────────────────────────────────────────────────

    /** The id for a page that has never existed in this workspace. */
    private fun newPageId(): String = "page-${System.currentTimeMillis()}-${(0..0xFFFF).random()}"

    /**
     * Adds an empty page directly after [afterPageId], or at the end when the id
     * is null or unknown.
     *
     * The page is left empty on purpose. A page is a canvas: seeding one would
     * be guessing at what the user is about to arrange, and every guess would
     * have to be undone by hand.
     */
    fun addPage(workspace: Workspace, afterPageId: String? = null): Workspace {
        val found = workspace.pages.indexOfFirst { it.id == afterPageId }
        val index = if (found < 0) workspace.pages.size else found + 1
        return workspace.copy(
            pages = workspace.pages.toMutableList().also { pages ->
                pages.add(index, WorkspacePage(id = newPageId()))
            },
        )
    }

    /**
     * Removes page [pageId], spilling what it held onto its neighbour.
     *
     * Deleting a page is a statement about the page, not about the shortcuts on
     * it, so nothing is destroyed: the items move to the page before it — or to
     * the page after it when the first page is the one going — in their existing
     * order. The last page is always kept, because Home must always have
     * somewhere to put an icon.
     *
     * The spill keeps the items in reading order. Removing the first page puts
     * its items *before* the survivor's, because that is where the page they
     * came from was; appending them instead would silently reorder the Home
     * screen of every user who rearranged the page that went away.
     *
     * A Home-page nomination that pointed at the removed page is cleared, which
     * reads back as "the first page is Home" rather than as a dangling id.
     */
    fun removePage(workspace: Workspace, pageId: String): Workspace {
        if (workspace.pages.size <= 1) return workspace
        val index = workspace.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return workspace
        val removed = workspace.pages[index]
        val survivorId = workspace.pages.getOrNull(index - 1)?.id ?: workspace.pages[index + 1].id
        val pages = workspace.pages
            .filterNot { it.id == pageId }
            .map { page ->
                when {
                    page.id != survivorId -> page
                    index == 0 -> page.copy(items = removed.items + page.items)
                    else -> page.copy(items = page.items + removed.items)
                }
            }
        return workspace.copy(
            pages = pages,
            defaultPageId = workspace.defaultPageId?.takeIf { it != pageId },
        )
    }

    /** Moves page [pageId] to position [toIndex], clamped to the pages there are. */
    fun movePage(workspace: Workspace, pageId: String, toIndex: Int): Workspace {
        val from = workspace.pages.indexOfFirst { it.id == pageId }
        if (from < 0) return workspace
        val to = toIndex.coerceIn(0, workspace.pages.lastIndex)
        if (to == from) return workspace
        val pages = workspace.pages.toMutableList()
        pages.add(to, pages.removeAt(from))
        return workspace.copy(pages = pages)
    }

    /**
     * Nominates the page a Home press returns to. An id that is not in the
     * workspace — or null — clears the nomination, so the launcher never stores
     * a choice it could not honour.
     */
    fun setDefaultPage(workspace: Workspace, pageId: String?): Workspace =
        workspace.copy(defaultPageId = pageId?.takeIf { id -> workspace.pages.any { it.id == id } })

    /**
     * Applies a finished drag. The single entry point for "the finger lifted",
     * so the placement rules and the no-op cases live here rather than spread
     * across the gesture callbacks.
     *
     * A drop with no target leaves the layout untouched: releasing over empty
     * space is a user changing their mind, not an instruction.
     */
    fun applyDrop(workspace: Workspace, session: DragTarget): Workspace {
        val target = session.target ?: return workspace
        return when (target) {
            is DropTarget.Page -> moveToPage(workspace, session.itemKey, target.pageId, target.index)
            is DropTarget.Dock -> moveToDock(workspace, session.itemKey, target.index)
            is DropTarget.Folder -> {
                val ref = (item(workspace, session.itemKey) as? WorkspaceItem.App)?.ref
                // Dropping a folder onto a folder would nest them, which the
                // model deliberately does not support.
                if (ref == null) workspace else moveIntoFolder(workspace, target.folderId, ref, target.index)
            }
        }
    }

    /**
     * Drops every shortcut whose app is no longer installed, plus any page this
     * prune emptied. Only ever removes: a launcher correcting itself must not
     * also decide to add anything.
     *
     * The last page is always kept, so Home is never left with nowhere to put
     * an icon — an empty trailing page is a canvas the user still has.
     */
    fun pruneMissing(workspace: Workspace, installedKeys: Set<String>): Workspace {
        /** The corrected form of [item], or null when nothing of it is left. */
        fun prune(item: WorkspaceItem): WorkspaceItem? = when (item) {
            is WorkspaceItem.App -> item.takeIf { it.ref.key in installedKeys }
            is WorkspaceItem.Widget -> item
            is WorkspaceItem.Folder -> item.folder.items
                .filter { it.key in installedKeys }
                .takeIf { it.isNotEmpty() }
                ?.let { members -> WorkspaceItem.Folder(item.folder.copy(items = members)) }
        }

        val pages = workspace.pages.map { page -> page.copy(items = page.items.mapNotNull(::prune)) }
        val keptPages = pages.filterIndexed { index, page ->
            // A page the user created and has not filled yet is a canvas, not a
            // page this pass emptied — only the latter is removed.
            val wasEmpty = workspace.pages[index].items.isEmpty()
            page.items.isNotEmpty() || wasEmpty || index == pages.lastIndex
        }
        val dock = workspace.dock.mapNotNull(::prune)
        val keptIds = keptPages.mapTo(HashSet()) { it.id }
        val defaultPageId = workspace.defaultPageId?.takeIf { it in keptIds }

        return if (keptPages == workspace.pages && dock == workspace.dock &&
            defaultPageId == workspace.defaultPageId
        ) {
            workspace
        } else {
            workspace.copy(pages = keptPages, dock = dock, defaultPageId = defaultPageId)
        }
    }

    /**
     * The minimum a finished drag needs to be applied. Declared here rather
     * than taking the UI's own drag type so the data layer does not depend on
     * Compose.
     */
    interface DragTarget {
        val itemKey: String
        val target: DropTarget?
    }

    /** Where a finished drag would place an item. */
    sealed interface DropTarget {
        data class Page(val pageId: String, val index: Int) : DropTarget
        data class Dock(val index: Int) : DropTarget
        data class Folder(val folderId: String, val index: Int) : DropTarget
    }

    /** The sentinel container id for the dock's single row. */
    const val IN_DOCK = "__dock__"

    /**
     * The default dock: Music first, because this is a FreeMusic launcher, then
     * the browsing- and capture-tools every tablet tends to carry.
     */
    val DEFAULT_DOCK_PACKAGES = listOf(
        "com.ihimanshunayak.freemusic",
        "com.android.chrome",
        "com.google.android.apps.photos",
        "com.android.camera2",
        "com.google.android.apps.docs",
        "com.android.vending",
    )

    /**
     * A first-run workspace: the default dock if those apps exist, and the first
     * page seeded with the first [SEED_APPS] launchable apps so the Home screen
     * is never empty on a fresh install.
     */
    fun firstRun(apps: List<AppEntry>, dockMax: Int): Workspace {
        val byPackage = apps.associateBy { it.packageName }
        val dockRefs = DEFAULT_DOCK_PACKAGES.mapNotNull { byPackage[it]?.ref }.take(dockMax)
        val dockKeys = dockRefs.map { it.key }.toSet()
        val homeRefs = apps.asSequence()
            .filter { it.ref.key !in dockKeys }
            .take(SEED_APPS)
            .map { it.ref }
            .toList()
        return Workspace(
            pages = listOf(WorkspacePage(id = "page-1", items = homeRefs.map { WorkspaceItem.App(it) })),
            dock = dockRefs.map { WorkspaceItem.App(it) },
        )
    }

    private const val SEED_APPS = 12
}
