package com.chiron.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations 12 through 15 handling bodyweight tracking and related schema upgrades.
 */
object Migrations12To15 {

    private fun applyBodyweightMigration(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `body_weight_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp_utc` INTEGER NOT NULL, `weight_lbs` REAL NOT NULL, `note` TEXT, `source` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weight_entry_timestamp_utc` ON `body_weight_entry` (`timestamp_utc`)")

        if (!MigrationHelpers.hasColumn(db, "exercise", "is_bodyweight"))
            db.execSQL("ALTER TABLE exercise ADD COLUMN is_bodyweight INTEGER NOT NULL DEFAULT 0")
        if (!MigrationHelpers.hasColumn(db, "exercise", "percent_bodyweight"))
            db.execSQL("ALTER TABLE exercise ADD COLUMN percent_bodyweight REAL NOT NULL DEFAULT 100.0")
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) = applyBodyweightMigration(db)
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) = applyBodyweightMigration(db)
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) = applyBodyweightMigration(db)
    }
}
