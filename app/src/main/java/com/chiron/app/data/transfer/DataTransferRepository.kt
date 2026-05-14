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
     * - Exercises: merge by name (reuse existing ID or insert)
     * - WorkoutSessions: always append (historical data), remap IDs
     * - ExerciseEntry/SetEntry: always insert, remap FKs
     * - TimerPresets: merge by label + duration
     * - ExercisePr: rebuilt from set data after import
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
     *
     * Strategy:
     * - Exercises: match by name; reuse existing ID if found, otherwise insert.
     * - WorkoutSessions: always insert as new (historical data), remap IDs.
     * - ExerciseEntry/SetEntry: always insert as new, remap exercise/workout FKs.
     * - TimerPresets: merge by label+duration to avoid duplicates.
     * - ExercisePr: skip (rebuilt from scratch via onRebuildPrs after import).
     */
    private suspend fun mergeImportedDatabase(importDb: File) {
        val db = SQLiteDatabase.openDatabase(
            importDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )

        try {
            // ── 1. Exercises ──────────────────────────────────────────────────
            // Map: importedExerciseId -> localExerciseId
            val exerciseIdMap = mutableMapOf<Long, Long>()

            db.rawQuery("SELECT * FROM exercise", null)
                ?.use { cursor ->
                    val iId = cursor.getColumnIndex("id")
                    val iName = cursor.getColumnIndex("name")
                    val iImageUri = cursor.getColumnIndex("image_uri")
                    val iDesc = cursor.getColumnIndex("description")
                    val iIcon = cursor.getColumnIndex("icon_name")
                    val iArchived = cursor.getColumnIndex("archived")
                    val iIsWeight = cursor.getColumnIndex("is_weight_based")
                    val iIsRep = cursor.getColumnIndex("is_rep_based")
                    val iIsTime = cursor.getColumnIndex("is_time_based")
                    val iIsDist = cursor.getColumnIndex("is_distance_based")
                    while (cursor.moveToNext()) {
                        val importedId = cursor.getLong(iId)
                        val name = cursor.getString(iName) ?: continue
                        val existing = exerciseDao.getByName(name)
                        if (existing != null) {
                            exerciseIdMap[importedId] = existing.id
                        } else {
                            val newId = exerciseDao.insertExercise(
                                Exercise(
                                    name = name,
                                    imageUri = if (iImageUri >= 0 && !cursor.isNull(iImageUri)) cursor.getString(iImageUri) else null,
                                    description = if (iDesc >= 0 && !cursor.isNull(iDesc)) cursor.getString(iDesc) else null,
                                    iconName = if (iIcon >= 0 && !cursor.isNull(iIcon)) cursor.getString(iIcon) else "default",
                                    archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0,
                                    isWeightBased = if (iIsWeight >= 0) cursor.getInt(iIsWeight) else 1,
                                    isRepBased = if (iIsRep >= 0) cursor.getInt(iIsRep) else 1,
                                    isTimeBased = if (iIsTime >= 0) cursor.getInt(iIsTime) else 0,
                                    isDistanceBased = if (iIsDist >= 0) cursor.getInt(iIsDist) else 0
                                )
                            )
                            exerciseIdMap[importedId] = newId
                        }
                    }
                }

            // ── 2. Workout Sessions ───────────────────────────────────────────
            // Map: importedWorkoutId -> localWorkoutId
            val workoutIdMap = mutableMapOf<Long, Long>()

            db.rawQuery("SELECT * FROM workout_session", null)?.use { cursor ->
                val iId = cursor.getColumnIndex("id")
                val iDayTag = cursor.getColumnIndex("day_tag")
                val iDateIso = cursor.getColumnIndex("date_iso")
                val iDateUtc = cursor.getColumnIndex("date_utc")
                val iLocation = cursor.getColumnIndex("location_tag")
                val iNotes = cursor.getColumnIndex("notes")
                val iArchived = cursor.getColumnIndex("archived")
                val iEndTime = cursor.getColumnIndex("end_time_utc")
                while (cursor.moveToNext()) {
                    val importedId = cursor.getLong(iId)
                    val newId = workoutSessionDao.insertWorkout(
                        WorkoutSession(
                            dayTag = cursor.getString(iDayTag) ?: "",
                            dateIso = cursor.getString(iDateIso) ?: "",
                            dateUtc = cursor.getLong(iDateUtc),
                            locationTag = cursor.getString(iLocation) ?: "",
                            notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null,
                            archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0,
                            endTimeUtc = if (iEndTime >= 0 && !cursor.isNull(iEndTime)) cursor.getLong(iEndTime) else null
                        )
                    )
                    workoutIdMap[importedId] = newId
                }
            }

            // ── 3. Exercise Entries ───────────────────────────────────────────
            // Map: importedEntryId -> localEntryId
            val entryIdMap = mutableMapOf<Long, Long>()
            // Also need to track imported groupId -> so we can remap later
            val importedGroupIds = mutableMapOf<Long, Long?>() // localEntryId -> importedGroupId

            db.rawQuery("SELECT * FROM exercise_entry", null)?.use { cursor ->
                val iId = cursor.getColumnIndex("id")
                val iWorkoutId = cursor.getColumnIndex("workout_id")
                val iExerciseId = cursor.getColumnIndex("exercise_id")
                val iSlotIndex = cursor.getColumnIndex("slot_index")
                val iGroupId = cursor.getColumnIndex("group_id")
                val iSeqType = cursor.getColumnIndex("sequence_type")
                val iNotes = cursor.getColumnIndex("notes")
                val iArchived = cursor.getColumnIndex("archived")
                val iNumSuperset = cursor.getColumnIndex("num_exercises_in_superset")
                while (cursor.moveToNext()) {
                    val importedId = cursor.getLong(iId)
                    val localWorkoutId = workoutIdMap[cursor.getLong(iWorkoutId)] ?: continue
                    val localExerciseId = exerciseIdMap[cursor.getLong(iExerciseId)] ?: continue
                    val importedGroupId = if (iGroupId >= 0 && !cursor.isNull(iGroupId)) cursor.getLong(iGroupId) else null
                    val newId = exerciseEntryDao.insertEntry(
                        ExerciseEntry(
                            workoutId = localWorkoutId,
                            exerciseId = localExerciseId,
                            slotIndex = cursor.getInt(iSlotIndex),
                            groupId = null, // set after all entries are inserted + remapped
                            sequenceType = if (iSeqType >= 0) cursor.getString(iSeqType) ?: "NONE" else "NONE",
                            notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null,
                            archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0,
                            numExercisesInSuperset = if (iNumSuperset >= 0) cursor.getInt(iNumSuperset) else 2
                        )
                    )
                    entryIdMap[importedId] = newId
                    importedGroupIds[newId] = importedGroupId
                }
            }

            // Remap group_id for superset entries now that all entries have local IDs
            for ((localId, importedGroupId) in importedGroupIds) {
                if (importedGroupId != null) {
                    val localGroupId = entryIdMap[importedGroupId]
                    if (localGroupId != null) {
                        val entry = exerciseEntryDao.getById(localId) ?: continue
                        exerciseEntryDao.updateEntry(entry.copy(groupId = localGroupId))
                    }
                }
            }

            // ── 4. Set Entries ────────────────────────────────────────────────
            val affectedExerciseIds = mutableSetOf<Long>()

            db.rawQuery("SELECT * FROM set_entry", null)?.use { cursor ->
                val iEntryId = cursor.getColumnIndex("exercise_entry_id")
                val iSetIndex = cursor.getColumnIndex("set_index")
                val iWeight = cursor.getColumnIndex("weight_lbs")
                val iReps = cursor.getColumnIndex("reps")
                val iDuration = cursor.getColumnIndex("duration_seconds")
                val iDistance = cursor.getColumnIndex("distance_meters")
                val iIsFailed = cursor.getColumnIndex("is_failed")
                val iTempo = cursor.getColumnIndex("tempo")
                val iNotes = cursor.getColumnIndex("notes")
                val iTs = cursor.getColumnIndex("timestamp_utc")
                while (cursor.moveToNext()) {
                    val localEntryId = entryIdMap[cursor.getLong(iEntryId)] ?: continue
                    setEntryDao.insertSet(
                        SetEntry(
                            exerciseEntryId = localEntryId,
                            setIndex = cursor.getInt(iSetIndex),
                            weightLbs = if (iWeight >= 0 && !cursor.isNull(iWeight)) cursor.getDouble(iWeight) else null,
                            reps = if (iReps >= 0 && !cursor.isNull(iReps)) cursor.getInt(iReps) else null,
                            durationSeconds = if (iDuration >= 0 && !cursor.isNull(iDuration)) cursor.getInt(iDuration) else null,
                            distanceMeters = if (iDistance >= 0 && !cursor.isNull(iDistance)) cursor.getDouble(iDistance) else null,
                            isFailed = if (iIsFailed >= 0) cursor.getInt(iIsFailed) else 0,
                            tempo = if (iTempo >= 0 && !cursor.isNull(iTempo)) cursor.getString(iTempo) else null,
                            notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null,
                            timestampUtc = if (iTs >= 0) cursor.getLong(iTs) else System.currentTimeMillis(),
                            isPr = 0 // will be rebuilt
                        )
                    )
                    val exerciseId = setEntryDao.getExerciseIdForEntry(localEntryId)
                    if (exerciseId != null) affectedExerciseIds.add(exerciseId)
                }
            }

            // ── 5. Timer Presets ──────────────────────────────────────────────
            db.rawQuery("SELECT duration_seconds, label, archived FROM timer_presets", null)
                ?.use { cursor ->
                    val iDuration = cursor.getColumnIndex("duration_seconds")
                    val iLabel = cursor.getColumnIndex("label")
                    val iArchived = cursor.getColumnIndex("archived")
                    while (cursor.moveToNext()) {
                        val duration = cursor.getInt(iDuration)
                        val label = cursor.getString(iLabel) ?: continue
                        val exists = timerPresetDao.getPresetByLabelAndDuration(label, duration)
                        if (exists == null) {
                            timerPresetDao.insertPreset(
                                TimerPreset(
                                    durationSeconds = duration,
                                    label = label,
                                    archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0
                                )
                            )
                        }
                    }
                }

            // ── 6. Fix Workout End Times ───────────────────────────────────────
            // Retroactively infer end times for workouts that may lack them (e.g., from old exports)
            workoutSessionDao.retroactiveInferEndTimes()

            // ── 7. Rebuild PRs for all affected exercises ─────────────────────
            for (exerciseId in affectedExerciseIds) {
                onRebuildPrs(exerciseId)
            }

        } finally {
            db.close()
        }
    }
}
