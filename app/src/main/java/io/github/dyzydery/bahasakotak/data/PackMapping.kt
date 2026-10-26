package io.github.dyzydery.bahasakotak.data

import io.github.dyzydery.bahasakotak.io.pack.GrammarNoteDto
import io.github.dyzydery.bahasakotak.io.pack.ItemDto
import io.github.dyzydery.bahasakotak.io.pack.LessonDto
import io.github.dyzydery.bahasakotak.io.pack.PackDto
import io.github.dyzydery.bahasakotak.io.pack.TopicDto
import java.time.Instant

/** Content rows written by importing one pack. */
data class PackRows(
    val pack: PackEntity,
    val topics: List<TopicEntity>,
    val lessons: List<LessonEntity>,
    val items: List<ItemEntity>,
)

fun PackDto.toRows(importedAt: Long) = PackRows(
    pack = PackEntity(packId, packVersion, title, description, importedAt),
    topics = topics.map { t -> TopicEntity(t.id, packId, t.title, t.description, t.order) },
    lessons = topics.flatMap { t ->
        t.lessons.map { l ->
            LessonEntity(
                id = l.id, topicId = t.id, packId = packId, title = l.title, order = l.order,
                difficulty = l.difficulty, grammarNoteId = l.grammarNote?.id,
                grammarNoteTitle = l.grammarNote?.title, grammarNoteBody = l.grammarNote?.body,
            )
        }
    },
    items = topics.flatMap { t ->
        t.lessons.flatMap { l ->
            l.items.mapIndexed { index, i ->
                ItemEntity(
                    id = i.id, lessonId = l.id, packId = packId, order = index, kind = i.kind,
                    idText = i.idText, plText = i.plText, register = i.register,
                    acceptedId = i.acceptedId, acceptedPl = i.acceptedPl,
                    distractorsId = i.distractorsId, distractorsPl = i.distractorsPl,
                    notes = i.notes, tags = i.tags, placementOnly = i.placementOnly,
                )
            }
        }
    },
)

/**
 * Rebuilds each installed pack, in pack format, from its active rows, so the export shows Claude exactly
 * what the app holds and Claude can return a corrected new version of a pack. Grouping follows the items'
 * own pack, so a lesson or topic later taken over by another pack still nests the items it contains.
 */
fun reconstructPacks(
    packs: List<PackEntity>,
    topics: List<TopicEntity>,
    lessons: List<LessonEntity>,
    items: List<ItemEntity>,
): List<PackDto> {
    val topicById = topics.associateBy { it.id }
    val lessonById = lessons.associateBy { it.id }
    return packs.mapNotNull { pack ->
        val packItems = items.filter { it.packId == pack.id && !it.retired }
        if (packItems.isEmpty()) return@mapNotNull null
        val byLesson = packItems.groupBy { it.lessonId }
        val packLessons = byLesson.keys.mapNotNull { lessonById[it] }
        val topicDtos = packLessons.groupBy { it.topicId }.mapNotNull { (topicId, topicLessons) ->
            val topic = topicById[topicId] ?: return@mapNotNull null
            TopicDto(
                id = topic.id, title = topic.title, description = topic.description, order = topic.order,
                lessons = topicLessons.sortedBy { it.order }.map { l -> l.toDto(byLesson.getValue(l.id)) },
            )
        }.sortedBy { it.order }
        PackDto(
            schemaVersion = 1,
            packId = pack.id,
            packVersion = pack.version,
            title = pack.title,
            description = pack.description,
            createdAt = Instant.ofEpochMilli(pack.importedAt).toString(),
            topics = topicDtos,
        )
    }
}

private fun LessonEntity.toDto(items: List<ItemEntity>) = LessonDto(
    id = id, title = title, order = order, difficulty = difficulty,
    grammarNote = grammarNoteId?.let { GrammarNoteDto(it, grammarNoteTitle.orEmpty(), grammarNoteBody.orEmpty()) },
    items = items.sortedBy { it.order }.map { i ->
        ItemDto(
            id = i.id, kind = i.kind, idText = i.idText, plText = i.plText, register = i.register,
            acceptedId = i.acceptedId, acceptedPl = i.acceptedPl,
            distractorsId = i.distractorsId, distractorsPl = i.distractorsPl,
            notes = i.notes, tags = i.tags, placementOnly = i.placementOnly,
        )
    },
)
