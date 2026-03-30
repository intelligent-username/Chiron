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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

            val timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "ce_$timestamp.db"

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
    private suspend fun mergeImportedDatabase(importDbFile: File) {
        SQLiteDatabase.openDatabase(
            importDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).use { importDb ->
            val exerciseMapping = mergeExercises(importDb)
            val workoutMapping = mergeWorkoutSessions(importDb)
            val entryMapping = mergeExerciseEntries(importDb, workoutMapping, exerciseMapping)
            val setIdMapping = mergeSetEntries(importDb, entryMapping)
            mergeExercisePrs(importDb, exerciseMapping, setIdMapping)
            mergeTimerPresets(importDb)

            // Rebuild PRs for all imported exercises to ensure consistency
            exerciseMapping.values.toSet().forEach { exerciseId ->
                onRebuildPrs(exerciseId)
            }
        }
    }

    private suspend fun mergeExercises(
        importDb: SQLiteDatabase
    ): Map<Long, Long> {
        val exerciseMapping = mutableMapOf<Long, Long>()

        importDb.query("exercise", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val iconName = cursor.getString(cursor.getColumnIndexOrThrow("icon_name"))
                val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))

                val importExercise = Exercise(id = id, name = name, iconName = iconName, archived = archived)

                val byId = exerciseDao.getById(id)
                if (byId != null) {
                    exerciseDao.updateExercise(byId.copy(name = name, iconName = iconName, archived = archived))
                    exerciseMapping[id] = id
                } else {
                    val existing = exerciseDao.getByName(name)
                    exerciseMapping[id] = if (existing != null) {
                        existing.id
                    } else {
                        exerciseDao.insertExercise(importExercise.copy(id = 0))
                    }
                }
            }
        }

        return exerciseMapping
    }

    private suspend fun mergeWorkoutSessions(
        importDb: SQLiteDatabase
    ): Map<Long, Long> {
        val workoutMapping = mutableMapOf<Long, Long>()

        importDb.query("workout_session", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val newId = workoutSessionDao.insertWorkout(
                    WorkoutSession(
                        id = 0,
                        dayTag = cursor.getString(cursor.getColumnIndexOrThrow("day_tag")),
                        dateIso = cursor.getString(cursor.getColumnIndexOrThrow("date_iso")),
                        dateUtc = cursor.getLong(cursor.getColumnIndexOrThrow("date_utc")),
                        locationTag = cursor.getString(cursor.getColumnIndexOrThrow("location_tag")),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                        archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))
                    )
                )
                workoutMapping[id] = newId
            }
        }

        return workoutMapping
    }

    private suspend fun mergeExerciseEntries(
        importDb: SQLiteDatabase,
        workoutMapping: Map<Long, Long>,
        exerciseMapping: Map<Long, Long>
    ): Map<Long, Long> {
        val entryMapping = mutableMapOf<Long, Long>()

        importDb.query("exercise_entry", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val workoutId = cursor.getLong(cursor.getColumnIndexOrThrow("workout_id"))
                val exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id"))

                val newWorkoutId = workoutMapping[workoutId] ?: continue
                val newExerciseId = exerciseMapping[exerciseId] ?: continue

                val newEntryId = exerciseEntryDao.insertEntry(
                    ExerciseEntry(
                        id = 0,
                        workoutId = newWorkoutId,
                        exerciseId = newExerciseId,
                        slotIndex = cursor.getInt(cursor.getColumnIndexOrThrow("slot_index")),
                        groupId = null, // fixed in second pass below
                        sequenceType = cursor.getString(cursor.getColumnIndexOrThrow("sequence_type")),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                        archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived")),
                        numExercisesInSuperset = cursor.getInt(
                            cursor.getColumnIndexOrThrow("num_exercises_in_superset")
                        )
                    )
                )
                entryMapping[id] = newEntryId
            }
        }

        // Second pass: fix groupId references for supersets
        importDb.query("exercise_entry", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val groupId = cursor.getLong(cursor.getColumnIndexOrThrow("group_id"))

                if (groupId > 0) {
                    val newEntryId = entryMapping[id] ?: continue
                    val newGroupId = entryMapping[groupId] ?: continue
                    val entry = exerciseEntryDao.getById(newEntryId) ?: continue
                    exerciseEntryDao.updateEntry(entry.copy(groupId = newGroupId))
                }
            }
        }

        return entryMapping
    }

    private suspend fun mergeSetEntries(
        importDb: SQLiteDatabase,
        entryMapping: Map<Long, Long>
    ): Map<Long, Long> {
        val setIdMapping = mutableMapOf<Long, Long>()

        importDb.query("set_entry", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val importSetId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val entryId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_entry_id"))

                val newEntryId = entryMapping[entryId] ?: continue

                val newSetId = setEntryDao.insertSet(
                    SetEntry(
                        id = 0,
                        exerciseEntryId = newEntryId,
                        reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps")),
                        weightLbs = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_lbs")),
                        setIndex = cursor.getInt(cursor.getColumnIndexOrThrow("set_index")),
                        isFailed = cursor.getInt(cursor.getColumnIndexOrThrow("is_failed")),
                        timestampUtc = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_utc")),
                        isPr = cursor.getInt(cursor.getColumnIndexOrThrow("is_pr"))
                    )
                )
                setIdMapping[importSetId] = newSetId
            }
        }

        return setIdMapping
    }

    private suspend fun mergeExercisePrs(
        importDb: SQLiteDatabase,
        exerciseMapping: Map<Long, Long>,
        setIdMapping: Map<Long, Long>
    ) {
        importDb.query("exercise_pr", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id"))
                val reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps"))
                val weightLbs = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_lbs"))
                val setId = cursor.getLong(cursor.getColumnIndexOrThrow("set_id"))
                val timestampUtc = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_utc"))

                val newExerciseId = exerciseMapping[exerciseId] ?: continue
                val newSetId = setIdMapping[setId] ?: continue

                // Keep the higher weight (best PR)
                val existing = exercisePrDao.getForExerciseAndReps(newExerciseId, reps)
                if (existing == null || weightLbs > existing.weightLbs) {
                    exercisePrDao.upsert(
                        ExercisePr(
                            exerciseId = newExerciseId,
                            reps = reps,
                            weightLbs = weightLbs,
                            setId = newSetId,
                            timestampUtc = timestampUtc
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeTimerPresets(importDb: SQLiteDatabase) {
        importDb.query("timer_presets", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val duration = cursor.getInt(cursor.getColumnIndexOrThrow("duration_seconds"))
                val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))

                val existing = timerPresetDao.getPresetByLabelAndDuration(label, duration)
                if (existing == null) {
                    timerPresetDao.insertPreset(
                        TimerPreset(id = 0, durationSeconds = duration, label = label, archived = archived)
                    )
                }
            }
        }
    }
}
