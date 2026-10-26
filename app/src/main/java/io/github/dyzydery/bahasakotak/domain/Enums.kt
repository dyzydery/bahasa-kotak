package io.github.dyzydery.bahasakotak.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ItemKind {
    @SerialName("word") WORD,
    @SerialName("phrase") PHRASE,
    @SerialName("sentence") SENTENCE,
}

/**
 * formal = bahasa baku; office = Jakarta workplace speech (Indonesian–English mix, chat abbreviations,
 * particles); colloquial = street gaul (gue/lo); neutral = fine anywhere.
 */
@Serializable
enum class Register {
    @SerialName("formal") FORMAL,
    @SerialName("office") OFFICE,
    @SerialName("colloquial") COLLOQUIAL,
    @SerialName("neutral") NEUTRAL,
}

@Serializable
enum class Direction {
    @SerialName("pl_id") PL_ID,
    @SerialName("id_pl") ID_PL,
}

@Serializable
enum class ExerciseType {
    @SerialName("typed") TYPED,
    @SerialName("blocks") BLOCKS,
    @SerialName("placement") PLACEMENT,
}

@Serializable
enum class Grade {
    @SerialName("correct") CORRECT,
    @SerialName("typo") TYPO,
    @SerialName("wrong") WRONG,
}
