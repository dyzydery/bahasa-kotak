package io.github.dyzydery.bahasakotak.domain

import kotlin.random.Random

/**
 * Word-block exercises: answers of three or more words are assembled from tiles instead of typed,
 * which spares typing long phrases on a phone. See docs/SPEC.md §7.
 */
object Blocks {
    const val MIN_WORDS = 3

    fun exerciseFor(item: StudyItem, direction: Direction): ExerciseType =
        if (tokens(item.answers(direction).first()).size >= MIN_WORDS) ExerciseType.BLOCKS else ExerciseType.TYPED

    /** Lesson difficulty 1..5 gives 2..5 distractor tiles; even easy lessons need some, or blocks solve themselves. */
    fun distractorCount(difficulty: Int) = (difficulty + 1).coerceIn(2, 5)

    /**
     * Tiles for one question: the canonical answer's words plus distractors, shuffled. Distractors come from
     * the item's own list first, then from [pool] in order (texts in the answer language, nearest material
     * first; the caller shuffles within each tier).
     */
    fun tiles(
        item: StudyItem,
        direction: Direction,
        difficulty: Int,
        pool: List<String>,
        random: Random,
    ): List<String> {
        val answer = tokens(item.answers(direction).first())
        val taken = answer.mapTo(HashSet()) { AnswerChecker.normalize(it) }
        val distractors = mutableListOf<String>()
        val candidates = item.distractors(direction).flatMap(::tokens) +
            pool.flatMap(::tokens).distinct()
        for (candidate in candidates) {
            if (distractors.size == distractorCount(difficulty)) break
            if (taken.add(AnswerChecker.normalize(candidate))) distractors += candidate
        }
        return (answer + distractors).shuffled(random)
    }

    /**
     * Splits a text into tiles: hints dropped, surrounding punctuation trimmed, and the first word lowercased
     * so capitalization doesn't give away the sentence start. Proper nouns elsewhere keep their case.
     */
    fun tokens(text: String): List<String> =
        AnswerChecker.stripHints(text)
            .split(' ')
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, word -> if (i == 0) word.replaceFirstChar { it.lowercaseChar() } else word }
}
