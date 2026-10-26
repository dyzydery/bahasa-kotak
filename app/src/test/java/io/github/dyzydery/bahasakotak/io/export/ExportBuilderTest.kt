package io.github.dyzydery.bahasakotak.io.export

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.github.dyzydery.bahasakotak.RepoFiles
import io.github.dyzydery.bahasakotak.data.AnswerEntity
import io.github.dyzydery.bahasakotak.data.CardEntity
import io.github.dyzydery.bahasakotak.data.LessonProgressEntity
import io.github.dyzydery.bahasakotak.data.SessionEntity
import io.github.dyzydery.bahasakotak.data.toRows
import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.ExerciseType
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.LessonStatus
import io.github.dyzydery.bahasakotak.io.pack.PackDto
import io.github.dyzydery.bahasakotak.io.pack.PackReadResult
import io.github.dyzydery.bahasakotak.io.pack.PackReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ExportBuilderTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-10-27T18:00:00Z")
    private val packReader = PackReader(RepoFiles.packSchema)
    private val starter = (packReader.read(RepoFiles.starterPack) as PackReadResult.Valid).pack
    private val rows = starter.toRows(importedAt = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli())

    private fun data(
        items: List<io.github.dyzydery.bahasakotak.data.ItemEntity> = rows.items,
        cards: List<CardEntity> = emptyList(),
    ) = ExportData(
        packs = listOf(rows.pack),
        topics = rows.topics,
        lessons = rows.lessons,
        items = items,
        cards = cards,
        answers = listOf(
            AnswerEntity(
                id = 1, itemId = "meet.rapat", direction = Direction.PL_ID, exerciseType = ExerciseType.TYPED,
                prompt = "spotkanie", userAnswer = "rapar", expected = "rapat", grade = Grade.TYPO,
                answeredAt = now.toEpochMilli(), durationMs = null, boxBefore = 2, boxAfter = 2,
                sessionId = "s1", retry = false,
            )
        ),
        sessions = listOf(SessionEntity("s1", now.toEpochMilli() - 60_000, null, 1, placement = false)),
        lessonProgress = listOf(LessonProgressEntity("meet.intro", grammarNoteSeen = true)),
    )

    private fun card(itemId: String, direction: Direction, box: Int) =
        CardEntity(itemId, direction, box, dueAt = now.toEpochMilli(), introducedAt = now.toEpochMilli())

    private fun build(data: ExportData = data()) = ExportBuilder.build(data, "0.1.0", now, zone)

    @Test
    fun `export validates against the export schema, and its content against the pack schema`() {
        val file = ExportBuilder.toFile(build(), now, zone)
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(RepoFiles.exportSchema)
        val errors = schema.validate(file.json, InputFormat.JSON)
        assertTrue(errors.isEmpty(), errors.toString())

        val content = Json.parseToJsonElement(file.json).jsonObject.getValue("content").jsonArray
        for (pack in content) {
            val result = packReader.read(pack.toString())
            assertTrue(result is PackReadResult.Valid, result.toString())
        }
        assertEquals("bahasa-kotak-export-20261027-1900.json", file.fileName)
    }

    @Test
    fun `content reproduces the installed pack`() {
        val exported = build().content.single()
        assertEquals(starter.copy(createdAt = "2026-10-26T10:00:00Z"), exported)
    }

    @Test
    fun `retired items are left out of content but their cards stay, flagged`() {
        val items = rows.items.map { if (it.id == "meet.rapat") it.copy(retired = true) else it }
        val export = build(data(items, cards = listOf(card("meet.rapat", Direction.PL_ID, 3))))
        assertFalse(export.content.single().topics.flatMap { it.lessons }.flatMap { it.items }.any { it.id == "meet.rapat" })
        assertTrue(export.cards.single().retired)
    }

    @Test
    fun `lesson statuses follow unlocking`() {
        val introItems = rows.items.filter { it.lessonId == "meet.intro" }
        val cards = introItems.flatMap { i -> Direction.entries.map { card(i.id, it, 2) } } +
            card("meet.rapat", Direction.ID_PL, 1)
        val statuses = build(data(cards = cards)).lessons.associate { it.lessonId to it.status }
        assertEquals(LessonStatus.COMPLETED, statuses["basics.l1"]) // placement-only lesson never blocks
        assertEquals(LessonStatus.COMPLETED, statuses["meet.intro"])
        assertEquals(LessonStatus.IN_PROGRESS, statuses["meet.meeting"])
        assertEquals(LessonStatus.AVAILABLE, statuses["comm.email"])
        assertEquals(LessonStatus.LOCKED, statuses["comm.status"])
    }

    @Test
    fun `answers, sessions and metadata are carried over`() {
        val export = build()
        assertEquals("Europe/Warsaw", export.timeZone)
        assertEquals(listOf(InstalledPackDto("starter", starter.packVersion, "2026-10-26T10:00:00Z")), export.installedPacks)
        assertEquals("rapar", export.answers.single().userAnswer)
        assertEquals(null, export.answers.single().durationMs)
        assertTrue(export.lessons.single { it.lessonId == "meet.intro" }.grammarNoteSeen)
        assertEquals(null, export.sessions.single().endedAt)
    }
}
