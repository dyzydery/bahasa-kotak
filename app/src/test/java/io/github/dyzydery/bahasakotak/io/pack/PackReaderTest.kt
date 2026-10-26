package io.github.dyzydery.bahasakotak.io.pack

import io.github.dyzydery.bahasakotak.RepoFiles
import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Register
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackReaderTest {
    private val reader = PackReader(RepoFiles.packSchema)

    @Test
    fun `starter pack is valid`() {
        val result = reader.read(RepoFiles.starterPack)
        val pack = assertInstanceOf(PackReadResult.Valid::class.java, result, result.toString()).pack
        assertEquals("starter", pack.packId)
        assertTrue(pack.items.size >= 100)
        assertTrue(pack.items.any { it.kind == ItemKind.SENTENCE })
        assertTrue(pack.items.any { it.register == Register.COLLOQUIAL })
        assertTrue(pack.items.any { it.placementOnly })
    }

    @Test
    fun `item fields are mapped`() {
        val pack = (reader.read(minimalPack()) as PackReadResult.Valid).pack
        val item = pack.items.single()
        assertEquals("rapat", item.idText)
        assertEquals("spotkanie", item.plText)
        assertEquals(listOf("zebranie"), item.acceptedPl)
        assertEquals(Register.FORMAL, item.register)
        assertEquals(false, item.placementOnly)
    }

    @Test
    fun `malformed json is rejected`() {
        assertInvalid(reader.read("{ not json"))
    }

    @Test
    fun `missing required field is rejected`() {
        assertInvalid(reader.read(minimalPack(item = { it - "register" })))
    }

    @Test
    fun `unknown register is rejected`() {
        assertInvalid(reader.read(minimalPack(item = { it + ("register" to JsonPrimitive("slang")) })))
    }

    @Test
    fun `unknown property is rejected`() {
        assertInvalid(reader.read(minimalPack(item = { it + ("audio" to JsonPrimitive("x.mp3")) })))
    }

    @Test
    fun `unsupported schema version is rejected`() {
        assertInvalid(reader.read(minimalPack(root = { it + ("schemaVersion" to JsonPrimitive(2)) })))
    }

    @Test
    fun `invalid id format is rejected`() {
        assertInvalid(reader.read(minimalPack(item = { it + ("id" to JsonPrimitive("Has Spaces")) })))
    }

    @Test
    fun `duplicate item ids are rejected`() {
        val text = minimalPack(lesson = { lesson ->
            val items = lesson.getValue("items").jsonArray
            lesson + ("items" to JsonArray(items + items))
        })
        val errors = assertInvalid(reader.read(text))
        assertTrue(errors.any { "meet.rapat" in it }, errors.toString())
    }

    private fun assertInvalid(result: PackReadResult): List<String> {
        val invalid = assertInstanceOf(PackReadResult.Invalid::class.java, result)
        assertTrue(invalid.errors.isNotEmpty())
        return invalid.errors
    }

    companion object {
        private val BASE = """
            {
              "schemaVersion": 1, "packId": "test", "packVersion": 1, "title": "Test",
              "createdAt": "2026-10-26T12:00:00Z",
              "topics": [{
                "id": "meet", "title": "Spotkania", "order": 0,
                "lessons": [{
                  "id": "meet.l1", "title": "Lekcja", "order": 0, "difficulty": 1,
                  "items": [{
                    "id": "meet.rapat", "kind": "word", "id_text": "rapat", "pl_text": "spotkanie",
                    "register": "formal", "acceptedPl": ["zebranie"]
                  }]
                }]
              }]
            }
        """.trimIndent()

        private operator fun JsonObject.plus(pair: Pair<String, JsonElement>) = JsonObject(this.toMap() + pair)
        private operator fun JsonObject.minus(key: String) = JsonObject(this.toMap() - key)

        /** BASE with optional edits applied to the root, the first lesson, or the first item. */
        fun minimalPack(
            root: (JsonObject) -> JsonObject = { it },
            lesson: (JsonObject) -> JsonObject = { it },
            item: (JsonObject) -> JsonObject = { it },
        ): String {
            val base = Json.parseToJsonElement(BASE).jsonObject
            val topic = base.getValue("topics").jsonArray[0].jsonObject
            val l = topic.getValue("lessons").jsonArray[0].jsonObject
            val i = l.getValue("items").jsonArray[0].jsonObject
            val newLesson = lesson(l + ("items" to JsonArray(listOf(item(i)))))
            val newTopic = topic + ("lessons" to JsonArray(listOf(newLesson)))
            return root(base + ("topics" to JsonArray(listOf(newTopic)))).toString()
        }
    }
}
