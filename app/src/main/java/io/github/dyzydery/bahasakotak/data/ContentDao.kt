package io.github.dyzydery.bahasakotak.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ContentStats(
    val topics: Int,
    val lessons: Int,
    val items: Int,
    val sentences: Int,
)

@Dao
interface ContentDao {
    @Query("SELECT * FROM packs ORDER BY importedAt")
    fun observePacks(): Flow<List<PackEntity>>

    @Query("SELECT version FROM packs WHERE id = :packId")
    suspend fun packVersion(packId: String): Int?

    @Query("SELECT id FROM topics WHERE packId = :packId AND NOT retired")
    suspend fun activeTopicIds(packId: String): List<String>

    @Query("SELECT id FROM lessons WHERE packId = :packId AND NOT retired")
    suspend fun activeLessonIds(packId: String): List<String>

    @Query("SELECT id FROM items WHERE packId = :packId AND NOT retired")
    suspend fun activeItemIds(packId: String): List<String>

    @Query("SELECT id FROM items")
    suspend fun allItemIds(): List<String>

    // @Upsert updates rows in place. Never use OnConflictStrategy.REPLACE here: it deletes the old row first.
    @Upsert suspend fun upsertPack(pack: PackEntity)
    @Upsert suspend fun upsertTopics(topics: List<TopicEntity>)
    @Upsert suspend fun upsertLessons(lessons: List<LessonEntity>)
    @Upsert suspend fun upsertItems(items: List<ItemEntity>)

    @Query("UPDATE topics SET retired = 1 WHERE id IN (:ids)")
    suspend fun retireTopics(ids: Collection<String>)

    @Query("UPDATE lessons SET retired = 1 WHERE id IN (:ids)")
    suspend fun retireLessons(ids: Collection<String>)

    @Query("UPDATE items SET retired = 1 WHERE id IN (:ids)")
    suspend fun retireItems(ids: Collection<String>)

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM topics WHERE NOT retired) AS topics,
          (SELECT COUNT(*) FROM lessons WHERE NOT retired) AS lessons,
          (SELECT COUNT(*) FROM items WHERE NOT retired AND NOT placementOnly) AS items,
          (SELECT COUNT(*) FROM items WHERE NOT retired AND NOT placementOnly AND kind = 'SENTENCE') AS sentences
        """
    )
    fun observeStats(): Flow<ContentStats>
}
