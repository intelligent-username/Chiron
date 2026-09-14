package com.chiron.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Historical migrations 1 through 11 for the Chiron Room database schema.
 */
object Migrations1To11 {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercise ADD COLUMN icon_name TEXT DEFAULT 'default'")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day_tag` TEXT NOT NULL, `date_iso` TEXT NOT NULL, `date_utc` INTEGER NOT NULL, `location_tag` TEXT NOT NULL, `notes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("INSERT INTO `workout_session_new` (`id`, `day_tag`, `date_iso`, `date_utc`, `location_tag`, `notes`, `archived`) SELECT `id`, `day_tag`, `date_iso`, `date_utc`, `location_tag`, `notes`, `archived` FROM `workout_session`")
            db.execSQL("DROP TABLE `workout_session`")
            db.execSQL("ALTER TABLE `workout_session_new` RENAME TO `workout_session`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_date_utc` ON `workout_session` (`date_utc`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `timer_presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration_seconds` INTEGER NOT NULL, `label` TEXT NOT NULL, `archived` INTEGER NOT NULL DEFAULT 0)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_entry_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workout_id` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, `slot_index` INTEGER NOT NULL, `group_id` INTEGER, `sequence_type` TEXT NOT NULL, `notes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0, `num_exercises_in_superset` INTEGER NOT NULL DEFAULT 2, FOREIGN KEY(`workout_id`) REFERENCES `workout_session`(`id`) ON DELETE CASCADE, FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO `exercise_entry_new` (`id`, `workout_id`, `exercise_id`, `slot_index`, `group_id`, `sequence_type`, `notes`, `archived`, `num_exercises_in_superset`) SELECT `id`, `workout_id`, `exercise_id`, `slot_index`, `group_id`, `sequence_type`, `notes`, `archived`, 2 FROM `exercise_entry`")
            db.execSQL("DROP TABLE `exercise_entry`")
            db.execSQL("ALTER TABLE `exercise_entry_new` RENAME TO `exercise_entry`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_entry_workout_id_slot_index` ON `exercise_entry` (`workout_id`, `slot_index`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entry_exercise_id` ON `exercise_entry` (`exercise_id`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE set_entry ADD COLUMN is_pr INTEGER NOT NULL DEFAULT 0")
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

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
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

    val MIGRATION_7_8 = object : Migration(7, 8) {
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

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_session ADD COLUMN end_time_utc INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!MigrationHelpers.hasColumn(db, "exercise", "is_weight_based"))
                db.execSQL("ALTER TABLE exercise ADD COLUMN is_weight_based INTEGER NOT NULL DEFAULT 1")
            if (!MigrationHelpers.hasColumn(db, "exercise", "is_rep_based"))
                db.execSQL("ALTER TABLE exercise ADD COLUMN is_rep_based INTEGER NOT NULL DEFAULT 1")
            if (!MigrationHelpers.hasColumn(db, "exercise", "is_time_based"))
                db.execSQL("ALTER TABLE exercise ADD COLUMN is_time_based INTEGER NOT NULL DEFAULT 0")
            if (!MigrationHelpers.hasColumn(db, "exercise", "is_distance_based"))
                db.execSQL("ALTER TABLE exercise ADD COLUMN is_distance_based INTEGER NOT NULL DEFAULT 0")

            if (!MigrationHelpers.hasColumn(db, "set_entry", "duration_seconds"))
                db.execSQL("ALTER TABLE set_entry ADD COLUMN duration_seconds INTEGER DEFAULT NULL")
            if (!MigrationHelpers.hasColumn(db, "set_entry", "distance_meters"))
                db.execSQL("ALTER TABLE set_entry ADD COLUMN distance_meters REAL DEFAULT NULL")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
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

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `weekly_target` INTEGER NOT NULL, `archived` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `goal_exercise` (`goal_id` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, PRIMARY KEY(`goal_id`, `exercise_id`), FOREIGN KEY(`goal_id`) REFERENCES `goal`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`exercise_id`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_exercise_exercise_id` ON `goal_exercise` (`exercise_id`)")
        }
    }
}
