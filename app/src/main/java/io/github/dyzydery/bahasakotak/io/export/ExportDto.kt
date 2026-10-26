package io.github.dyzydery.bahasakotak.io.export

import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.ExerciseType
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.LessonStatus
import io.github.dyzydery.bahasakotak.io.pack.PackDto
import kotlinx.serialization.Serializable

/** Mirrors `schemas/export.schema.json` (schemaVersion 1). Timestamps are ISO-8601 UTC. */
@Serializable
data class ExportDto(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val timeZone: String,
    val appVersion: String,
    val installedPacks: List<InstalledPackDto>,
    val content: List<PackDto>,
    val answers: List<AnswerDto>,
    val cards: List<CardDto>,
    val lessons: List<LessonProgressDto>,
    val sessions: List<SessionDto>,
)

@Serializable
data class InstalledPackDto(val packId: String, val packVersion: Int, val importedAt: String)

@Serializable
data class AnswerDto(
    val itemId: String,
    val direction: Direction,
    val exerciseType: ExerciseType,
    val prompt: String,
    val userAnswer: String,
    val expected: String,
    val grade: Grade,
    val answeredAt: String,
    val durationMs: Long? = null,
    val boxBefore: Int,
    val boxAfter: Int,
    val sessionId: String,
    val retry: Boolean,
)

@Serializable
data class CardDto(
    val itemId: String,
    val direction: Direction,
    val box: Int,
    val dueAt: String,
    val masteredIntervalDays: Int,
    val introducedAt: String,
    val retired: Boolean,
)

@Serializable
data class LessonProgressDto(
    val lessonId: String,
    val topicId: String,
    val status: LessonStatus,
    val grammarNoteSeen: Boolean,
)

@Serializable
data class SessionDto(
    val id: String,
    val startedAt: String,
    val endedAt: String? = null,
    val taskCount: Int,
    val placement: Boolean,
)
