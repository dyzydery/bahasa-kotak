package io.github.dyzydery.bahasakotak.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.github.dyzydery.bahasakotak.io.export.ExportData

@Dao
interface ExportDao {
    @Query("SELECT * FROM packs") suspend fun packs(): List<PackEntity>
    @Query("SELECT * FROM topics") suspend fun topics(): List<TopicEntity>
    @Query("SELECT * FROM lessons") suspend fun lessons(): List<LessonEntity>
    @Query("SELECT * FROM items") suspend fun items(): List<ItemEntity>
    @Query("SELECT * FROM cards") suspend fun cards(): List<CardEntity>
    @Query("SELECT * FROM answers") suspend fun answers(): List<AnswerEntity>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM lesson_progress") suspend fun lessonProgress(): List<LessonProgressEntity>

    /** One consistent snapshot of everything the export needs. */
    @Transaction
    suspend fun exportData() = ExportData(
        packs(), topics(), lessons(), items(), cards(), answers(), sessions(), lessonProgress(),
    )
}
