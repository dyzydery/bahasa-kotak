package io.github.dyzydery.bahasakotak.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.ExerciseType
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Register

// Content tables are written only by pack import. Progress tables (cards, answers, sessions, lesson_progress)
// are keyed by content IDs but deliberately have no foreign keys, so re-importing content can never
// cascade into learning history.

@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val title: String,
    val description: String?,
    val importedAt: Long,
)

@Entity(tableName = "topics", indices = [Index("packId")])
data class TopicEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "sortOrder") val order: Int,
    val retired: Boolean = false,
)

@Entity(tableName = "lessons", indices = [Index("topicId"), Index("packId")])
data class LessonEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val packId: String,
    val title: String,
    @ColumnInfo(name = "sortOrder") val order: Int,
    val difficulty: Int,
    val grammarNoteId: String?,
    val grammarNoteTitle: String?,
    val grammarNoteBody: String?,
    val retired: Boolean = false,
)

@Entity(tableName = "items", indices = [Index("lessonId"), Index("packId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val packId: String,
    @ColumnInfo(name = "sortOrder") val order: Int,
    val kind: ItemKind,
    val idText: String,
    val plText: String,
    val register: Register,
    val acceptedId: List<String>,
    val acceptedPl: List<String>,
    val distractorsId: List<String>,
    val distractorsPl: List<String>,
    val notes: String?,
    val tags: List<String>,
    val placementOnly: Boolean,
    val retired: Boolean = false,
)

/** Leitner state for one (item, direction). box: 0 = not introduced, 1..5, 6 = mastered. */
@Entity(tableName = "cards", primaryKeys = ["itemId", "direction"])
data class CardEntity(
    val itemId: String,
    val direction: Direction,
    val box: Int,
    val dueAt: Long?,
    val masteredIntervalDays: Int = 0,
    val introducedAt: Long?,
)

@Entity(tableName = "answers", indices = [Index("itemId"), Index("sessionId"), Index("answeredAt")])
data class AnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val direction: Direction,
    val exerciseType: ExerciseType,
    val prompt: String,
    val userAnswer: String,
    val expected: String,
    val grade: Grade,
    val answeredAt: Long,
    val durationMs: Long?,
    val boxBefore: Int,
    val boxAfter: Int,
    val sessionId: String,
    val retry: Boolean,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val taskCount: Int,
    val placement: Boolean,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val grammarNoteSeen: Boolean,
)
