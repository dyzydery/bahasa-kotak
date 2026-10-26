package io.github.dyzydery.bahasakotak.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PackEntity::class, TopicEntity::class, LessonEntity::class, ItemEntity::class,
        CardEntity::class, AnswerEntity::class, SessionEntity::class, LessonProgressEntity::class,
    ],
    version = BahasaDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BahasaDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun studyDao(): StudyDao
    abstract fun exportDao(): ExportDao

    companion object {
        const val FILE_NAME = "bahasa-kotak.db"
        const val VERSION = 1

        fun open(context: Context): BahasaDatabase =
            Room.databaseBuilder(context, BahasaDatabase::class.java, FILE_NAME).build()
    }
}
