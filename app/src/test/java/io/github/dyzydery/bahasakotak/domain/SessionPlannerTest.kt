package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import io.github.dyzydery.bahasakotak.domain.Direction.PL_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class SessionPlannerTest {
    private val nowMillis = NOW.toEpochMilli()
    private val tomorrow = midnight("2026-10-27")

    private fun lesson(id: String, topicOrder: Int, order: Int, items: List<StudyItem>, note: GrammarNote? = null, seen: Boolean = false) =
        LessonInfo(id, topicOrder, order, difficulty = 1, grammarNote = note, grammarNoteSeen = seen, items = items)

    private fun input(lessons: List<LessonInfo>, cards: List<CardState> = emptyList(), limit: Int = 20) = PlannerInput(
        nowMillis = nowMillis,
        cards = cards.associateBy { it.key },
        items = lessons.flatMap { it.items }.associateBy { it.id },
        lessons = lessons,
        taskLimit = limit,
    )

    private fun plan(input: PlannerInput) = SessionPlanner.plan(input, Random(42))

    @Test
    fun `fresh start introduces up to five new items with both directions`() {
        val items = (1..8).map { item("t.w$it") }
        val tasks = plan(input(listOf(lesson("t.l1", 0, 0, items))))
        val intros = tasks.filterIsInstance<Task.Intro>().map { it.item.id }
        assertEquals(items.take(5).map { it.id }, intros)
        val asks = tasks.filterIsInstance<Task.Ask>()
        assertEquals(5, asks.count { it.direction == ID_PL })
        assertEquals(5, asks.count { it.direction == PL_ID })
    }

    @Test
    fun `each new item is introduced before it is asked`() {
        val tasks = plan(input(listOf(lesson("t.l1", 0, 0, (1..5).map { item("t.w$it") }))))
        for (id in (1..5).map { "t.w$it" }) {
            val intro = tasks.indexOfFirst { it is Task.Intro && it.item.id == id }
            val firstAsk = tasks.indexOfFirst { it is Task.Ask && it.item.id == id }
            assertTrue(intro in 0 until firstAsk, "$id intro at $intro, ask at $firstAsk")
        }
    }

    @Test
    fun `due reviews come before new material and respect the limit`() {
        val reviewed = (1..30).map { item("t.r$it") }
        val fresh = (1..5).map { item("t.n$it", lessonId = "t.l2") }
        val cards = reviewed.flatMap { listOf(card(it.id, ID_PL, 3), card(it.id, PL_ID, 3)) }
        val tasks = plan(input(listOf(lesson("t.l1", 0, 0, reviewed), lesson("t.l2", 0, 1, fresh)), cards, limit = 20))
        assertEquals(20, tasks.count { it is Task.Ask })
        assertTrue(tasks.none { it is Task.Intro })
    }

    @Test
    fun `reviews are balanced across directions`() {
        val items = (1..20).map { item("t.r$it") }
        // Many more PL->ID cards due than ID->PL.
        val cards = items.map { card(it.id, PL_ID, 2) } + items.take(6).map { card(it.id, ID_PL, 2) } +
            items.drop(6).map { card(it.id, ID_PL, 2, dueAt = tomorrow) }
        val asks = plan(input(listOf(lesson("t.l1", 0, 0, items)), cards, limit = 10)).filterIsInstance<Task.Ask>()
        assertEquals(10, asks.size)
        assertTrue(abs(asks.count { it.direction == PL_ID } - asks.count { it.direction == ID_PL }) <= 1)
    }

    @Test
    fun `cards not yet due are skipped`() {
        val items = listOf(item("t.a"))
        val cards = listOf(card("t.a", ID_PL, 2, dueAt = tomorrow), card("t.a", PL_ID, 2, dueAt = tomorrow))
        assertTrue(plan(input(listOf(lesson("t.l1", 0, 0, items)), cards)).isEmpty())
    }

    @Test
    fun `overdue cards are picked oldest first and mastered last`() {
        val items = (1..3).map { item("t.r$it") }
        val cards = listOf(
            card("t.r1", ID_PL, Leitner.MASTERED, dueAt = midnight("2026-10-01")),
            card("t.r2", ID_PL, 2, dueAt = midnight("2026-10-20")),
            card("t.r3", ID_PL, 2, dueAt = midnight("2026-10-10")),
        )
        val asks = plan(input(listOf(lesson("t.l1", 0, 0, items)), cards, limit = 2)).filterIsInstance<Task.Ask>()
        assertEquals(setOf("t.r3", "t.r2"), asks.map { it.item.id }.toSet())
    }

    @Test
    fun `next lesson opens only when every card of the current one is in box 2 or higher`() {
        val l1 = lesson("t.l1", 0, 0, listOf(item("t.a"), item("t.b")))
        val l2 = lesson("t.l2", 0, 1, listOf(item("t.c", lessonId = "t.l2")))
        val almost = listOf(
            card("t.a", ID_PL, 2, tomorrow), card("t.a", PL_ID, 2, tomorrow),
            card("t.b", ID_PL, 2, tomorrow), card("t.b", PL_ID, 1, tomorrow),
        )
        assertTrue(SessionPlanner.pickNewItems(input(listOf(l1, l2), almost)).isEmpty())

        val done = almost.map { it.copy(box = 2) }
        assertEquals(listOf("t.c"), SessionPlanner.pickNewItems(input(listOf(l1, l2), done)).map { it.id })
    }

    @Test
    fun `topics advance in parallel, lowest topic first`() {
        val a = lesson("a.l1", 0, 0, listOf(item("a.1", "a.l1"), item("a.2", "a.l1")))
        val b = lesson("b.l1", 1, 0, listOf(item("b.1", "b.l1")))
        val introducedA = listOf(card("a.1", ID_PL, 1, tomorrow), card("a.1", PL_ID, 1, tomorrow))
        assertEquals(listOf("a.2", "b.1"), SessionPlanner.pickNewItems(input(listOf(a, b), introducedA)).map { it.id })
    }

    @Test
    fun `placement-only items are never new material`() {
        val l = lesson("basics.l1", 0, 0, listOf(item("basics.ya", "basics.l1", placementOnly = true)))
        val next = lesson("t.l1", 1, 0, listOf(item("t.a")))
        assertEquals(listOf("t.a"), SessionPlanner.pickNewItems(input(listOf(l, next))).map { it.id })
        assertTrue(SessionPlanner.isFinished(l, emptyMap()))
    }

    @Test
    fun `grammar note precedes the first new item of its lesson, once`() {
        val note = GrammarNote("t.l1.note", "Szyk", "…")
        val tasks = plan(input(listOf(lesson("t.l1", 0, 0, listOf(item("t.a"), item("t.b")), note))))
        assertTrue(tasks.first() is Task.Note)
        assertEquals(1, tasks.count { it is Task.Note })
    }

    @Test
    fun `seen grammar note is not shown again`() {
        val note = GrammarNote("t.l1.note", "Szyk", "…")
        val tasks = plan(input(listOf(lesson("t.l1", 0, 0, listOf(item("t.a")), note, seen = true))))
        assertFalse(tasks.any { it is Task.Note })
    }

    @Test
    fun `task limit targets five minutes at the median pace`() {
        assertEquals(SessionPlanner.DEFAULT_TASK_LIMIT, SessionPlanner.taskLimit(List(5) { 1_000L }))
        assertEquals(30, SessionPlanner.taskLimit(List(25) { 10_000L }))
        assertEquals(10, SessionPlanner.taskLimit(List(25) { 60_000L }))
        assertEquals(40, SessionPlanner.taskLimit(List(25) { 2_000L }))
    }
}
