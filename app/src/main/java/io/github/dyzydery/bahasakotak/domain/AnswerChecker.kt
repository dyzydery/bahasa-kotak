package io.github.dyzydery.bahasakotak.domain

import java.text.Normalizer

data class CheckResult(
    val grade: Grade,
    /** The accepted answer closest to what the user typed, in its original spelling. */
    val expected: String,
)

/**
 * Grades a typed answer against every accepted variant. Case, punctuation, spaces, hyphens and Polish
 * diacritics are ignored. A small edit distance, scaled by length, counts as a typo ("prawie dobrze").
 */
object AnswerChecker {
    fun check(answer: String, variants: List<String>): CheckResult {
        require(variants.isNotEmpty())
        val expanded = variants.flatMap(::expandVariant).distinct()
        val given = normalize(answer)
        if (given.isEmpty()) return CheckResult(Grade.WRONG, variants.first())

        // Spaces are compared loosely: "meetingnya" = "meeting-nya", "kirakira" = "kira-kira".
        val compactGiven = given.replace(" ", "")
        expanded.firstOrNull { normalize(it).replace(" ", "") == compactGiven }?.let { return CheckResult(Grade.CORRECT, it) }

        val (closest, distance) = expanded
            .map { it to damerauLevenshtein(given, normalize(it)) }
            .minBy { it.second }
        val grade = if (distance <= typoThreshold(normalize(closest).length)) Grade.TYPO else Grade.WRONG
        return CheckResult(grade, if (grade == Grade.TYPO) closest else variants.first())
    }

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace('ł', 'l')
            .replace('-', ' ')
            .replace(NON_WORD, "")
            .replace(WHITESPACE, " ")
            .trim()
    }

    fun typoThreshold(length: Int): Int = when {
        length <= 3 -> 0
        length <= 7 -> 1
        length <= 14 -> 2
        else -> length / 7
    }

    /**
     * Pack texts carry hints a learner shouldn't have to type: "dzień dobry (rano)" also accepts
     * "dzień dobry", and "Pan / Pani" accepts each side on its own.
     */
    internal fun expandVariant(variant: String): List<String> {
        val withoutHints = stripHints(variant)
        val alternatives = variant.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
        return (listOf(variant, withoutHints) + if (alternatives.size > 1) alternatives else emptyList())
            .filter { normalize(it).isNotEmpty() }
    }

    /** Removes parenthesized hints: "pozwól(cie), że się przedstawię" → "pozwól, że się przedstawię". */
    fun stripHints(text: String): String = text.replace(PARENTHESIZED, "").replace(WHITESPACE, " ").trim()

    /** Optimal string alignment distance: Levenshtein plus adjacent transpositions. */
    internal fun damerauLevenshtein(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N} ]")
    private val WHITESPACE = Regex("\\s+")
    private val PARENTHESIZED = Regex("\\([^)]*\\)")
}
