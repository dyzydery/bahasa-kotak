package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Five-box Leitner system with day-granular due dates (due at local midnight), so a card due "in 1 day"
 * shows up in tomorrow's session whatever time of day it is played. See docs/SPEC.md §5.
 */
object Leitner {
    const val MASTERED = 6
    const val FIRST_MASTERED_INTERVAL_DAYS = 60

    /** Days until the next review for a card that has just landed in [box] (1..5). */
    private val BOX_INTERVAL_DAYS = intArrayOf(1, 2, 4, 8, 16)

    fun intervalDays(box: Int): Int = BOX_INTERVAL_DAYS[box - 1]

    fun newCard(key: CardKey, now: Instant, zone: ZoneId) = CardState(
        key = key,
        box = 1,
        dueAt = dayStart(now, zone, plusDays = 0),
        introducedAt = now.toEpochMilli(),
    )

    fun review(card: CardState, grade: Grade, now: Instant, zone: ZoneId): CardState = when (grade) {
        Grade.WRONG -> card.copy(box = 1, masteredIntervalDays = 0, dueAt = dayStart(now, zone, intervalDays(1)))
        Grade.TYPO ->
            if (card.box == MASTERED) card.copy(dueAt = dayStart(now, zone, card.masteredIntervalDays))
            else card.copy(dueAt = dayStart(now, zone, intervalDays(card.box)))
        Grade.CORRECT -> when (card.box) {
            MASTERED -> {
                val interval = card.masteredIntervalDays * 2
                card.copy(masteredIntervalDays = interval, dueAt = dayStart(now, zone, interval))
            }
            5 -> card.copy(
                box = MASTERED,
                masteredIntervalDays = FIRST_MASTERED_INTERVAL_DAYS,
                dueAt = dayStart(now, zone, FIRST_MASTERED_INTERVAL_DAYS),
            )
            else -> card.copy(box = card.box + 1, dueAt = dayStart(now, zone, intervalDays(card.box + 1)))
        }
    }

    /** Places a card directly into [box] (placement test), due after that box's interval. */
    fun place(key: CardKey, box: Int, now: Instant, zone: ZoneId) = CardState(
        key = key,
        box = box,
        dueAt = dayStart(now, zone, intervalDays(box)),
        introducedAt = now.toEpochMilli(),
    )

    fun dayStart(now: Instant, zone: ZoneId, plusDays: Int): Long =
        now.atZone(zone).toLocalDate().plusDays(plusDays.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
}
