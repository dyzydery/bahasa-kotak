package io.github.dyzydery.bahasakotak.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LessonRow(
    @Embedded val lesson: LessonEntity,
    val topicOrder: Int,
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM items WHERE NOT retired ORDER BY lessonId, sortOrder")
    suspend fun activeItems(): List<ItemEntity>

    @Query(
        """
        SELECT lessons.*, topics.sortOrder AS topicOrder FROM lessons JOIN topics ON topics.id = lessons.topicId
        WHERE NOT lessons.retired AND NOT topics.retired
        ORDER BY topics.sortOrder, lessons.sortOrder
        """
    )
    suspend fun activeLessons(): List<LessonRow>

    @Query("SELECT * FROM cards")
    suspend fun cards(): List<CardEntity>

    @Query("SELECT lessonId FROM lesson_progress WHERE grammarNoteSeen")
    suspend fun seenGrammarNotes(): List<String>

    @Query("SELECT durationMs FROM answers WHERE durationMs IS NOT NULL AND answeredAt >= :since")
    suspend fun durationsSince(since: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE placement AND endedAt IS NOT NULL)")
    fun observePlacementDone(): Flow<Boolean>

    @Upsert suspend fun upsertCards(cards: List<CardEntity>)
    @Upsert suspend fun upsertLessonProgress(progress: LessonProgressEntity)
    @Upsert suspend fun upsertSession(session: SessionEntity)
    @Insert suspend fun insertAnswer(answer: AnswerEntity)
}
