package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One logged answer, as far as statistics care. */
data class StatAnswer(
    val itemId: String,
    val direction: Direction,
    val exerciseType: ExerciseType,
    val grade: Grade,
    val answeredAt: Long,
    val durationMs: Long?,
    val boxAfter: Int,
    val retry: Boolean,
)

data class StatTopic(val id: String, val title: String, val order: Int)

data class DayCount(val day: LocalDate, val value: Int)

data class HardItem(
    val item: StudyItem,
    val direction: Direction,
    val attempts: Int,
    val wrong: Int,
    val typos: Int,
) {
    val accuracy get() = (attempts - wrong - typos).toDouble() / attempts
}

data class TopicProgress(val topic: StatTopic, val known: Int, val total: Int)

data class Stats(
    val activity: List<DayCount>,
    val activeDays: Int,
    val knownOverTime: List<DayCount>,
    /** Card counts for boxes 1..5 and mastered (index 5). */
    val boxes: List<Int>,
    val mastered: Int,
    val known: Int,
    val introducedItems: Int,
    val totalItems: Int,
    /** Share of correct first attempts in the last 7 days, or null without answers. */
    val recentAccuracy: Double?,
    val hardest: List<HardItem>,
    val topics: List<TopicProgress>,
)

/** Statistics screen numbers (docs/SPEC.md §12). "Known" means box 3 or higher, mastered included. */
object StatsCalculator {
    const val KNOWN_BOX = 3
    const val DAYS = 30
    private const val MIN_ATTEMPTS = 3
    private const val HARDEST = 10

    fun compute(
        answers: List<StatAnswer>,
        cards: List<CardState>,
        items: Map<String, StudyItem>,
        lessonTopic: Map<String, String>,
        topics: List<StatTopic>,
        now: Instant,
        zone: ZoneId,
    ): Stats {
        val today = now.atZone(zone).toLocalDate()
        val days = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        // Retries and the placement test are not learning activity in the usual sense; count first attempts.
        val firstAttempts = answers.filter { !it.retry && it.exerciseType != ExerciseType.PLACEMENT }
        val perDay = firstAttempts.groupingBy { dayOf(it.answeredAt) }.eachCount()
        val activity = days.map { DayCount(it, perDay[it] ?: 0) }

        val activeCards = cards.filter { it.key.itemId in items }
        val boxes = (1..Leitner.MASTERED).map { b -> activeCards.count { it.box == b } }

        val weekAgo = today.minusDays(6)
        val recent = firstAttempts.filter { !dayOf(it.answeredAt).isBefore(weekAgo) }
        val learnable = items.values.filter { !it.placementOnly }

        return Stats(
            activity = activity,
            activeDays = activity.count { it.value > 0 },
            knownOverTime = knownOverTime(answers, activeCards, days, ::dayOf),
            boxes = boxes,
            mastered = boxes.last(),
            known = activeCards.count { it.box >= KNOWN_BOX },
            introducedItems = learnable.count { item -> activeCards.any { it.key.itemId == item.id } },
            totalItems = learnable.size,
            recentAccuracy = recent.takeIf { it.isNotEmpty() }?.let { r -> r.count { it.grade == Grade.CORRECT }.toDouble() / r.size },
            hardest = hardest(firstAttempts, items),
            topics = topicProgress(learnable, activeCards, lessonTopic, topics),
        )
    }

    /**
     * Cards in box ≥ 3 at the end of each day, replayed from the log. Cards the log never mentions (placed by
     * the placement test without an answer) count from the day they were introduced with their current box.
     */
    private fun knownOverTime(
        answers: List<StatAnswer>,
        cards: List<CardState>,
        days: List<LocalDate>,
        dayOf: (Long) -> LocalDate,
    ): List<DayCount> {
        val history = answers.sortedBy { it.answeredAt }.groupBy { CardKey(it.itemId, it.direction) }
        return days.map { day ->
            DayCount(day, cards.count { card ->
                val logged = history[card.key]?.lastOrNull { !dayOf(it.answeredAt).isAfter(day) }
                val box = when {
                    logged != null -> logged.boxAfter
                    history[card.key] == null && !dayOf(card.introducedAt).isAfter(day) -> card.box
                    else -> 0
                }
                box >= KNOWN_BOX
            })
        }
    }

    private fun hardest(firstAttempts: List<StatAnswer>, items: Map<String, StudyItem>): List<HardItem> =
        firstAttempts.groupBy { CardKey(it.itemId, it.direction) }
            .mapNotNull { (key, list) ->
                val item = items[key.itemId] ?: return@mapNotNull null
                HardItem(
                    item, key.direction, list.size,
                    wrong = list.count { it.grade == Grade.WRONG },
                    typos = list.count { it.grade == Grade.TYPO },
                )
            }
            .filter { it.attempts >= MIN_ATTEMPTS && it.wrong + it.typos > 0 }
            .sortedWith(compareByDescending<HardItem> { it.wrong }.thenBy { it.accuracy })
            .take(HARDEST)

    /** Per topic: cards in box ≥ 3 out of all cards its items will have (two per item). */
    private fun topicProgress(
        learnable: List<StudyItem>,
        cards: List<CardState>,
        lessonTopic: Map<String, String>,
        topics: List<StatTopic>,
    ): List<TopicProgress> {
        val knownByItem = cards.filter { it.box >= KNOWN_BOX }.groupingBy { it.key.itemId }.eachCount()
        val itemsByTopic = learnable.groupBy { lessonTopic[it.lessonId] }
        return topics.sortedBy { it.order }.mapNotNull { topic ->
            val topicItems = itemsByTopic[topic.id].orEmpty()
            if (topicItems.isEmpty()) return@mapNotNull null
            TopicProgress(topic, topicItems.sumOf { knownByItem[it.id] ?: 0 }, topicItems.size * Direction.entries.size)
        }
    }
}
