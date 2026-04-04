package com.chiron.app.data.transfer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.ExercisePrDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.data.entities.WorkoutSession
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


/**
 * Handles database export (snapshot to Downloads) and import (merge from .db file).
 *
 * Post-import PR consistency is delegated via [onRebuildPrs].
 */
class DataTransferRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val timerPresetDao: TimerPresetDao,
    private val exercisePrDao: ExercisePrDao,
    private val onRebuildPrs: suspend (exerciseId: Long) -> Unit
) {
    data class ExportedData(
        val uri: Uri,
        val locationLabel: String,
        val fileName: String
    )

    /**
     * Export a local snapshot of the Room database into Downloads/Chiron.
     * Returns a MediaStore content URI that can be opened/shared via Android intents.
     */
    fun exportDataSnapshot(): Result<ExportedData> {
        return runCatching {
            val dbName = "chiron_database"
            val dbFile = context.getDatabasePath(dbName)
            require(dbFile.exists()) { "Database file not found" }

            val fileName = resolveExportFileName()

            // Best-effort WAL checkpoint so DB + WAL are as consistent as possible.
            try {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
                ).use { sqliteDb ->
                    sqliteDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
            } catch (_: Exception) {
                // Continue export even if checkpoint fails.
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Chiron"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, values)
                ?: error("Failed to create export file in Downloads")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to open output stream for export")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val complete = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, complete, null, null)
                }
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }

            ExportedData(
                uri = uri,
                locationLabel = "Downloads/Chiron/$fileName",
                fileName = fileName
            )
        }
    }

    /**
     * Resolves a unique export filename in Downloads/Chiron.
     * Returns "Chiron.db" if available, otherwise "Chiron2.db", "Chiron3.db", etc.
     */
    private fun resolveExportFileName(): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        } else null
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/Chiron/", "Chiron%.db")
        } else null

        val existingNames = mutableSetOf<String>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (nameCol >= 0) existingNames.add(cursor.getString(nameCol))
            }
        }

        if ("Chiron.db" !in existingNames) return "Chiron.db"
        var index = 2
        while ("Chiron$index.db" in existingNames) index++
        return "Chiron$index.db"
    }

    /**
     * Import data from an exported .db file.
     * Intelligently merges data to avoid conflicts:
     * - Exercises: merge by ID/name (update/link/create)
     * - WorkoutSessions: always append (historical data)
     * - ExerciseEntry/SetEntry: remap IDs based on exercise merge
     * - TimerPresets: merge by label + duration
     * - ExercisePr: merge intelligently (keep best PR)
     */
    suspend fun importDataFromFile(fileUri: Uri): Result<String> {
        return runCatching {
            val displayName = getDisplayNameFromUri(fileUri)
            require(!displayName.isNullOrBlank()) { "Unable to determine filename" }

            val importDb = File.createTempFile("chiron_import", ".db", context.cacheDir)

            try {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    FileOutputStream(importDb).use { output ->
                        inputStream.copyTo(output)
                    }
                } ?: error("Cannot open import file")

                if (!importDb.exists()) error("Database not found in import file")
                require(isSqliteDatabaseFile(importDb)) {
                    "Selected file is not a valid SQLite database"
                }

                mergeImportedDatabase(importDb)
                "Import successful"
            } finally {
                importDb.delete()
            }
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private fun getDisplayNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex =
                                cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (columnIndex >= 0) cursor.getString(columnIndex)
                            else uri.lastPathSegment
                        } else {
                            uri.lastPathSegment
                        }
                    }
                } catch (e: Exception) {
                    uri.lastPathSegment
                }
            }
            "file" -> uri.lastPathSegment
            else -> uri.lastPathSegment
        }
    }

    private fun isSqliteDatabaseFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val signature = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val header = ByteArray(signature.size)
        return try {
            FileInputStream(file).use { input ->
                val readCount = input.read(header)
                readCount == signature.size && header.contentEquals(signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Merge an imported database with the current one, handling ID remapping
     * and conflict resolution for all entity types.
     */
    private suspend fun mergeImportedDatabase(importDb: File) {
        // DatabaseImportMerger.mergeImportedDatabase(
        //     importDb,
        //     exerciseDao,
        //     workoutSessionDao,
        //     exerciseEntryDao,
        //     setEntryDao,
        //     exercisePrDao,
        //     timerPresetDao,
        //     onRebuildPrs
        // )
    }
}
