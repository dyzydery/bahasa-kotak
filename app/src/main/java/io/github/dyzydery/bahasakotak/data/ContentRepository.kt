package io.github.dyzydery.bahasakotak.data

import androidx.room.withTransaction
import io.github.dyzydery.bahasakotak.io.pack.ImportPlan
import io.github.dyzydery.bahasakotak.io.pack.InstalledContent

class ContentRepository(private val db: BahasaDatabase) {
    private val dao = db.contentDao()

    val packs = dao.observePacks()
    val stats = dao.observeStats()

    suspend fun installedContent(packId: String) = InstalledContent(
        installedVersion = dao.packVersion(packId),
        packTopicIds = dao.activeTopicIds(packId).toSet(),
        packLessonIds = dao.activeLessonIds(packId).toSet(),
        packItemIds = dao.activeItemIds(packId).toSet(),
        allItemIds = dao.allItemIds().toSet(),
    )

    /** Applies a plan atomically. Only content tables are written; progress is untouched. */
    suspend fun apply(plan: ImportPlan.Apply, now: Long) {
        val pack = plan.pack
        db.withTransaction {
            dao.upsertPack(PackEntity(pack.packId, pack.packVersion, pack.title, pack.description, now))
            dao.upsertTopics(pack.topics.map { t ->
                TopicEntity(t.id, pack.packId, t.title, t.description, t.order)
            })
            dao.upsertLessons(pack.topics.flatMap { t ->
                t.lessons.map { l ->
                    LessonEntity(
                        id = l.id, topicId = t.id, packId = pack.packId, title = l.title, order = l.order,
                        difficulty = l.difficulty, grammarNoteId = l.grammarNote?.id,
                        grammarNoteTitle = l.grammarNote?.title, grammarNoteBody = l.grammarNote?.body,
                    )
                }
            })
            dao.upsertItems(pack.topics.flatMap { t ->
                t.lessons.flatMap { l ->
                    l.items.mapIndexed { index, i ->
                        ItemEntity(
                            id = i.id, lessonId = l.id, packId = pack.packId, order = index, kind = i.kind,
                            idText = i.idText, plText = i.plText, register = i.register,
                            acceptedId = i.acceptedId, acceptedPl = i.acceptedPl,
                            distractorsId = i.distractorsId, distractorsPl = i.distractorsPl,
                            notes = i.notes, tags = i.tags, placementOnly = i.placementOnly,
                        )
                    }
                }
            })
            if (plan.retiredTopicIds.isNotEmpty()) dao.retireTopics(plan.retiredTopicIds)
            if (plan.retiredLessonIds.isNotEmpty()) dao.retireLessons(plan.retiredLessonIds)
            if (plan.retiredItemIds.isNotEmpty()) dao.retireItems(plan.retiredItemIds)
        }
    }
}
