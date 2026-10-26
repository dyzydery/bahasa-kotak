package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import io.github.dyzydery.bahasakotak.domain.Direction.PL_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlacementTest {
    private val basics = (1..15).map { item("basics.b$it", "basics.l1", placementOnly = true) }
    private val basicsLesson = LessonInfo("basics.l1", "basics", 0, 0, 1, null, false, basics)
    private val meetItems = (1..4).map { item("meet.w$it", "meet.l1") } +
        item("meet.s1", "meet.l1").copy(kind = ItemKind.SENTENCE)
    private val meet1 = LessonInfo("meet.l1", "meet", 1, 0, 1, null, false, meetItems)
    private val meet2 = LessonInfo("meet.l2", "meet", 1, 1, 2, null, false, listOf(item("meet.x", "meet.l2")))
    private val lessons = listOf(basicsLesson, meet1, meet2)

    private fun answerAll(engine: PlacementEngine, answer: (Task.Ask) -> String) {
        while (true) {
            val task = engine.current as? Task.Ask ?: return
            engine.submit(answer(task), NOW, 1000)
            engine.advance()
        }
    }

    private fun correct(task: Task.Ask) = task.item.answers(task.direction).first()

    @Test
    fun `plan samples basics and two non-sentence items from each topic's first lesson`() {
        val tasks = PlacementPlanner.plan(lessons, emptyMap(), Random(1))
        assertEquals(PlacementPlanner.MAX_BASICS, tasks.count { it.item.placementOnly })
        val sampled = tasks.filter { !it.item.placementOnly }
        assertEquals(2, sampled.size)
        assertTrue(sampled.all { it.item.lessonId == "meet.l1" && it.item.kind != ItemKind.SENTENCE })
        assertTrue(tasks.all { it.direction == ID_PL && it.exerciseType == ExerciseType.PLACEMENT })
    }

    @Test
    fun `items that already have cards are not tested`() {
        val cards = meetItems.map { card(it.id, ID_PL, 2) }.associateBy { it.key }
        val tasks = PlacementPlanner.plan(lessons, cards, Random(1))
        assertTrue(tasks.none { it.item.lessonId == "meet.l1" })
    }

    @Test
    fun `grades map to boxes, both directions`() {
        val tasks = listOf(meetItems[0], meetItems[1], basics[0], meetItems[2])
            .map { Task.Ask(it, ID_PL, ExerciseType.PLACEMENT) }
        val engine = PlacementEngine(tasks, lessons, emptySet(), ZONE)
        answerAll(engine) { t ->
            when (t.item) {
                meetItems[0] -> correct(t)
                meetItems[1] -> correct(t).dropLast(1) + "x" // "pl-meet.wx": typo
                else -> "nie wiem"
            }
        }
        val cards = engine.resultCards(NOW).associate { it.key to it.box }
        assertEquals(4, cards[CardKey("meet.w1", ID_PL)])
        assertEquals(4, cards[CardKey("meet.w1", PL_ID)])
        assertEquals(2, cards[CardKey("meet.w2", PL_ID)])
        assertEquals(1, cards[CardKey(basics[0].id, PL_ID)]) // unknown basics enter reviews
        assertTrue(CardKey("meet.w3", ID_PL) !in cards)       // unknown lesson items stay new
    }

    @Test
    fun `all sampled items correct puts the rest of the lesson in box 2 due today, without overwriting cards`() {
        val tasks = meetItems.take(2).map { Task.Ask(it, ID_PL, ExerciseType.PLACEMENT) }
        val engine = PlacementEngine(tasks, lessons, introducedItemIds = setOf("meet.w4"), zone = ZONE)
        answerAll(engine, ::correct)
        val cards = engine.resultCards(NOW).associateBy { it.key }
        assertEquals(2, cards.getValue(CardKey("meet.w3", PL_ID)).box)
        assertEquals(Leitner.dayStart(NOW, ZONE, 0), cards.getValue(CardKey("meet.s1", ID_PL)).dueAt)
        assertTrue(CardKey("meet.w4", ID_PL) !in cards)
        assertTrue(CardKey("meet.x", ID_PL) !in cards) // other lessons untouched
    }

    @Test
    fun `one miss in a lesson keeps its untested items new`() {
        val tasks = meetItems.take(2).map { Task.Ask(it, ID_PL, ExerciseType.PLACEMENT) }
        val engine = PlacementEngine(tasks, lessons, emptySet(), ZONE)
        answerAll(engine) { t -> if (t.item == meetItems[0]) correct(t) else "" }
        assertTrue(engine.resultCards(NOW).none { it.key.itemId == "meet.w3" })
    }

    @Test
    fun `outcomes report the landing box for the log`() {
        val engine = PlacementEngine(listOf(Task.Ask(meetItems[0], ID_PL, ExerciseType.PLACEMENT)), lessons, emptySet(), ZONE)
        val outcome = engine.submit(correct(engine.current as Task.Ask), NOW, 1000)
        assertEquals(0, outcome.boxBefore)
        assertEquals(4, outcome.cardAfter.box)
        assertEquals(1, engine.answeredQuestions)
    }
}
