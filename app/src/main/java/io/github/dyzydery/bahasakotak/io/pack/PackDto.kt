package io.github.dyzydery.bahasakotak.io.pack

import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Register
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `schemas/pack.schema.json` (schemaVersion 1). */
@Serializable
data class PackDto(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: Int,
    val title: String,
    val description: String? = null,
    val createdAt: String,
    val basedOnExport: String? = null,
    /** Claude's report for the learner, shown when importing; not stored. */
    val feedback: String? = null,
    val topics: List<TopicDto>,
)

@Serializable
data class TopicDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val order: Int,
    val lessons: List<LessonDto>,
)

@Serializable
data class LessonDto(
    val id: String,
    val title: String,
    val order: Int,
    val difficulty: Int,
    val grammarNote: GrammarNoteDto? = null,
    val items: List<ItemDto>,
)

@Serializable
data class GrammarNoteDto(
    val id: String,
    val title: String,
    val body: String,
)

@Serializable
data class ItemDto(
    val id: String,
    val kind: ItemKind,
    @SerialName("id_text") val idText: String,
    @SerialName("pl_text") val plText: String,
    val register: Register,
    val acceptedId: List<String> = emptyList(),
    val acceptedPl: List<String> = emptyList(),
    val distractorsId: List<String> = emptyList(),
    val distractorsPl: List<String> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val placementOnly: Boolean = false,
)

val PackDto.lessons: List<LessonDto> get() = topics.flatMap { it.lessons }
val PackDto.items: List<ItemDto> get() = lessons.flatMap { it.items }
