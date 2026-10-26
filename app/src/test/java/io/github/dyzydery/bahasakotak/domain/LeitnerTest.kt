package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LeitnerTest {
    private fun review(box: Int, grade: Grade, masteredInterval: Int = 0) =
        Leitner.review(card("a", ID_PL, box).copy(masteredIntervalDays = masteredInterval), grade, NOW, ZONE)

    @Test
    fun `new card starts in box 1 due today`() {
        val c = Leitner.newCard(CardKey("a", ID_PL), NOW, ZONE)
        assertEquals(1, c.box)
        assertEquals(midnight("2026-10-26"), c.dueAt)
        assertEquals(NOW.toEpochMilli(), c.introducedAt)
    }

    @ParameterizedTest(name = "box {0} -> {1}, due {2}")
    @CsvSource(
        "1, 2, 2026-10-28",
        "2, 3, 2026-10-30",
        "3, 4, 2026-11-03",
        "4, 5, 2026-11-11",
    )
    fun `correct moves one box up with that box's interval`(from: Int, to: Int, due: String) {
        val c = review(from, Grade.CORRECT)
        assertEquals(to, c.box)
        assertEquals(midnight(due), c.dueAt)
    }

    @Test
    fun `correct in box 5 masters the card for 60 days`() {
        val c = review(5, Grade.CORRECT)
        assertEquals(Leitner.MASTERED, c.box)
        assertEquals(60, c.masteredIntervalDays)
        assertEquals(midnight("2026-12-25"), c.dueAt)
    }

    @Test
    fun `correct on a mastered card doubles the interval`() {
        val c = review(Leitner.MASTERED, Grade.CORRECT, masteredInterval = 60)
        assertEquals(Leitner.MASTERED, c.box)
        assertEquals(120, c.masteredIntervalDays)
        val again = Leitner.review(c, Grade.CORRECT, NOW, ZONE)
        assertEquals(240, again.masteredIntervalDays)
    }

    @ParameterizedTest
    @CsvSource("1", "3", "5", "6")
    fun `wrong sends any card back to box 1 due tomorrow`(from: Int) {
        val c = review(from, Grade.WRONG, masteredInterval = if (from == 6) 120 else 0)
        assertEquals(1, c.box)
        assertEquals(0, c.masteredIntervalDays)
        assertEquals(midnight("2026-10-27"), c.dueAt)
    }

    @ParameterizedTest
    @CsvSource("1, 2026-10-27", "3, 2026-10-30", "5, 2026-11-11")
    fun `typo keeps the box and reschedules by its interval`(box: Int, due: String) {
        val c = review(box, Grade.TYPO)
        assertEquals(box, c.box)
        assertEquals(midnight(due), c.dueAt)
    }

    @Test
    fun `typo on a mastered card keeps its interval`() {
        val c = review(Leitner.MASTERED, Grade.TYPO, masteredInterval = 120)
        assertEquals(Leitner.MASTERED, c.box)
        assertEquals(120, c.masteredIntervalDays)
        assertEquals(midnight("2027-02-23"), c.dueAt)
    }

    @Test
    fun `due date is a local midnight even late in the evening`() {
        val lateEvening = Instant.parse("2026-10-26T22:50:00Z") // 23:50 in Warsaw
        val c = Leitner.review(card("a", ID_PL, 1), Grade.WRONG, lateEvening, ZONE)
        assertEquals(midnight("2026-10-27"), c.dueAt)
    }
}
