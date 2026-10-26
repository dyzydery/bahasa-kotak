package io.github.dyzydery.bahasakotak.io.pack

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.json.Json

sealed interface PackReadResult {
    data class Valid(val pack: PackDto) : PackReadResult
    data class Invalid(val errors: List<String>) : PackReadResult
}

/**
 * Parses a pack file: JSON Schema validation first (the same schema Claude is given),
 * then checks the schema cannot express, such as ID uniqueness across the whole pack.
 */
class PackReader(schemaJson: String) {
    private val schema: JsonSchema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaJson)

    private val json = Json { ignoreUnknownKeys = false }

    fun read(text: String): PackReadResult {
        val schemaErrors = try {
            schema.validate(text, InputFormat.JSON).map { it.message }
        } catch (e: Exception) {
            return PackReadResult.Invalid(listOf("To nie jest poprawny plik JSON: ${e.message}"))
        }
        if (schemaErrors.isNotEmpty()) {
            return PackReadResult.Invalid(schemaErrors.sorted().take(MAX_ERRORS))
        }
        val pack = try {
            json.decodeFromString(PackDto.serializer(), text)
        } catch (e: Exception) {
            return PackReadResult.Invalid(listOf("Nie udało się odczytać paczki: ${e.message}"))
        }
        val semanticErrors = checkUniqueIds(pack)
        return if (semanticErrors.isEmpty()) PackReadResult.Valid(pack)
        else PackReadResult.Invalid(semanticErrors.take(MAX_ERRORS))
    }

    private fun checkUniqueIds(pack: PackDto): List<String> {
        val groups = mapOf(
            "temat" to pack.topics.map { it.id },
            "lekcja" to pack.lessons.map { it.id },
            "notka" to pack.lessons.mapNotNull { it.grammarNote?.id },
            "element" to pack.items.map { it.id },
        )
        return groups.flatMap { (label, ids) ->
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                .map { "Zduplikowane ID ($label): $it" }
        }
    }

    private companion object {
        const val MAX_ERRORS = 20
    }
}
