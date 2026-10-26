package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.ZoneId

val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

/** 2026-10-26 15:30 local time. */
val NOW: Instant = Instant.parse("2026-10-26T14:30:00Z")

fun midnight(date: String): Long = java.time.LocalDate.parse(date).atStartOfDay(ZONE).toInstant().toEpochMilli()

fun item(id: String, lessonId: String = "t.l1", placementOnly: Boolean = false) = StudyItem(
    id = id, lessonId = lessonId, kind = ItemKind.WORD, idText = "id-$id", plText = "pl-$id",
    register = Register.NEUTRAL, placementOnly = placementOnly,
)

fun card(itemId: String, direction: Direction, box: Int, dueAt: Long = midnight("2026-10-26")) =
    CardState(CardKey(itemId, direction), box, dueAt, introducedAt = 0)
