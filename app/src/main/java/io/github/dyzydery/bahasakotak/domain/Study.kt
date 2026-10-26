package io.github.dyzydery.bahasakotak.domain

data class StudyItem(
    val id: String,
    val lessonId: String,
    val kind: ItemKind,
    val idText: String,
    val plText: String,
    val register: Register,
    val acceptedId: List<String> = emptyList(),
    val acceptedPl: List<String> = emptyList(),
    val notes: String? = null,
    val placementOnly: Boolean = false,
) {
    fun prompt(direction: Direction) = if (direction == Direction.PL_ID) plText else idText

    /** Canonical answer first, then the pack's accepted variants. */
    fun answers(direction: Direction) =
        if (direction == Direction.PL_ID) listOf(idText) + acceptedId else listOf(plText) + acceptedPl
}

data class CardKey(val itemId: String, val direction: Direction)

/** Leitner state of one card. box: 1..5, or [Leitner.MASTERED]. dueAt: epoch millis of a local midnight. */
data class CardState(
    val key: CardKey,
    val box: Int,
    val dueAt: Long,
    val masteredIntervalDays: Int = 0,
    val introducedAt: Long,
)

data class GrammarNote(val id: String, val title: String, val body: String)

data class LessonInfo(
    val id: String,
    val topicOrder: Int,
    val order: Int,
    val difficulty: Int,
    val grammarNote: GrammarNote?,
    val grammarNoteSeen: Boolean,
    /** Active items in pack order. */
    val items: List<StudyItem>,
)

sealed interface Task {
    data class Note(val lessonId: String, val note: GrammarNote) : Task
    data class Intro(val item: StudyItem) : Task
    data class Ask(
        val item: StudyItem,
        val direction: Direction,
        val exerciseType: ExerciseType = ExerciseType.TYPED,
        val retry: Boolean = false,
    ) : Task {
        val key get() = CardKey(item.id, direction)
    }
}
