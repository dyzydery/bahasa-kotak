package io.github.dyzydery.bahasakotak.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AnswerCheckerTest {
    private fun grade(answer: String, vararg variants: String) = AnswerChecker.check(answer, variants.toList()).grade

    @Nested
    inner class Normalization {
        @ParameterizedTest
        @CsvSource(
            "zolw, żółw",
            "ZÓŁW, żółw",
            "'  dzien   dobry ', dzień dobry",
            "dzien dobry!, 'Dzień dobry.'",
            "kira kira, kira-kira",
            "sama sama, sama-sama",
            "jak sie masz, jak się masz?",
            "zrodlo, źródło",
            "meetingnya, meeting-nya",
            "kirakira, kira-kira",
            "di follow up, di-follow up",
        )
        fun `equivalent forms are correct`(answer: String, variant: String) {
            assertEquals(Grade.CORRECT, grade(answer, variant))
        }

        @Test
        fun `normalize strips case, diacritics, punctuation and spacing`() {
            assertEquals("pozwolcie ze sie przedstawie", AnswerChecker.normalize("Pozwólcie, że się  przedstawię!"))
        }
    }

    @Nested
    inner class Variants {
        @Test
        fun `any accepted variant is correct and reported as expected`() {
            val result = AnswerChecker.check("zebranie", listOf("spotkanie", "zebranie", "narada"))
            assertEquals(Grade.CORRECT, result.grade)
            assertEquals("zebranie", result.expected)
        }

        @Test
        fun `parenthesized hints are optional`() {
            assertEquals(Grade.CORRECT, grade("dzień dobry", "dzień dobry (rano)"))
            assertEquals(Grade.CORRECT, grade("dzień dobry (rano)", "dzień dobry (rano)"))
            assertEquals(Grade.CORRECT, grade("pozwól, że się przedstawię", "pozwól(cie), że się przedstawię"))
        }

        @Test
        fun `slash alternatives are accepted on their own`() {
            assertEquals(Grade.CORRECT, grade("pani", "Pan / Pani"))
            assertEquals(Grade.CORRECT, grade("pan", "Pan / Pani"))
        }

        @Test
        fun `wrong answer reports the canonical variant`() {
            val result = AnswerChecker.check("kot", listOf("spotkanie", "zebranie"))
            assertEquals(Grade.WRONG, result.grade)
            assertEquals("spotkanie", result.expected)
        }

        @Test
        fun `empty answer is wrong`() {
            assertEquals(Grade.WRONG, grade("   ", "ya"))
        }
    }

    @Nested
    inner class Typos {
        @Test
        fun `single substitution in a medium word is a typo`() {
            val result = AnswerChecker.check("rapar", listOf("rapat"))
            assertEquals(Grade.TYPO, result.grade)
            assertEquals("rapat", result.expected)
        }

        @Test
        fun `adjacent transposition counts as one edit`() {
            assertEquals(Grade.TYPO, grade("jaringna", "jaringan"))
            assertEquals(1, AnswerChecker.damerauLevenshtein("ab", "ba"))
        }

        @Test
        fun `short words must be exact`() {
            assertEquals(Grade.WRONG, grade("itu", "ini"))
            assertEquals(Grade.WRONG, grade("ta", "ya"))
        }

        @Test
        fun `threshold grows with length`() {
            assertEquals(Grade.TYPO, grade("prezentaca", "prezentacja"))   // 11 chars, 1 edit
            assertEquals(Grade.TYPO, grade("prezentcaa", "prezentacja"))   // 2 edits allowed at 8..14
            assertEquals(Grade.WRONG, grade("bekrj", "bekerja"))           // 7 chars, 2 edits > 1
        }

        @Test
        fun `thresholds match the spec`() {
            assertEquals(listOf(0, 0, 1, 1, 2, 2, 2, 3), listOf(1, 3, 4, 7, 8, 14, 20, 21).map(AnswerChecker::typoThreshold))
        }

        @Test
        fun `typo is measured against the closest variant`() {
            val result = AnswerChecker.check("zebrnie", listOf("spotkanie", "zebranie"))
            assertEquals(Grade.TYPO, result.grade)
            assertEquals("zebranie", result.expected)
        }

        @Test
        fun `typo ignores polish diacritics too`() {
            assertEquals(Grade.TYPO, grade("zaacznik", "załącznik"))
        }
    }
}
