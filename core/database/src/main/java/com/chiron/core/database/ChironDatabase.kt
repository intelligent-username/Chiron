package com.chiron.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.BodyWeightDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.ExercisePrDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.database.dao.TimerPresetDao
import com.chiron.core.model.Exercise
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.ExercisePr
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.core.model.TimerPreset
import com.chiron.core.database.dao.Exercise1rmEstimateDao
import com.chiron.core.database.dao.GoalDao
import com.chiron.core.model.Exercise1rmEstimate
import com.chiron.core.model.Goal
import com.chiron.core.model.GoalExercise

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        ExerciseEntry::class,
        SetEntry::class,
        TimerPreset::class,
        ExercisePr::class,
        Exercise1rmEstimate::class,
        Goal::class,
        GoalExercise::class,
        BodyWeightEntry::class
    ],
    version = 14,
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
    abstract fun goalDao(): GoalDao
    abstract fun bodyWeightDao(): BodyWeightDao

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

        // Generalize exercise_pr from (exercise_id, reps, weight_lbs) to a
        // category-agnostic (exercise_id, bucket, record) shape so PRs can be
        // tracked for time/distance exercises too. Existing rows are all
        // weight+reps, so reps→bucket and weight_lbs→record is a faithful copy.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_pr_new` (
                        `exercise_id` INTEGER NOT NULL,
                        `bucket` REAL NOT NULL,
                        `record` REAL NOT NULL,
                        `set_id` INTEGER NOT NULL,
                        `timestamp_utc` INTEGER NOT NULL,
                        PRIMARY KEY(`exercise_id`, `bucket`),
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`set_id`) REFERENCES `set_entry`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `exercise_pr_new` (`exercise_id`, `bucket`, `record`, `set_id`, `timestamp_utc`)
                    SELECT `exercise_id`, CAST(`reps` AS REAL), `weight_lbs`, `set_id`, `timestamp_utc` FROM `exercise_pr`
                """.trimIndent())

                db.execSQL("DROP TABLE `exercise_pr`")
                db.execSQL("ALTER TABLE `exercise_pr_new` RENAME TO `exercise_pr`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_pr_exercise_id` ON `exercise_pr` (`exercise_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_pr_set_id` ON `exercise_pr` (`set_id`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `weekly_target` INTEGER NOT NULL, `archived` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_exercise` (`goal_id` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, PRIMARY KEY(`goal_id`, `exercise_id`), FOREIGN KEY(`goal_id`) REFERENCES `goal`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_exercise_exercise_id` ON `goal_exercise` (`exercise_id`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

                db.execSQL("CREATE TABLE IF NOT EXISTS `body_weight_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp_utc` INTEGER NOT NULL, `weight_lbs` REAL NOT NULL, `note` TEXT, `source` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weight_entry_timestamp_utc` ON `body_weight_entry` (`timestamp_utc`)")

                if (!hasColumn("exercise", "is_bodyweight"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_bodyweight INTEGER NOT NULL DEFAULT 0")
                if (!hasColumn("exercise", "percent_bodyweight"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN percent_bodyweight REAL NOT NULL DEFAULT 100.0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

                db.execSQL("CREATE TABLE IF NOT EXISTS `body_weight_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp_utc` INTEGER NOT NULL, `weight_lbs` REAL NOT NULL, `note` TEXT, `source` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weight_entry_timestamp_utc` ON `body_weight_entry` (`timestamp_utc`)")

                if (!hasColumn("exercise", "is_bodyweight"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN is_bodyweight INTEGER NOT NULL DEFAULT 0")
                if (!hasColumn("exercise", "percent_bodyweight"))
                    db.execSQL("ALTER TABLE exercise ADD COLUMN percent_bodyweight REAL NOT NULL DEFAULT 100.0")
            }
        }

        private data class DefaultExerciseSeed(
            val name: String,
            val iconName: String,
            val isBodyweight: Boolean = false,
            val percentBodyweight: Double = 100.0
        )

        fun getInstance(context: Context): ChironDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChironDatabase::class.java,
                    "chiron_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate default exercises on first launch using raw SQL to avoid recursion issues
                        val defaults = listOf(
                            DefaultExerciseSeed("Ab Twister", "ab-twister"),
                            DefaultExerciseSeed("Australian Pull Ups", "pull-up", isBodyweight = true, percentBodyweight = 60.0),
                            DefaultExerciseSeed("Band Pull Aparts", "bands"),
                            DefaultExerciseSeed("Barbell Row", "barbell-row"),
                            DefaultExerciseSeed("Bench Press", "benchpress"),
                            DefaultExerciseSeed("Cable Crossover", "cable-crossover"),
                            DefaultExerciseSeed("Cable Row", "cables"),
                            DefaultExerciseSeed("Machine Chest Press", "chest-press"),
                            DefaultExerciseSeed("Bicep Curl", "curl"),
                            DefaultExerciseSeed("Hammer Curl", "hammer-curl"),
                            DefaultExerciseSeed("Deadlift", "deadlift"),
                            DefaultExerciseSeed("Dips", "dip", isBodyweight = true, percentBodyweight = 100.0),
                            DefaultExerciseSeed("Farmer Carry", "farmers-carry"),
                            DefaultExerciseSeed("Fly Machine", "fly-machine"),
                            DefaultExerciseSeed("Cardio", "heart-rate"),
                            DefaultExerciseSeed("Hip Thrust", "hip-thrust"),
                            DefaultExerciseSeed("Incline Bench Press", "incline-bench"),
                            DefaultExerciseSeed("Incline Machine Press", "incline-press-machine"),
                            DefaultExerciseSeed("Box Jumps", "jump"),
                            DefaultExerciseSeed("Kettlebell Swing", "kettlebell"),
                            DefaultExerciseSeed("Landmine Rotation", "landmine-rotation"),
                            DefaultExerciseSeed("Lateral Raises", "lateral-raise"),
                            DefaultExerciseSeed("Leg Curl", "leg-curl"),
                            DefaultExerciseSeed("Leg Extension", "leg-extension"),
                            DefaultExerciseSeed("Leg Press", "leg-press"),
                            DefaultExerciseSeed("Leg Raises", "leg-raise", isBodyweight = true, percentBodyweight = 50.0),
                            DefaultExerciseSeed("Lunges", "lunge"),
                            DefaultExerciseSeed("Machine Row", "machine-row"),
                            DefaultExerciseSeed("Pec Deck", "machine"),
                            DefaultExerciseSeed("Medicine Ball Slam", "medicine-ball"),
                            DefaultExerciseSeed("Overhead Press", "overhead-press"),
                            DefaultExerciseSeed("Plank", "45-plate"),
                            DefaultExerciseSeed("Preacher Curl", "preacher-curl"),
                            DefaultExerciseSeed("Pull Ups", "pull-up", isBodyweight = true, percentBodyweight = 100.0),
                            DefaultExerciseSeed("Lat Pulldown", "pulldown"),
                            DefaultExerciseSeed("Push ups", "push-up", isBodyweight = true, percentBodyweight = 60.0),
                            DefaultExerciseSeed("Tricep Pushdown", "pushdown"),
                            DefaultExerciseSeed("Ring Dips", "rings", isBodyweight = true, percentBodyweight = 100.0),
                            DefaultExerciseSeed("Sit-ups", "sit-up", isBodyweight = true, percentBodyweight = 50.0),
                            DefaultExerciseSeed("Good Morning", "good-morning"),
                            DefaultExerciseSeed("Smith Machine Squat", "smith"),
                            DefaultExerciseSeed("Squat", "squat"),
                            DefaultExerciseSeed("Stationary Bike", "stationary_bike"),
                            DefaultExerciseSeed("Treadmill", "treadmill")
                        )

                        db.beginTransaction()
                        try {
                            defaults.forEach { seed ->
                                val values = android.content.ContentValues().apply {
                                    put("name", seed.name)
                                    put("icon_name", seed.iconName)
                                    put("archived", 0)
                                    put("is_bodyweight", if (seed.isBodyweight) 1 else 0)
                                    put("percent_bodyweight", seed.percentBodyweight)
                                }
                                db.insert("exercise", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }

                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 100.0 WHERE name IN ('Pull Ups', 'Pull ups', 'Pull-ups') AND is_bodyweight = 0")
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 60.0 WHERE name IN ('Push ups', 'Push Ups', 'Push-ups') AND is_bodyweight = 0")
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 50.0 WHERE name IN ('Sit-ups', 'Sit Ups', 'Sit-up') AND is_bodyweight = 0")
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 100.0 WHERE name = 'Dips' AND is_bodyweight = 0")
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 100.0 WHERE name = 'Ring Dips' AND is_bodyweight = 0")
                            db.execSQL("UPDATE exercise SET is_bodyweight = 1, percent_bodyweight = 50.0 WHERE name = 'Leg Raises' AND is_bodyweight = 0")
                            db.execSQL("INSERT OR IGNORE INTO exercise (name, icon_name, archived, is_weight_based, is_rep_based, is_time_based, is_distance_based, is_bodyweight, percent_bodyweight) VALUES ('Australian Pull Ups', 'pull-up', 0, 1, 1, 0, 0, 1, 60.0)")
                        } catch (_: Exception) {}
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
