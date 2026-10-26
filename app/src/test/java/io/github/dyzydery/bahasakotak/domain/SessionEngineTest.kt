package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import io.github.dyzydery.bahasakotak.domain.Direction.PL_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SessionEngineTest {
    private val a = item("t.a")
    private val fillers = (1..6).map { item("t.f$it") }

    private fun engine(tasks: List<Task>, cards: List<CardState> = emptyList()) =
        SessionEngine(tasks, cards.associateBy { it.key }, ZONE, Random(1))

    /** Drains the queue answering every question correctly; returns the tasks seen. */
    private fun SessionEngine.drain(): List<Task> {
        val seen = mutableListOf<Task>()
        while (true) {
            val t = current ?: return seen
            seen += t
            if (t is Task.Ask) submit(t.item.answers(t.direction).first(), NOW, 1000)
            advance()
        }
    }

    @Test
    fun `correct answer moves the card and is reported for persisting`() {
        val e = engine(listOf(Task.Ask(a, ID_PL)), listOf(card("t.a", ID_PL, 2)))
        val outcome = e.submit("pl-t.a", NOW, 1500)
        assertEquals(Grade.CORRECT, outcome.check.grade)
        assertEquals(2, outcome.boxBefore)
        assertEquals(3, outcome.cardAfter.box)
        assertEquals(1, e.answeredQuestions)
    }

    @Test
    fun `wrong answer is retried later in the same session without moving the card again`() {
        val tasks = listOf(Task.Ask(a, ID_PL)) + fillers.map { Task.Ask(it, ID_PL) }
        val e = engine(tasks, listOf(card("t.a", ID_PL, 4)) + fillers.map { card(it.id, ID_PL, 1) })

        val first = e.submit("zle", NOW, 1000)
        assertEquals(Grade.WRONG, first.check.grade)
        assertEquals(1, first.cardAfter.box)
        e.advance()

        val rest = e.drain()
        val retryIndex = rest.indexOfFirst { it is Task.Ask && it.item == a }
        assertTrue(retryIndex in 3..5, "retry at $retryIndex")
        assertTrue((rest[retryIndex] as Task.Ask).retry)
        // Retries don't count towards progress.
        assertEquals(tasks.size, e.answeredQuestions)
        assertEquals(tasks.size, e.plannedQuestions)
    }

    @Test
    fun `retry answer is logged with an unchanged box`() {
        val e = engine(listOf(Task.Ask(a, ID_PL, retry = true)), listOf(card("t.a", ID_PL, 1)))
        val outcome = e.submit("pl-t.a", NOW, 1000)
        assertEquals(1, outcome.boxBefore)
        assertEquals(1, outcome.cardAfter.box)
        assertEquals(0, e.answeredQuestions)
    }

    @Test
    fun `retries stop after three more wrong answers`() {
        val e = engine(listOf(Task.Ask(a, ID_PL)), listOf(card("t.a", ID_PL, 1)))
        var attempts = 0
        while (e.current != null) {
            e.submit("zle", NOW, 1000)
            e.advance()
            attempts++
        }
        assertEquals(4, attempts)
    }

    @Test
    fun `typo is not retried`() {
        val word = a.copy(plText = "spotkanie")
        val e = engine(listOf(Task.Ask(word, ID_PL)), listOf(card("t.a", ID_PL, 3)))
        assertEquals(Grade.TYPO, e.submit("spotkanei", NOW, 1000).check.grade)
        e.advance()
        assertNull(e.current)
    }

    @Test
    fun `intro creates both cards in box 1 and keeps existing ones`() {
        val e = engine(listOf(Task.Intro(a)), listOf(card("t.a", PL_ID, 3)))
        val created = e.introduce(a, NOW).associateBy { it.key.direction }
        assertEquals(1, created.getValue(ID_PL).box)
        assertEquals(3, created.getValue(PL_ID).box)
    }

    @Test
    fun `grades are tallied for first attempts only`() {
        val tasks = listOf(Task.Ask(a, ID_PL)) + fillers.take(3).map { Task.Ask(it, ID_PL) }
        val e = engine(tasks, listOf(card("t.a", ID_PL, 1)) + fillers.map { card(it.id, ID_PL, 1) })
        e.submit("zle", NOW, 1000)
        e.advance()
        e.drain()
        assertEquals(1, e.grades[Grade.WRONG])
        assertEquals(3, e.grades[Grade.CORRECT])
        assertFalse(e.grades.containsKey(Grade.TYPO))
    }
}
