package io.github.dyzydery.bahasakotak.io.export

import io.github.dyzydery.bahasakotak.RepoFiles
import io.github.dyzydery.bahasakotak.io.pack.PackReadResult
import io.github.dyzydery.bahasakotak.io.pack.PackReader
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Keeps docs/ANALYSIS_PROMPT.md in sync with the pack schema: its example must be importable. */
class AnalysisPromptTest {
    @Test
    fun `example pack in the prompt is valid`() {
        val example = Regex("```json\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
            .findAll(RepoFiles.analysisPrompt).single().groupValues[1]
        val result = PackReader(RepoFiles.packSchema).read(example)
        assertTrue(result is PackReadResult.Valid, result.toString())
    }
}
