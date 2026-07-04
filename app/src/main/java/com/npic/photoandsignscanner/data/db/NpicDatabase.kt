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
    version = 2,
    exportSchema = true,
)
abstract class NpicDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun draftDao(): DraftDao

    companion object {
        private const val DB_NAME = "npic.db"

        /**
         * v2 schema adds:
         *   - `students.nameKey` + composite (classNum, nameKey) index (Oracle O5-B4 / PRD §8.1)
         *   - `drafts.rawPath` / `rawMode` / `capturedAt` / `guideBox{Left,Top,Right,Bottom}`
         *     to persist CameraCapture across process kill (Oracle O1-7)
         *
         * v1 → v2 uses destructive migration because the app has not shipped yet (DEFERRED-
         * DECISIONS B4 freezes migration flow at the v1.0 tag). Dev installs lose any test
         * records — acceptable per user directive m1114. Once v1.0 ships this must be
         * replaced with an explicit Migration(1, 2) that ADD COLUMN + backfills nameKey.
         */
        fun create(context: Context): NpicDatabase = Room.databaseBuilder(
            context = context.applicationContext,
            klass = NpicDatabase::class.java,
            name = DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
