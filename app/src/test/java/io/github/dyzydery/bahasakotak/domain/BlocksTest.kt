package io.github.dyzydery.bahasakotak.domain

import io.github.dyzydery.bahasakotak.RepoFiles
import io.github.dyzydery.bahasakotak.domain.Direction.ID_PL
import io.github.dyzydery.bahasakotak.domain.Direction.PL_ID
import io.github.dyzydery.bahasakotak.io.pack.PackReadResult
import io.github.dyzydery.bahasakotak.io.pack.PackReader
import io.github.dyzydery.bahasakotak.io.pack.items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BlocksTest {
    private val sentence = item("t.s").copy(
        kind = ItemKind.SENTENCE,
        idText = "Saya bekerja di Jakarta.",
        plText = "Pracuję w Dżakarcie.",
        acceptedPl = listOf("W Dżakarcie pracuję."),
    )

    @Test
    fun `answers of three or more words use blocks`() {
        assertEquals(ExerciseType.BLOCKS, Blocks.exerciseFor(sentence, PL_ID))
        assertEquals(ExerciseType.BLOCKS, Blocks.exerciseFor(sentence, ID_PL))
        val phrase = item("t.p").copy(idText = "hari ini", plText = "pozwól(cie), że się przedstawię")
        assertEquals(ExerciseType.TYPED, Blocks.exerciseFor(phrase, PL_ID))
        assertEquals(ExerciseType.BLOCKS, Blocks.exerciseFor(phrase, ID_PL))
    }

    @Test
    fun `tokens drop hints and punctuation and lowercase only the first word`() {
        assertEquals(listOf("saya", "bekerja", "di", "Jakarta"), Blocks.tokens("Saya bekerja di Jakarta."))
        assertEquals(listOf("pozwól", "że", "się", "przedstawię"), Blocks.tokens("pozwól(cie), że się przedstawię"))
        assertEquals(listOf("santai", "aja", "nggak", "apa-apa"), Blocks.tokens("Santai aja, nggak apa-apa."))
    }

    @Test
    fun `distractor count follows difficulty`() {
        val pool = listOf("Kami menunggu laporan operator besok")
        val expected = mapOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 5)
        for ((difficulty, distractors) in expected) {
            val tiles = Blocks.tiles(sentence, PL_ID, difficulty, pool, Random(difficulty))
            assertEquals(4 + distractors, tiles.size, "difficulty $difficulty")
        }
    }

    @Test
    fun `item distractors are used first and never duplicate answer words`() {
        val withOwn = sentence.copy(distractorsId = listOf("kerja", "Saya"))
        val tiles = Blocks.tiles(withOwn, PL_ID, difficulty = 1, pool = listOf("besok"), random = Random(1))
        // "Saya" duplicates an answer word, so the second distractor comes from the pool.
        assertEquals(listOf("bekerja", "di", "Jakarta", "kerja", "saya", "besok").sorted(), tiles.sorted())
    }

    @Test
    fun `tiles still work when the pool runs dry`() {
        val tiles = Blocks.tiles(sentence, PL_ID, difficulty = 5, pool = emptyList(), random = Random(1))
        assertEquals(4, tiles.size)
    }

    @Test
    fun `typo in blocks counts as wrong`() {
        val task = Task.Ask(sentence, ID_PL, ExerciseType.BLOCKS)
        assertEquals(Grade.WRONG, grade(task, "pracuje w dzakarcieee").grade)
        assertEquals(Grade.CORRECT, grade(task, "w dżakarcie pracuję").grade)
    }

    @Test
    fun `every starter pack answer can be assembled from its own tiles`() {
        val pack = (PackReader(RepoFiles.packSchema).read(RepoFiles.starterPack) as PackReadResult.Valid).pack
        for (dto in pack.items) {
            val studyItem = StudyItem(
                dto.id, "l", dto.kind, dto.idText, dto.plText, dto.register, dto.acceptedId, dto.acceptedPl,
            )
            for (d in Direction.entries) {
                val built = Blocks.tokens(studyItem.answers(d).first()).joinToString(" ")
                val result = grade(Task.Ask(studyItem, d, ExerciseType.BLOCKS), built)
                assertEquals(Grade.CORRECT, result.grade, "${dto.id} $d: '$built'")
            }
        }
    }

    @Test
    fun `planner turns long answers into blocks with tiles`() {
        val lesson = LessonInfo("t.l1", 0, 0, difficulty = 3, grammarNote = null, grammarNoteSeen = false, items = listOf(sentence, item("t.w")))
        val tasks = SessionPlanner.plan(
            PlannerInput(NOW.toEpochMilli(), emptyMap(), lesson.items.associateBy { it.id }, listOf(lesson), taskLimit = 20),
            Random(3),
        )
        val sentenceAsks = tasks.filterIsInstance<Task.Ask>().filter { it.item == sentence }
        assertEquals(2, sentenceAsks.size)
        assertTrue(sentenceAsks.all { it.exerciseType == ExerciseType.BLOCKS && it.tiles.isNotEmpty() })
        val wordAsks = tasks.filterIsInstance<Task.Ask>().filter { it.item.id == "t.w" }
        assertTrue(wordAsks.all { it.exerciseType == ExerciseType.TYPED && it.tiles.isEmpty() })
    }
}
