package com.npic.photoandsignscanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Root Room database (PRD §8.1). Version 1 — schema JSON exports to `app/schemas/` so any
 * future migration has a diff baseline. Three tables:
 *
 *  * [StudentEntity]      — persisted student records surfaced by Gallery/Detail/Export
 *  * [ClassCounterEntity] — monotonic per-class serial counter (nextSerial atomicity)
 *  * [DraftEntity]        — capture-in-progress record for resume-prompt (PRD §8.3)
 */
@Database(
    entities = [
        StudentEntity::class,
        ClassCounterEntity::class,
        DraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NpicDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun draftDao(): DraftDao

    companion object {
        private const val DB_NAME = "npic.db"

        fun create(context: Context): NpicDatabase = Room.databaseBuilder(
            context = context.applicationContext,
            klass = NpicDatabase::class.java,
            name = DB_NAME,
        ).build()
    }
}
