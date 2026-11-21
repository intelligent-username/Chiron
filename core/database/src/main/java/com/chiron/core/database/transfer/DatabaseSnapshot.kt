package com.chiron.core.database.transfer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.chiron.core.database.ChironDatabase
import java.io.File

/**
 * Copy committed rows through SQLite, not the live DB file. One Room transaction
 * sees a consistent snapshot including WAL-backed rows on every supported Android version.
 */
internal suspend fun ChironDatabase.writeBackupSnapshot(destination: File) {
    withTransaction {
        val source = openHelper.writableDatabase
        SQLiteDatabase.openOrCreateDatabase(destination, null).use { snapshot ->
            snapshot.disableWriteAheadLogging()
            snapshot.beginTransaction()
            try {
                val schema = source.query(
                    "SELECT type, name, sql FROM sqlite_master " +
                        "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                        "AND name != 'android_metadata' ORDER BY type, name"
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                        }
                    }
                }
                val tables = schema.filter { it.first == "table" }
                tables.forEach { (_, _, sql) -> snapshot.execSQL(sql) }
                tables.forEach { (_, name, _) ->
                    source.query("SELECT * FROM ${quoteIdentifier(name)}").use { rows ->
                        val columns = rows.columnNames.joinToString(",") { quoteIdentifier(it) }
                        val placeholders = List(rows.columnCount) { "?" }.joinToString(",")
                        snapshot.compileStatement(
                            "INSERT INTO ${quoteIdentifier(name)} ($columns) VALUES ($placeholders)"
                        ).use { insert ->
                            while (rows.moveToNext()) {
                                insert.clearBindings()
                                for (column in 0 until rows.columnCount) {
                                    val index = column + 1
                                    when (rows.getType(column)) {
                                        Cursor.FIELD_TYPE_NULL -> insert.bindNull(index)
                                        Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(index, rows.getLong(column))
                                        Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(index, rows.getDouble(column))
                                        Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(index, rows.getBlob(column))
                                        else -> insert.bindString(index, rows.getString(column))
                                    }
                                }
                                insert.executeInsert()
                            }
                        }
                    }
                }
                schema.filter { it.first != "table" }.forEach { (_, _, sql) -> snapshot.execSQL(sql) }
                snapshot.version = source.version
                snapshot.setTransactionSuccessful()
            } finally {
                snapshot.endTransaction()
            }
            snapshot.rawQuery("PRAGMA integrity_check", null).use { result ->
                check(result.moveToFirst() && result.getString(0) == "ok") {
                    "Exported database failed its integrity check"
                }
            }
        }
    }
}

private fun quoteIdentifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
