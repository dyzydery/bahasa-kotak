package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Everything one answer produced, for the caller to persist. */
data class AnswerOutcome(
    val task: Task.Ask,
    val userAnswer: String,
    val check: CheckResult,
    val boxBefore: Int,
    val cardAfter: CardState,
    val answeredAt: Instant,
    val durationMs: Long?,
)

/** A running queue of tasks, shared by regular sessions and the placement test. */
interface SessionRunner {
    val current: Task?
    /** Graded tasks planned at the start; retries are extra and don't count. */
    val plannedQuestions: Int
    val answeredQuestions: Int
    val grades: Map<Grade, Int>

    /** Cards to create when an intro card is shown. */
    fun introduce(item: StudyItem, now: Instant): List<CardState>
    fun submit(answer: String, now: Instant, durationMs: Long?): AnswerOutcome
    fun advance()
}

/** Grades an answer; word blocks have no "almost right": a wrong tile is a wrong answer. */
fun grade(task: Task.Ask, answer: String): CheckResult {
    val variants = task.item.answers(task.direction)
    val check = AnswerChecker.check(answer, variants)
    return if (task.exerciseType == ExerciseType.BLOCKS && check.grade == Grade.TYPO) {
        CheckResult(Grade.WRONG, variants.first())
    } else check
}

/**
 * Runs one session's task queue. Pure: the caller persists the cards and outcomes it reports.
 * The first answer to a card moves it in the Leitner boxes; a wrong answer re-queues the task 3–5 positions
 * later as a retry, which is logged but never moves the card again.
 */
class SessionEngine(
    tasks: List<Task>,
    cards: Map<CardKey, CardState>,
    private val zone: ZoneId,
    private val random: Random = Random.Default,
) : SessionRunner {
    private val queue = ArrayDeque(tasks)
    private val cards = cards.toMutableMap()
    private val retries = mutableMapOf<CardKey, Int>()

    override val plannedQuestions = tasks.count { it is Task.Ask }
    override var answeredQuestions = 0
        private set
    override val grades = mutableMapOf<Grade, Int>()

    override val current: Task? get() = queue.firstOrNull()

    override fun introduce(item: StudyItem, now: Instant): List<CardState> =
        Direction.entries.map { d ->
            val key = CardKey(item.id, d)
            cards.getOrPut(key) { Leitner.newCard(key, now, zone) }
        }

    override fun submit(answer: String, now: Instant, durationMs: Long?): AnswerOutcome {
        val task = current as? Task.Ask ?: error("Current task is not a question: $current")
        val check = grade(task, answer)
        val before = cards[task.key] ?: Leitner.newCard(task.key, now, zone)
        val after = if (task.retry) before else Leitner.review(before, check.grade, now, zone)
        cards[task.key] = after

        if (!task.retry) {
            answeredQuestions++
            grades.merge(check.grade, 1, Int::plus)
        }
        if (check.grade == Grade.WRONG) requeue(task)
        return AnswerOutcome(task, answer, check, before.box, after, now, durationMs)
    }

    override fun advance() {
        queue.removeFirstOrNull()
    }

    private fun requeue(task: Task.Ask) {
        val count = retries.merge(task.key, 1, Int::plus)!!
        if (count > MAX_RETRIES) return
        // +1 because the current task is still at the head of the queue until advance().
        val position = minOf(random.nextInt(3, 6) + 1, queue.size)
        queue.add(position, task.copy(retry = true))
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
