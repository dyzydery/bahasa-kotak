package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * Placement test (docs/SPEC.md §8): short ID→PL questions on the basics left out of lessons and on a sample
 * of each topic's first lesson. Known material goes straight to higher boxes instead of blocking progress.
 */
object PlacementPlanner {
    const val MAX_BASICS = 12
    const val PER_TOPIC = 2

    fun plan(lessons: List<LessonInfo>, cards: Map<CardKey, CardState>, random: Random = Random.Default): List<Task.Ask> {
        val introduced = cards.keys.mapTo(HashSet()) { it.itemId }
        fun eligible(item: StudyItem) = item.id !in introduced && item.kind != ItemKind.SENTENCE

        val basics = lessons.flatMap { it.items }.filter { it.placementOnly && eligible(it) }
            .shuffled(random).take(MAX_BASICS)
        val sampled = lessons.groupBy { it.topicId }.values.sortedBy { it.first().topicOrder }
            .mapNotNull { topic -> topic.minByOrNull { it.order } }
            .flatMap { lesson ->
                lesson.items.filter { !it.placementOnly && eligible(it) }.shuffled(random).take(PER_TOPIC)
            }
        return (basics + sampled).map { Task.Ask(it, Direction.ID_PL, ExerciseType.PLACEMENT) }
    }
}

/**
 * Runs the placement test: one attempt per question, no retries, no card changes until [resultCards].
 * Answer outcomes report the box each item will land in (0 = stays new).
 */
class PlacementEngine(
    tasks: List<Task.Ask>,
    private val lessons: List<LessonInfo>,
    /** Items that already have cards; the test never overwrites existing progress. */
    private val introducedItemIds: Set<String>,
    private val zone: ZoneId,
) : SessionRunner {
    private val queue = ArrayDeque(tasks)
    private val results = mutableMapOf<StudyItem, Grade>()

    override val plannedQuestions = tasks.size
    override val answeredQuestions get() = results.size
    override val grades get() = results.values.groupingBy { it }.eachCount()
    override val current: Task? get() = queue.firstOrNull()

    override fun introduce(item: StudyItem, now: Instant): List<CardState> = emptyList()

    override fun submit(answer: String, now: Instant, durationMs: Long?): AnswerOutcome {
        val task = current as? Task.Ask ?: error("Current task is not a question: $current")
        val check = grade(task, answer)
        results[task.item] = check.grade
        val box = boxFor(task.item, check.grade)
        val placed = CardState(task.key, box, Leitner.dayStart(now, zone, 0), introducedAt = now.toEpochMilli())
        return AnswerOutcome(task, answer, check, boxBefore = 0, cardAfter = placed, answeredAt = now, durationMs = durationMs)
    }

    override fun advance() {
        queue.removeFirstOrNull()
    }

    /**
     * Cards to create once the test ends:
     * - correct → both directions in box 4, typo → box 2 (due after that box's interval),
     * - wrong basics → box 1 due today (basics are never introduced as new material otherwise),
     * - wrong lesson items stay new,
     * - a lesson whose sampled items were all correct puts its other items in box 2, due today,
     *   so the next session checks them right away.
     */
    fun resultCards(now: Instant): List<CardState> {
        val cards = mutableListOf<CardState>()
        for ((item, grade) in results) {
            val box = boxFor(item, grade)
            if (box == 0) continue
            for (d in Direction.entries) {
                val key = CardKey(item.id, d)
                cards += if (box == 1) Leitner.newCard(key, now, zone) else Leitner.place(key, box, now, zone)
            }
        }
        val tested = results.keys.filter { !it.placementOnly }.groupBy { it.lessonId }
        for ((lessonId, items) in tested) {
            if (items.any { results[it] != Grade.CORRECT }) continue
            val lesson = lessons.firstOrNull { it.id == lessonId } ?: continue
            for (item in lesson.items) {
                if (item.placementOnly || item in results || item.id in introducedItemIds) continue
                for (d in Direction.entries) {
                    cards += CardState(CardKey(item.id, d), 2, Leitner.dayStart(now, zone, 0), introducedAt = now.toEpochMilli())
                }
            }
        }
        return cards
    }

    private fun boxFor(item: StudyItem, grade: Grade) = when (grade) {
        Grade.CORRECT -> 4
        Grade.TYPO -> 2
        Grade.WRONG -> if (item.placementOnly) 1 else 0
    }
}
