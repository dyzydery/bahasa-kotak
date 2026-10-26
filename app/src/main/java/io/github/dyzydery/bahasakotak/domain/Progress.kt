package io.github.dyzydery.bahasakotak.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LessonStatus {
    @SerialName("locked") LOCKED,
    @SerialName("available") AVAILABLE,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("completed") COMPLETED,
}

object Progress {
    /** Mirrors [SessionPlanner]'s unlocking: per topic, lessons before the first unfinished one are complete. */
    fun lessonStatuses(lessons: List<LessonInfo>, cards: Map<CardKey, CardState>): Map<String, LessonStatus> {
        val introduced = cards.keys.mapTo(HashSet()) { it.itemId }
        val result = mutableMapOf<String, LessonStatus>()
        for (topic in lessons.groupBy { it.topicOrder }.values) {
            var open = true
            for (lesson in topic.sortedBy { it.order }) {
                result[lesson.id] = when {
                    !open -> LessonStatus.LOCKED
                    SessionPlanner.isFinished(lesson, cards) -> LessonStatus.COMPLETED
                    else -> {
                        open = false
                        if (lesson.items.any { it.id in introduced }) LessonStatus.IN_PROGRESS else LessonStatus.AVAILABLE
                    }
                }
            }
        }
        return result
    }
}
