package com.npic.photoandsignscanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Root Room database (PRD §8.1). Schema JSON exports to `app/schemas/` so every migration
 * has a diff baseline. Three tables:
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
    version = 3,
    exportSchema = true,
)
abstract class NpicDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
    abstract fun draftDao(): DraftDao

    companion object {
        private const val DB_NAME = "npic.db"

        /**
         * Explicit v1 → v2 migration. Adds:
         *   - `students.nameKey` (TEXT NOT NULL, backfilled from displayName via the same
         *     TRIM + collapse-whitespace + LOWER pipeline that Kotlin's [normalizeNameKey]
         *     uses; ASCII-equivalent so the SQLite pass is byte-for-byte identical for
         *     English names)
         *   - composite index (classNum, nameKey) — replaces the previous (displayName)
         *     scan index
         *   - `drafts.rawPath`, `rawMode`, `capturedAt`, `guideBoxLeft/Top/Right/Bottom`
         *     (all NULLABLE) — persists [CameraCapture] across process kill for the
         *     resume-prompt (Oracle O1-7)
         *
         * DEFERRED-DECISIONS C2 lands this migration ahead of any real user data so v1.0
         * ships with a working migration baseline (per user directive m1537 B4: "if easier
         * and will not take muh time do not otherwise in v2"). This one is ~30 min of work
         * with a clean SQL diff so it lives in v1.0.
         */
        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Students: add nameKey (populated deterministically to match
                // normalizeNameKey), then swap displayName index for the composite lookup.
                db.execSQL("ALTER TABLE students ADD COLUMN nameKey TEXT NOT NULL DEFAULT ''")
                // Backfill: LOWER(TRIM(displayName)) collapses whitespace to match Kotlin's
                // normalizeNameKey. SQLite has no WHILE loop, so we approximate `\s+` → ` `
                // via two stacked REPLACE chains. Inner chain collapses runs of 2..8 into
                // one space; outer chain re-collapses whatever the inner pass produced.
                // Two passes handle any input up to ~64 consecutive spaces — well beyond
                // any real name. Oracle qc-round-8 flagged the single-pass version as
                // leaving 2 spaces on 9+ space runs.
                // Oracle #2 D1 (qc-round-10): Kotlin's normalizeNameKey uses `\s+` which
                // matches ALL Unicode whitespace (tab 0x09, LF 0x0A, CR 0x0D, ASCII space
                // 0x20, plus U+00A0 NBSP etc.). SQLite REPLACE only substitutes literal
                // strings, so we must FIRST fold tab / LF / CR / NBSP down to plain space,
                // THEN run the two-pass REPLACE chain to collapse runs. Without the
                // pre-pass, a name saved as "John\tDoe" in v1 would migrate to
                // "john\tdoe" and mismatch the runtime lookup "john doe".
                db.execSQL(
                    """
                    UPDATE students SET nameKey = LOWER(TRIM(
                        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                            REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                                REPLACE(REPLACE(REPLACE(REPLACE(
                                    displayName,
                                    char(9), ' '),
                                    char(10), ' '),
                                    char(13), ' '),
                                    char(160), ' '),
                                '        ', ' '),
                                '       ', ' '),
                                '      ', ' '),
                                '     ', ' '),
                                '    ', ' '),
                                '   ', ' '),
                                '  ', ' '),
                                '  ', ' '),
                            '        ', ' '),
                            '       ', ' '),
                            '      ', ' '),
                            '     ', ' '),
                            '    ', ' '),
                            '   ', ' '),
                            '  ', ' '),
                            '  ', ' ')
                    ))
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS `index_students_displayName`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_students_classNum_nameKey` " +
                        "ON students (classNum, nameKey)"
                )

                // Drafts: add the seven capture-rehydration columns. All nullable so
                // existing v1 rows (which predate the columns) sit at NULL — matching the
                // "no capture yet" branch of DraftEntity.toCameraCaptureOrNull().
                db.execSQL("ALTER TABLE drafts ADD COLUMN rawPath TEXT")
                db.execSQL("ALTER TABLE drafts ADD COLUMN rawMode TEXT")
                db.execSQL("ALTER TABLE drafts ADD COLUMN capturedAt INTEGER")
                db.execSQL("ALTER TABLE drafts ADD COLUMN guideBoxLeft INTEGER")
                db.execSQL("ALTER TABLE drafts ADD COLUMN guideBoxTop INTEGER")
                db.execSQL("ALTER TABLE drafts ADD COLUMN guideBoxRight INTEGER")
                db.execSQL("ALTER TABLE drafts ADD COLUMN guideBoxBottom INTEGER")
            }
        }

        fun create(context: Context): NpicDatabase = Room.databaseBuilder(
            context = context.applicationContext,
            klass = NpicDatabase::class.java,
            name = DB_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            // m2502 pre-ship: v3 adds StudentEntity.duplicateIndex + swaps unique
            // indices. App has not shipped to production yet (user directive: no legacy
            // data to preserve), so a destructive fallback is safe. Post-launch, replace
            // with an explicit MIGRATION_2_3 that ALTERs the column + rebuilds indices.
            .fallbackToDestructiveMigration()
            .build()
    }
}
