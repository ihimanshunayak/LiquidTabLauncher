/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — the persisted workspace format, under test.
 *
 * Name      : WorkspaceJsonTest.kt
 * Version   : 1.0.0
 * Purpose   : The workspace is written to disk as JSON and read back by a later
 *             build, so its wire form is a format — not an implementation
 *             detail. These tests pin that format down: the discriminator
 *             strings the sealed hierarchy writes, the names a renamed field
 *             would break, and the reads this build must still survive.
 *
 *             They also guard the R8 story: the model classes are kept by
 *             `proguard-rules.pro` precisely because a renamed data class would
 *             fail to decode a layout the user had already arranged, and that
 *             failure would not show up in any release build.
 */

package com.ihimanshunayak.liquidtab.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceJsonTest {

    /** The same configuration [LauncherStore] uses, so the form matches. */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val a = AppRef("com.example.a", "com.example.a.Main")
    private val b = AppRef("com.example.b")

    private fun sample() = Workspace(
        pages = listOf(
            WorkspacePage(
                id = "page-1",
                items = listOf(
                    WorkspaceItem.App(a),
                    WorkspaceItem.Folder(
                        FolderRef("folder-1", "Games", listOf(a, b)),
                    ),
                    WorkspaceItem.Widget("clock-1", WidgetKind.CLOCK),
                ),
            ),
            WorkspacePage(id = "page-2"),
        ),
        dock = listOf(WorkspaceItem.App(b), WorkspaceItem.Widget("music-1", WidgetKind.NOW_PLAYING)),
        defaultPageId = "page-2",
    )

    @Test
    fun `a workspace survives a round trip unchanged`() {
        val start = sample()
        assertEquals(start, json.decodeFromString<Workspace>(json.encodeToString(start)))
    }

    @Test
    fun `a Home-page nomination survives a round trip`() {
        val start = sample()
        val decoded = json.decodeFromString<Workspace>(json.encodeToString(start))
        assertEquals("page-2", decoded.defaultPageId)
        assertTrue(
            "the nomination field must be written by name",
            json.encodeToString(start).contains("\"defaultPageId\""),
        )
    }

    @Test
    fun `a payload with no Home-page nomination reads as the first page`() {
        // The field arrived after the first released build, so a layout written
        // before it must load as "first page" rather than failing to decode.
        val decoded = json.decodeFromString<Workspace>(
            """{"version":1,"pages":[{"id":"page-1"}],"dock":[]}""",
        )
        assertEquals(null, decoded.defaultPageId)
    }
    @Test
    fun `an empty workspace survives a round trip`() {
        val start = Workspace.empty()
        assertEquals(start, json.decodeFromString<Workspace>(json.encodeToString(start)))
    }

    @Test
    fun `the item discriminator is the stable name, not the class name`() {
        val encoded = json.encodeToString(sample())
        assertTrue("app discriminator missing in $encoded", encoded.contains("\"app\""))
        assertTrue("folder discriminator missing in $encoded", encoded.contains("\"folder\""))
        assertTrue("widget discriminator missing in $encoded", encoded.contains("\"widget\""))
    }

    @Test
    fun `widget kinds are written by enum name`() {
        val encoded = json.encodeToString(sample())
        assertTrue("CLOCK missing in $encoded", encoded.contains("\"CLOCK\""))
        assertTrue("NOW_PLAYING missing in $encoded", encoded.contains("\"NOW_PLAYING\""))
    }

    @Test
    fun `every widget kind has a name the format can carry`() {
        // A kind that cannot be written is a widget that vanishes on relaunch.
        WidgetKind.entries.forEach { kind ->
            val item = WorkspaceItem.Widget("probe", kind)
            val decoded = json.decodeFromString<Workspace>(json.encodeToString(Workspace.empty().copy(
                pages = listOf(WorkspacePage("page-1", listOf(item))),
            )))
            assertEquals(item, decoded.pages.first().items.first())
        }
    }

    @Test
    fun `an unknown field does not stop a stored layout from loading`() {
        // A newer build may add fields. Reading one must degrade to ignoring it
        // rather than discarding the user's layout.
        val payload = """
            {
              "version": 1,
              "pages": [ { "id": "page-1", "items": [], "futureField": 7 } ],
              "dock": [],
              "futureField": "ignored"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<Workspace>(payload)
        assertEquals(listOf("page-1"), decoded.pages.map { it.id })
    }

    @Test
    fun `a payload missing the optional collections still decodes`() {
        // The defaults are what an older build's payload looks like.
        val decoded = json.decodeFromString<Workspace>("""{"version":1}""")
        assertEquals(Workspace.CURRENT_VERSION, decoded.version)
        assertTrue(decoded.pages.isEmpty())
        assertTrue(decoded.dock.isEmpty())
    }

    @Test
    fun `an unknown widget kind fails loudly rather than silently`() {
        // The store catches this and sets the payload aside, which is only
        // correct if the decode really does throw instead of inventing a kind.
        val payload = """
            {
              "version": 1,
              "pages": [ { "id": "page-1", "items": [
                { "type": "widget", "id": "w", "kind": "HOLOGRAM" }
              ] } ],
              "dock": []
            }
        """.trimIndent()
        val threw = try {
            json.decodeFromString<Workspace>(payload)
            false
        } catch (t: Throwable) {
            true
        }
        assertTrue("An unknown widget kind must not decode silently", threw)
    }

    @Test
    fun `a folder's apps survive the round trip in order`() {
        val start = sample()
        val decoded = json.decodeFromString<Workspace>(json.encodeToString(start))
        val folder = decoded.pages.first().items
            .filterIsInstance<WorkspaceItem.Folder>()
            .first()
            .folder
        assertEquals(listOf(a, b), folder.items)
    }
}
