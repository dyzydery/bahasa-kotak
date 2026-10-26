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
        val rows = plan.pack.toRows(importedAt = now)
        db.withTransaction {
            dao.upsertPack(rows.pack)
            dao.upsertTopics(rows.topics)
            dao.upsertLessons(rows.lessons)
            dao.upsertItems(rows.items)
            if (plan.retiredTopicIds.isNotEmpty()) dao.retireTopics(plan.retiredTopicIds)
            if (plan.retiredLessonIds.isNotEmpty()) dao.retireLessons(plan.retiredLessonIds)
            if (plan.retiredItemIds.isNotEmpty()) dao.retireItems(plan.retiredItemIds)
        }
    }
}
