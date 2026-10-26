package io.github.dyzydery.bahasakotak.data

import androidx.room.withTransaction
import io.github.dyzydery.bahasakotak.domain.AnswerOutcome
import io.github.dyzydery.bahasakotak.domain.CardKey
import io.github.dyzydery.bahasakotak.domain.CardState
import io.github.dyzydery.bahasakotak.domain.GrammarNote
import io.github.dyzydery.bahasakotak.domain.LessonInfo
import io.github.dyzydery.bahasakotak.domain.StudyItem

data class StudySnapshot(
    val items: Map<String, StudyItem>,
    val lessons: List<LessonInfo>,
    val cards: Map<CardKey, CardState>,
)

class StudyRepository(private val db: BahasaDatabase) {
    private val dao = db.studyDao()

    suspend fun snapshot(): StudySnapshot {
        val items = dao.activeItems().map { it.toStudyItem() }
        val itemsByLesson = items.groupBy { it.lessonId }
        val seenNotes = dao.seenGrammarNotes().toSet()
        val lessons = dao.activeLessons().map { row ->
            val l = row.lesson
            LessonInfo(
                id = l.id,
                topicOrder = row.topicOrder,
                order = l.order,
                difficulty = l.difficulty,
                grammarNote = l.grammarNoteId?.let { GrammarNote(it, l.grammarNoteTitle.orEmpty(), l.grammarNoteBody.orEmpty()) },
                grammarNoteSeen = l.id in seenNotes,
                items = itemsByLesson[l.id].orEmpty(),
            )
        }
        return StudySnapshot(
            items = items.associateBy { it.id },
            lessons = lessons,
            cards = dao.cards().associate { c -> CardKey(c.itemId, c.direction) to c.toCardState() },
        )
    }

    suspend fun durationsSince(since: Long) = dao.durationsSince(since)

    /** True once the placement test was finished or skipped (a skip is an empty placement session). */
    val placementDone = dao.observePlacementDone()

    suspend fun saveCards(cards: List<CardState>) = dao.upsertCards(cards.map { it.toEntity() })

    suspend fun markGrammarNoteSeen(lessonId: String) =
        dao.upsertLessonProgress(LessonProgressEntity(lessonId, grammarNoteSeen = true))

    suspend fun saveSession(session: SessionEntity) = dao.upsertSession(session)

    /** Card update and its log entry are written together so the log always matches card state. */
    suspend fun saveAnswer(outcome: AnswerOutcome, sessionId: String) = db.withTransaction {
        dao.upsertCards(listOf(outcome.cardAfter.toEntity()))
        logAnswer(outcome, sessionId)
    }

    /** Log entry only, for the placement test, whose cards are written together when it ends. */
    suspend fun logAnswer(outcome: AnswerOutcome, sessionId: String) =
        dao.insertAnswer(
            AnswerEntity(
                itemId = outcome.task.item.id,
                direction = outcome.task.direction,
                exerciseType = outcome.task.exerciseType,
                prompt = outcome.task.item.prompt(outcome.task.direction),
                userAnswer = outcome.userAnswer,
                expected = outcome.check.expected,
                grade = outcome.check.grade,
                answeredAt = outcome.answeredAt.toEpochMilli(),
                durationMs = outcome.durationMs,
                boxBefore = outcome.boxBefore,
                boxAfter = outcome.cardAfter.box,
                sessionId = sessionId,
                retry = outcome.task.retry,
            )
        )
}

private fun ItemEntity.toStudyItem() = StudyItem(
    id = id, lessonId = lessonId, kind = kind, idText = idText, plText = plText, register = register,
    acceptedId = acceptedId, acceptedPl = acceptedPl, distractorsId = distractorsId, distractorsPl = distractorsPl,
    notes = notes, placementOnly = placementOnly,
)

private fun CardEntity.toCardState() = CardState(
    key = CardKey(itemId, direction),
    box = box,
    dueAt = checkNotNull(dueAt),
    masteredIntervalDays = masteredIntervalDays,
    introducedAt = checkNotNull(introducedAt),
)

private fun CardState.toEntity() = CardEntity(
    itemId = key.itemId,
    direction = key.direction,
    box = box,
    dueAt = dueAt,
    masteredIntervalDays = masteredIntervalDays,
    introducedAt = introducedAt,
)
