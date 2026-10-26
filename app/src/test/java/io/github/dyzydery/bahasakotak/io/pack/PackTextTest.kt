package io.github.dyzydery.bahasakotak.io.pack

import io.github.dyzydery.bahasakotak.RepoFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackTextTest {
    private val pack = """{"schemaVersion": 1, "packId": "loop-001"}"""

    @Test
    fun `bare json is returned as is`() {
        assertEquals(pack, PackText.extract("  $pack\n"))
    }

    @Test
    fun `json is taken from a fenced block in a full reply`() {
        val reply = "## Raport\n\n- Mylisz *sudah* i *belum*.\n\n```json\n$pack\n```\n\nPowodzenia!"
        assertEquals(pack, PackText.extract(reply))
    }

    @Test
    fun `the pack block wins over other fenced blocks`() {
        val reply = "Przykład:\n```\nsaya → aku\n```\nPaczka:\n```json\n$pack\n```"
        assertEquals(pack, PackText.extract(reply))
    }

    @Test
    fun `unfenced json inside prose is cut out`() {
        assertEquals(pack, PackText.extract("Oto paczka: $pack Daj znać."))
    }

    @Test
    fun `a pasted reply with the starter pack imports cleanly`() {
        val reply = "Raport…\n\n```json\n${RepoFiles.starterPack.trim()}\n```\n"
        val result = PackReader(RepoFiles.packSchema).read(PackText.extract(reply))
        assertTrue(result is PackReadResult.Valid, result.toString())
    }
}
