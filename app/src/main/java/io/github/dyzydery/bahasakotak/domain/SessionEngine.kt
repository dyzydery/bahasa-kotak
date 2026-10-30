package io.github.dyzydery.bahasakotak.domain

import java.time.Instant
import java.time.ZoneId

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
    /** Graded tasks in this session so far: the planned ones plus every retry queued after a wrong answer. */
    val totalQuestions: Int
    /** Graded answers given, retries included; equals [totalQuestions] once the queue is empty. */
    val answeredQuestions: Int
    /** Grades of first answers only (retries excluded). */
    val grades: Map<Grade, Int>

    /** Leitner box the card of [task] is in right now, or null when the runner has no cards (placement test). */
    fun boxOf(task: Task.Ask): Int?

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
 * The first answer to a card moves it in the Leitner boxes. A wrong first answer re-queues the task once, at the
 * end of the session, as a retry that is logged but never moves the card again; a wrong retry is not re-queued.
 * So each card comes up at most twice and the session always ends.
 */
class SessionEngine(
    tasks: List<Task>,
    cards: Map<CardKey, CardState>,
    private val zone: ZoneId,
) : SessionRunner {
    private val queue = ArrayDeque(tasks)
    private val cards = cards.toMutableMap()
    private val retried = mutableSetOf<CardKey>()

    override var totalQuestions = tasks.count { it is Task.Ask }
        private set
    override var answeredQuestions = 0
        private set
    override val grades = mutableMapOf<Grade, Int>()

    override val current: Task? get() = queue.firstOrNull()

    override fun boxOf(task: Task.Ask): Int? = cards[task.key]?.box

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

        answeredQuestions++
        if (!task.retry) grades.merge(check.grade, 1, Int::plus)
        if (check.grade == Grade.WRONG && !task.retry && retried.add(task.key)) {
            queue.addLast(task.copy(retry = true))
            totalQuestions++
        }
        return AnswerOutcome(task, answer, check, before.box, after, now, durationMs)
    }

    override fun advance() {
        queue.removeFirstOrNull()
    }
}
