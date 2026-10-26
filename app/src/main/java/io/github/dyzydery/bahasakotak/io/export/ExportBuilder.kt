package io.github.dyzydery.bahasakotak.io.export

import io.github.dyzydery.bahasakotak.data.AnswerEntity
import io.github.dyzydery.bahasakotak.data.CardEntity
import io.github.dyzydery.bahasakotak.data.ItemEntity
import io.github.dyzydery.bahasakotak.data.LessonEntity
import io.github.dyzydery.bahasakotak.data.LessonProgressEntity
import io.github.dyzydery.bahasakotak.data.PackEntity
import io.github.dyzydery.bahasakotak.data.SessionEntity
import io.github.dyzydery.bahasakotak.data.TopicEntity
import io.github.dyzydery.bahasakotak.data.reconstructPacks
import io.github.dyzydery.bahasakotak.domain.CardKey
import io.github.dyzydery.bahasakotak.domain.CardState
import io.github.dyzydery.bahasakotak.domain.LessonInfo
import io.github.dyzydery.bahasakotak.domain.Progress
import io.github.dyzydery.bahasakotak.domain.StudyItem
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Every table the export reads, loaded in one go. */
data class ExportData(
    val packs: List<PackEntity>,
    val topics: List<TopicEntity>,
    val lessons: List<LessonEntity>,
    val items: List<ItemEntity>,
    val cards: List<CardEntity>,
    val answers: List<AnswerEntity>,
    val sessions: List<SessionEntity>,
    val lessonProgress: List<LessonProgressEntity>,
)

data class ExportFile(val fileName: String, val json: String)

object ExportBuilder {
    // Compact on purpose: the export goes into Claude's context, where indentation is pure overhead.
    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    fun build(data: ExportData, appVersion: String, now: Instant, zone: ZoneId): ExportDto {
        val itemsById = data.items.associateBy { it.id }
        return ExportDto(
            exportedAt = now.toString(),
            timeZone = zone.id,
            appVersion = appVersion,
            installedPacks = data.packs.map { InstalledPackDto(it.id, it.version, iso(it.importedAt)) },
            content = reconstructPacks(data.packs, data.topics, data.lessons, data.items),
            answers = data.answers.sortedBy { it.answeredAt }.map { a ->
                AnswerDto(
                    itemId = a.itemId, direction = a.direction, exerciseType = a.exerciseType, prompt = a.prompt,
                    userAnswer = a.userAnswer, expected = a.expected, grade = a.grade, answeredAt = iso(a.answeredAt),
                    durationMs = a.durationMs, boxBefore = a.boxBefore, boxAfter = a.boxAfter,
                    sessionId = a.sessionId, retry = a.retry,
                )
            },
            cards = data.cards.map { c ->
                CardDto(
                    itemId = c.itemId, direction = c.direction, box = c.box, dueAt = iso(checkNotNull(c.dueAt)),
                    masteredIntervalDays = c.masteredIntervalDays, introducedAt = iso(checkNotNull(c.introducedAt)),
                    retired = itemsById[c.itemId]?.retired ?: true,
                )
            },
            lessons = lessonProgress(data),
            sessions = data.sessions.sortedBy { it.startedAt }.map { s ->
                SessionDto(s.id, iso(s.startedAt), s.endedAt?.let(::iso), s.taskCount, s.placement)
            },
        )
    }

    fun toFile(export: ExportDto, now: Instant, zone: ZoneId) =
        ExportFile(fileName(now, zone), json.encodeToString(ExportDto.serializer(), export))

    fun fileName(now: Instant, zone: ZoneId) =
        "bahasa-kotak-export-${FILE_STAMP.format(ZonedDateTime.ofInstant(now, zone))}.json"

    private fun lessonProgress(data: ExportData): List<LessonProgressDto> {
        val topicOrder = data.topics.associate { it.id to it.order }
        val activeItems = data.items.filter { !it.retired }.groupBy { it.lessonId }
        val activeLessons = data.lessons.filter { !it.retired }
        val infos = activeLessons.map { l ->
            LessonInfo(
                id = l.id, topicId = l.topicId, topicOrder = topicOrder[l.topicId] ?: 0, order = l.order, difficulty = l.difficulty,
                grammarNote = null, grammarNoteSeen = false,
                items = activeItems[l.id].orEmpty().map { StudyItem(it.id, it.lessonId, it.kind, it.idText, it.plText, it.register, placementOnly = it.placementOnly) },
            )
        }
        val cards = data.cards.associate { c ->
            CardKey(c.itemId, c.direction) to CardState(CardKey(c.itemId, c.direction), c.box, c.dueAt ?: 0, c.masteredIntervalDays, c.introducedAt ?: 0)
        }
        val statuses = Progress.lessonStatuses(infos, cards)
        val seen = data.lessonProgress.filter { it.grammarNoteSeen }.mapTo(HashSet()) { it.lessonId }
        return activeLessons.map { l -> LessonProgressDto(l.id, l.topicId, statuses.getValue(l.id), l.id in seen) }
    }

    private fun iso(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).toString()

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
}
