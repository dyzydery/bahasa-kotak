package io.github.dyzydery.bahasakotak.io.pack

import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Register
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImportPlannerTest {
    private val nothingInstalled = InstalledContent(null, emptySet(), emptySet(), emptySet(), emptySet())

    @Test
    fun `fresh pack is all new`() {
        val plan = apply(pack(version = 1, "t.a", "t.b"), nothingInstalled)
        assertFalse(plan.isUpgrade)
        assertEquals(setOf("t.a", "t.b"), plan.newItemIds)
        assertTrue(plan.updatedItemIds.isEmpty())
        assertTrue(plan.retiredItemIds.isEmpty())
    }

    @Test
    fun `upgrade splits items into new, updated and retired`() {
        val installed = installed(version = 1, items = setOf("t.a", "t.b"))
        val plan = apply(pack(version = 2, "t.b", "t.c"), installed)
        assertTrue(plan.isUpgrade)
        assertEquals(setOf("t.c"), plan.newItemIds)
        assertEquals(setOf("t.b"), plan.updatedItemIds)
        assertEquals(setOf("t.a"), plan.retiredItemIds)
    }

    @Test
    fun `removed lessons and topics are retired`() {
        val installed = installed(
            version = 1, items = setOf("t.a"),
            lessons = setOf("t.l1", "t.old"), topics = setOf("t", "gone"),
        )
        val plan = apply(pack(version = 2, "t.a"), installed)
        assertEquals(setOf("t.old"), plan.retiredLessonIds)
        assertEquals(setOf("gone"), plan.retiredTopicIds)
    }

    @Test
    fun `previously retired item that reappears is an update, not new`() {
        // Retired items are not in packItemIds (active only) but still exist in the database.
        val installed = installed(version = 2, items = setOf("t.a"), allItems = setOf("t.a", "t.back"))
        val plan = apply(pack(version = 3, "t.a", "t.back"), installed)
        assertEquals(setOf("t.a", "t.back"), plan.updatedItemIds)
        assertTrue(plan.newItemIds.isEmpty())
        assertTrue(plan.retiredItemIds.isEmpty())
    }

    @Test
    fun `item owned by another pack counts as updated`() {
        val plan = apply(pack(version = 1, "shared.x"), nothingInstalled.copy(allItemIds = setOf("shared.x")))
        assertEquals(setOf("shared.x"), plan.updatedItemIds)
        assertTrue(plan.newItemIds.isEmpty())
    }

    @Test
    fun `same version is a no-op`() {
        val plan = ImportPlanner.plan(pack(version = 3, "t.a"), installed(version = 3, items = setOf("t.a")))
        assertEquals(ImportPlan.AlreadyInstalled(3), plan)
    }

    @Test
    fun `older version is rejected`() {
        val plan = ImportPlanner.plan(pack(version = 1, "t.a"), installed(version = 2, items = setOf("t.a")))
        assertEquals(ImportPlan.Downgrade(installed = 2, offered = 1), plan)
    }

    private fun apply(pack: PackDto, installed: InstalledContent) =
        assertInstanceOf(ImportPlan.Apply::class.java, ImportPlanner.plan(pack, installed))

    private fun installed(
        version: Int,
        items: Set<String>,
        lessons: Set<String> = setOf("t.l1"),
        topics: Set<String> = setOf("t"),
        allItems: Set<String> = items,
    ) = InstalledContent(version, topics, lessons, items, allItems)

    private fun pack(version: Int, vararg itemIds: String) = PackDto(
        schemaVersion = 1, packId = "p", packVersion = version, title = "P", createdAt = "2026-10-26T12:00:00Z",
        topics = listOf(
            TopicDto(
                id = "t", title = "T", order = 0,
                lessons = listOf(
                    LessonDto(
                        id = "t.l1", title = "L", order = 0, difficulty = 1,
                        items = itemIds.map { ItemDto(it, ItemKind.WORD, it, it, Register.NEUTRAL) },
                    )
                ),
            )
        ),
    )
}
