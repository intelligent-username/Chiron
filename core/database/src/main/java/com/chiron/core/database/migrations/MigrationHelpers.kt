package com.chiron.core.database.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Shared SQLite schema inspection utilities for Room migrations.
 */
object MigrationHelpers {

    /**
     * Checks whether a column exists on a table within SQLite.
     */
    fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
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
}
