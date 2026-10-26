package io.github.dyzydery.bahasakotak.domain

import kotlin.random.Random

data class PlannerInput(
    val nowMillis: Long,
    /** Every existing card, keyed by (item, direction). */
    val cards: Map<CardKey, CardState>,
    /** Active items by ID, so due cards can be resolved to their content. */
    val items: Map<String, StudyItem>,
    /** Active lessons in topic, then lesson order. */
    val lessons: List<LessonInfo>,
    val taskLimit: Int,
    val maxNewItems: Int = SessionPlanner.MAX_NEW_ITEMS,
)

/** Builds the task list for one ~5-minute session. See docs/SPEC.md §6. */
object SessionPlanner {
    const val MAX_NEW_ITEMS = 5
    const val DEFAULT_TASK_LIMIT = 20
    private const val TARGET_SESSION_MS = 300_000L
    private const val MIN_SAMPLES = 20

    fun plan(input: PlannerInput, random: Random = Random.Default): List<Task> {
        val reviews = pickReviews(input)
        val newSlots = (input.taskLimit - reviews.size) / 2 // each new item brings two graded tasks
        val newItems = pickNewItems(input).take(minOf(newSlots, input.maxNewItems).coerceAtLeast(0))
        return interleave(reviews.shuffled(random), newItems, input.lessons, random)
            .map { if (it is Task.Ask) withExercise(it, input, random) else it }
    }

    /** Long answers become word blocks; tiles draw distractors from the same lesson first, then everything. */
    private fun withExercise(task: Task.Ask, input: PlannerInput, random: Random): Task.Ask {
        if (Blocks.exerciseFor(task.item, task.direction) != ExerciseType.BLOCKS) return task
        val lesson = input.lessons.firstOrNull { it.id == task.item.lessonId }
        val answerText = { it: StudyItem -> it.answers(task.direction).first() }
        val pool = lesson?.items.orEmpty().filter { it.id != task.item.id }.map(answerText).shuffled(random) +
            input.items.values.filter { it.lessonId != task.item.lessonId }.map(answerText).shuffled(random)
        val tiles = Blocks.tiles(task.item, task.direction, lesson?.difficulty ?: 1, pool, random)
        return task.copy(exerciseType = ExerciseType.BLOCKS, tiles = tiles)
    }

    /** Task count calibrated so a session takes about five minutes at the user's recent pace. */
    fun taskLimit(recentDurationsMs: List<Long>): Int {
        if (recentDurationsMs.size < MIN_SAMPLES) return DEFAULT_TASK_LIMIT
        val sorted = recentDurationsMs.sorted()
        val median = sorted[sorted.size / 2].coerceAtLeast(1)
        return (TARGET_SESSION_MS / median).toInt().coerceIn(10, 40)
    }

    /** Due cards, oldest first and mastered last, alternating directions so both stay balanced. */
    private fun pickReviews(input: PlannerInput): List<Task.Ask> {
        val due = input.cards.values
            .filter { it.dueAt <= input.nowMillis && it.key.itemId in input.items }
            .sortedWith(compareBy({ it.box == Leitner.MASTERED }, { it.dueAt }))
        val byDirection = Direction.entries.map { d -> ArrayDeque(due.filter { it.key.direction == d }) }
        val picked = mutableListOf<CardState>()
        var turn = 0
        while (picked.size < input.taskLimit && byDirection.any { it.isNotEmpty() }) {
            val queue = byDirection[turn % 2].takeIf { it.isNotEmpty() } ?: byDirection[(turn + 1) % 2]
            picked += queue.removeFirst()
            turn++
        }
        return picked.map { Task.Ask(input.items.getValue(it.key.itemId), it.key.direction) }
    }

    /**
     * New material comes from the first unfinished lesson of each topic, lowest topic first. A lesson is
     * finished once every card of its items is in box 2 or higher; only then does the next one open.
     */
    internal fun pickNewItems(input: PlannerInput): List<StudyItem> {
        val introduced = input.cards.keys.mapTo(HashSet()) { it.itemId }
        return input.lessons
            .groupBy { it.topicOrder }
            .toSortedMap()
            .values
            .mapNotNull { lessons -> lessons.sortedBy { it.order }.firstOrNull { !isFinished(it, input.cards) } }
            .flatMap { lesson -> lesson.items.filter { !it.placementOnly && it.id !in introduced } }
    }

    internal fun isFinished(lesson: LessonInfo, cards: Map<CardKey, CardState>): Boolean =
        lesson.items.filter { !it.placementOnly }.all { item ->
            Direction.entries.all { d -> (cards[CardKey(item.id, d)]?.box ?: 0) >= 2 }
        }

    /**
     * Order: grammar note (once per lesson), then each new item's intro, followed a couple of tasks later
     * by its ID→PL question; reviews fill the gaps; PL→ID questions for new items close the session.
     */
    private fun interleave(
        reviews: List<Task.Ask>,
        newItems: List<StudyItem>,
        lessons: List<LessonInfo>,
        random: Random,
    ): List<Task> {
        val lessonById = lessons.associateBy { it.id }
        val out = mutableListOf<Task>()
        val pendingReviews = ArrayDeque(reviews)
        val notesShown = mutableSetOf<String>()
        var pendingAsk: Task.Ask? = null
        for (item in newItems) {
            val lesson = lessonById[item.lessonId]
            if (lesson?.grammarNote != null && !lesson.grammarNoteSeen && notesShown.add(lesson.id)) {
                out += Task.Note(lesson.id, lesson.grammarNote)
            }
            out += Task.Intro(item)
            pendingAsk?.let { out += it }
            repeat(2) { pendingReviews.removeFirstOrNull()?.let { out += it } }
            pendingAsk = Task.Ask(item, Direction.ID_PL)
        }
        pendingAsk?.let { out += it }
        out += pendingReviews
        out += newItems.shuffled(random).map { Task.Ask(it, Direction.PL_ID) }
        return out
    }
}
