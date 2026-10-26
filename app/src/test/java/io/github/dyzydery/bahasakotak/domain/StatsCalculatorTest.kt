package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import io.github.dyzydery.bahasakotak.domain.Direction.PL_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private val a = item("meet.a", "meet.l1")
    private val b = item("meet.b", "meet.l1")
    private val c = item("telco.c", "telco.l1")
    private val basic = item("basics.ya", "basics.l1", placementOnly = true)
    private val items = listOf(a, b, c, basic).associateBy { it.id }
    private val lessonTopic = mapOf("meet.l1" to "meet", "telco.l1" to "telco", "basics.l1" to "basics")
    private val topics = listOf(StatTopic("telco", "Telco", 2), StatTopic("meet", "Spotkania", 1), StatTopic("basics", "Podstawy", 0))

    private fun at(date: String, hour: Int = 12) =
        LocalDate.parse(date).atTime(hour, 0).atZone(ZONE).toInstant().toEpochMilli()

    private fun answer(item: StudyItem, d: Direction, grade: Grade, date: String, boxAfter: Int = 2, retry: Boolean = false, type: ExerciseType = ExerciseType.TYPED) =
        StatAnswer(item.id, d, type, grade, at(date), 3000, boxAfter, retry)

    private fun stateCard(item: StudyItem, d: Direction, box: Int, introduced: String = "2026-10-20") =
        CardState(CardKey(item.id, d), box, 0, introducedAt = at(introduced))

    private fun compute(answers: List<StatAnswer>, cards: List<CardState> = emptyList()) =
        StatsCalculator.compute(answers, cards, items, lessonTopic, topics, NOW, ZONE)

    @Test
    fun `activity counts first attempts per day over 30 days`() {
        val stats = compute(
            listOf(
                answer(a, ID_PL, Grade.CORRECT, "2026-10-26"),
                answer(a, PL_ID, Grade.WRONG, "2026-10-26"),
                answer(a, PL_ID, Grade.CORRECT, "2026-10-26", retry = true),
                answer(b, ID_PL, Grade.CORRECT, "2026-10-24", type = ExerciseType.PLACEMENT),
                answer(b, ID_PL, Grade.CORRECT, "2026-10-20"),
                answer(b, ID_PL, Grade.CORRECT, "2026-09-01"), // outside the window
            )
        )
        assertEquals(30, stats.activity.size)
        assertEquals(LocalDate.parse("2026-10-26"), stats.activity.last().day)
        assertEquals(2, stats.activity.last().value)
        assertEquals(0, stats.activity.single { it.day == LocalDate.parse("2026-10-24") }.value)
        assertEquals(2, stats.activeDays)
    }

    @Test
    fun `recent accuracy uses first attempts of the last 7 days`() {
        val stats = compute(
            listOf(
                answer(a, ID_PL, Grade.CORRECT, "2026-10-26"),
                answer(a, PL_ID, Grade.TYPO, "2026-10-25"),
                answer(b, PL_ID, Grade.WRONG, "2026-10-20"),
                answer(b, PL_ID, Grade.CORRECT, "2026-10-20", retry = true),
                answer(c, ID_PL, Grade.WRONG, "2026-10-01"), // older than 7 days
            )
        )
        assertEquals(1.0 / 3, stats.recentAccuracy!!, 1e-9)
        assertNull(compute(emptyList()).recentAccuracy)
    }

    @Test
    fun `box distribution, mastered and known counts`() {
        val cards = listOf(
            stateCard(a, ID_PL, 1), stateCard(a, PL_ID, 3),
            stateCard(b, ID_PL, 5), stateCard(b, PL_ID, Leitner.MASTERED),
            CardState(CardKey("gone.x", ID_PL), 4, 0, introducedAt = 0), // retired item: ignored
        )
        val stats = compute(emptyList(), cards)
        assertEquals(listOf(1, 0, 1, 0, 1, 1), stats.boxes)
        assertEquals(1, stats.mastered)
        assertEquals(3, stats.known)
        assertEquals(2, stats.introducedItems)
        assertEquals(3, stats.totalItems) // placement-only basics are not lesson material
    }

    @Test
    fun `known over time replays the answer log`() {
        val answers = listOf(
            answer(a, ID_PL, Grade.CORRECT, "2026-10-22", boxAfter = 3),
            answer(a, ID_PL, Grade.WRONG, "2026-10-25", boxAfter = 1),
            answer(b, ID_PL, Grade.CORRECT, "2026-10-24", boxAfter = 4),
        )
        val cards = listOf(
            stateCard(a, ID_PL, 1), stateCard(b, ID_PL, 4),
            stateCard(c, PL_ID, 4, introduced = "2026-10-23"), // placed without an answer
        )
        val series = compute(answers, cards).knownOverTime.associate { it.day.toString() to it.value }
        assertEquals(0, series["2026-10-21"])
        assertEquals(1, series["2026-10-22"])
        assertEquals(2, series["2026-10-23"])
        assertEquals(3, series["2026-10-24"])
        assertEquals(2, series["2026-10-25"])
        assertEquals(2, series["2026-10-26"])
    }

    @Test
    fun `hardest needs three attempts and ranks by wrong answers then accuracy`() {
        val answers = List(3) { answer(a, PL_ID, Grade.WRONG, "2026-10-2${it + 1}") } +
            List(4) { answer(b, ID_PL, if (it == 0) Grade.TYPO else Grade.CORRECT, "2026-10-2${it + 1}") } +
            List(2) { answer(c, ID_PL, Grade.WRONG, "2026-10-2${it + 1}") } +           // only 2 attempts
            List(3) { answer(a, ID_PL, Grade.CORRECT, "2026-10-2${it + 1}") }           // no errors
        val hardest = compute(answers).hardest
        assertEquals(listOf(CardKey("meet.a", PL_ID), CardKey("meet.b", ID_PL)), hardest.map { CardKey(it.item.id, it.direction) })
        assertEquals(0.75, hardest[1].accuracy, 1e-9)
    }

    @Test
    fun `topic progress counts known cards out of two per item, in topic order`() {
        val cards = listOf(stateCard(a, ID_PL, 3), stateCard(a, PL_ID, 2), stateCard(c, ID_PL, Leitner.MASTERED))
        val topicStats = compute(emptyList(), cards).topics
        assertEquals(listOf("meet", "telco"), topicStats.map { it.topic.id }) // basics have no lesson items
        assertEquals(1 to 4, topicStats[0].known to topicStats[0].total)
        assertEquals(1 to 2, topicStats[1].known to topicStats[1].total)
    }
}
