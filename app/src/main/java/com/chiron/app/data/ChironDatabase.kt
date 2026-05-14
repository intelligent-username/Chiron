package com.chiron.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.data.dao.Exercise1rmEstimateDao
import com.chiron.app.data.entities.Exercise1rmEstimate

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        ExerciseEntry::class,
        SetEntry::class,
        TimerPreset::class,
        ExercisePr::class,
        Exercise1rmEstimate::class
    ],
    version = 10,
    exportSchema = false
)
abstract class ChironDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun setEntryDao(): SetEntryDao
    abstract fun timerPresetDao(): TimerPresetDao
    abstract fun exercisePrDao(): ExercisePrDao
    abstract fun exercise1rmEstimateDao(): Exercise1rmEstimateDao

    companion object {
        @Volatile
        private var INSTANCE: ChironDatabase? = null

        // These migrations are legacy, probably shouldn't be run every again
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise ADD COLUMN icon_name TEXT DEFAULT 'default'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop and recreate table with correct schema (no unique constraint at all)
                // Note: unique constraint on (day_tag, date_iso, location_tag) is REMOVED.
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day_tag` TEXT NOT NULL, `date_iso` TEXT NOT NULL, `date_utc` INTEGER NOT NULL, `location_tag` TEXT NOT NULL, `notes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO `workout_session_new` (`id`, `day_tag`, `date_iso`, `date_utc`, `location_tag`, `notes`, `archived`) SELECT `id`, `day_tag`, `date_iso`, `date_utc`, `location_tag`, `notes`, `archived` FROM `workout_session`")
                db.execSQL("DROP TABLE `workout_session`")
                db.execSQL("ALTER TABLE `workout_session_new` RENAME TO `workout_session`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_date_utc` ON `workout_session` (`date_utc`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `timer_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration_seconds` INTEGER NOT NULL, `label` TEXT NOT NULL, `archived` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate exercise_entry table with the new num_exercises_in_superset column as NOT NULL
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_entry_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workout_id` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, `slot_index` INTEGER NOT NULL, `group_id` INTEGER, `sequence_type` TEXT NOT NULL, `notes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0, `num_exercises_in_superset` INTEGER NOT NULL DEFAULT 2, FOREIGN KEY(`workout_id`) REFERENCES `workout_session`(`id`) ON DELETE CASCADE, FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE)")
                db.execSQL("INSERT INTO `exercise_entry_new` (`id`, `workout_id`, `exercise_id`, `slot_index`, `group_id`, `sequence_type`, `notes`, `archived`, `num_exercises_in_superset`) SELECT `id`, `workout_id`, `exercise_id`, `slot_index`, `group_id`, `sequence_type`, `notes`, `archived`, 2 FROM `exercise_entry`")
                db.execSQL("DROP TABLE `exercise_entry`")
                db.execSQL("ALTER TABLE `exercise_entry_new` RENAME TO `exercise_entry`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_entry_workout_id_slot_index` ON `exercise_entry` (`workout_id`, `slot_index`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entry_exercise_id` ON `exercise_entry` (`exercise_id`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add is_pr column to set_entry
                db.execSQL("ALTER TABLE set_entry ADD COLUMN is_pr INTEGER NOT NULL DEFAULT 0")
                // Create exercise_pr table for global current PRs per (exercise, reps)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_pr` (
                        `exercise_id` INTEGER NOT NULL,
                        `reps` INTEGER NOT NULL,
                        `weight_lbs` REAL NOT NULL,
                        `set_id` INTEGER NOT NULL,
                        `timestamp_utc` INTEGER NOT NULL,
                        PRIMARY KEY(`exercise_id`, `reps`),
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_pr_exercise_id` ON `exercise_pr` (`exercise_id`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate exercise_pr to add missing FK(set_id -> set_entry.id) and missing index(set_id)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_pr_new` (
                        `exercise_id` INTEGER NOT NULL,
                        `reps` INTEGER NOT NULL,
                        `weight_lbs` REAL NOT NULL,
                        `set_id` INTEGER NOT NULL,
                        `timestamp_utc` INTEGER NOT NULL,
                        PRIMARY KEY(`exercise_id`, `reps`),
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`set_id`) REFERENCES `set_entry`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `exercise_pr_new` (`exercise_id`, `reps`, `weight_lbs`, `set_id`, `timestamp_utc`)
                    SELECT `exercise_id`, `reps`, `weight_lbs`, `set_id`, `timestamp_utc` FROM `exercise_pr`
                """.trimIndent())

                db.execSQL("DROP TABLE `exercise_pr`")
                db.execSQL("ALTER TABLE `exercise_pr_new` RENAME TO `exercise_pr`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_pr_exercise_id` ON `exercise_pr` (`exercise_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_pr_set_id` ON `exercise_pr` (`set_id`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_1rm_estimate` (
                        `exercise_id` INTEGER NOT NULL,
                        `estimate_lbs` REAL NOT NULL,
                        PRIMARY KEY(`exercise_id`),
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_session ADD COLUMN end_time_utc INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper: check whether a column already exists (idempotent adds)
                fun hasColumn(table: String, column: String): Boolean {
                    var exists = false
                    db.query("PRAGMA table_info($table)").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            if (nameIdx >= 0 && c.getString(nameIdx) == column) {
                                exists = true
                                break
                            }
                        }
                    }
                    return exists
                }

                // Exercise: 4 tracking config flags (NOT NULL + DEFAULT to backfill existing rows)
                if (!hasColumn("exercise", "is_weight_based"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_weight_based INTEGER NOT NULL DEFAULT 1")
                if (!hasColumn("exercise", "is_rep_based"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_rep_based INTEGER NOT NULL DEFAULT 1")
                if (!hasColumn("exercise", "is_time_based"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_time_based INTEGER NOT NULL DEFAULT 0")
                if (!hasColumn("exercise", "is_distance_based"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_distance_based INTEGER NOT NULL DEFAULT 0")

                // SetEntry: 2 new nullable metric columns
                if (!hasColumn("set_entry", "duration_seconds"))
                    db.execSQL("ALTER TABLE set_entry ADD COLUMN duration_seconds INTEGER DEFAULT NULL")
                if (!hasColumn("set_entry", "distance_meters"))
                    db.execSQL("ALTER TABLE set_entry ADD COLUMN distance_meters REAL DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): ChironDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChironDatabase::class.java,
                    "chiron_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate default exercises on first launch using raw SQL to avoid recursion issues
                        val defaults = listOf(
                            "Ab Twister" to "ab-twister",
                            "Band Pull Aparts" to "bands",
                            "Barbell Row" to "barbell",
                            "Bench Press" to "benchpress",
                            "Cable Crossover" to "cable-crossover",
                            "Cable Row" to "cables",
                            "Machine Chest Press" to "chest-press",
                            "Bicep Curl" to "curl",
                            "Hammer Curl" to "hammer-curl",
                            "Deadlift" to "deadlift",
                            "Dips" to "dip",
                            "Farmers Carry" to "farmers-carry",
                            "Fly Machine" to "fly-machine",
                            "Cardio" to "heart-rate",
                            "Hip Thrust" to "hip-thrust",
                            "Incline Bench Press" to "incline-bench",
                            "Incline Machine Press" to "incline-press-machine",
                            "Box Jumps" to "jump",
                            "Kettlebell Swing" to "kettlebell",
                            "Landmine Rotation" to "landmine-rotation",
                            "Lateral Raises" to "lateral-raise",
                            "Leg Curl" to "leg-curl",
                            "Leg Extension" to "leg-extension",
                            "Leg Press" to "leg-press",
                            "Leg Raises" to "leg-raise",
                            "Lunges" to "lunge",
                            "Machine Row" to "machine-row",
                            "Pec Deck" to "machine",
                            "Medicine Ball Slam" to "medicine-ball",
                            "Overhead Press" to "overhead-press",
                            "Plank" to "45-plate",
                            "Preacher Curl" to "preacher-curl",
                            "Pull Ups" to "pull-up",
                            "Lat Pulldown" to "pulldown",
                            "Push ups" to "push-up",
                            "Tricep Pushdown" to "pushdown",
                            "Ring Dips" to "rings",
                            "Sit-ups" to "sit-up",
                            "Good Morning" to "smiley",
                            "Smith Machine Squat" to "smith",
                            "Squat" to "squat",
                            "Stationary Bike" to "stationary_bike",
                            "Treadmill" to "treadmill"
                        )

                        db.beginTransaction()
                        try {
                            defaults.forEach { (name, iconName) ->
                                val values = android.content.ContentValues().apply {
                                    put("name", name)
                                    put("icon_name", iconName)
                                    put("archived", 0)
                                }
                                db.insert("exercise", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
