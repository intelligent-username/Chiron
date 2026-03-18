package com.chiron.app.data

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
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.dao.TimerPresetDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.ExercisePr
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import com.chiron.app.data.entities.TimerPreset
import com.chiron.app.util.Jaccard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChironRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val timerPresetDao: TimerPresetDao,
    private val exercisePrDao: ExercisePrDao
) {
    data class ExportedData(
        val uri: Uri,
        val locationLabel: String,
        val fileName: String
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise ops
    // ─────────────────────────────────────────────────────────────────────────

    val exercisesFlow: Flow<List<Exercise>> = exerciseDao.getExercisesFlow()

    val archivedExercisesFlow: Flow<List<Exercise>> = exerciseDao.getAllExercisesFlow()
        .map { exercises -> exercises.filter { it.archived != 0 } }

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insertExercise(exercise)

    suspend fun updateExercise(exercise: Exercise) = exerciseDao.updateExercise(exercise)

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getById(id)

    suspend fun getExerciseByName(name: String): Exercise? = exerciseDao.getByName(name)

    suspend fun archiveExercise(id: Long) = exerciseDao.archive(id)

    suspend fun unarchiveExercise(id: Long) = exerciseDao.unarchive(id)

    /**
     * Search exercises using Jaccard similarity on tokenized names.
     * Tie-break by recency (lower ID = older, so prefer higher ID).
     */
    suspend fun searchExercises(query: String, archived: Boolean = false, limit: Int = 10): List<Exercise> {
        if (query.isBlank()) return emptyList()
        val allExercises = if (archived) exerciseDao.getAllArchived() else exerciseDao.getAllNonArchived()
        return Jaccard.rankBySimilarity(query, allExercises, { it.name }, limit)

    }

    suspend fun getAllExercises(): List<Exercise> = exerciseDao.getAllNonArchived()

    // ─────────────────────────────────────────────────────────────────────────
    // Last session preview (for press-and-hold reference)
    // ─────────────────────────────────────────────────────────────────────────

    data class LastSessionPreview(
        val dateLabel: String,
        val sets: List<SetEntry>,
        val notes: String? = null
    )

    data class SupersetExercisePreview(
        val exerciseId: Long,
        val exerciseName: String,
        val iconName: String?,
        val sets: List<SetEntry>
    )

    data class LastSessionSupersetPreview(
        val dateLabel: String,
        val exercises: List<SupersetExercisePreview>,
        val notes: String?
    )

    suspend fun getLastSessionPreview(exerciseId: Long, currentWorkoutId: Long): LastSessionPreview? {
        val entry = exerciseEntryDao.getMostRecentEntryForExercise(exerciseId, currentWorkoutId)
            ?: return null
        val sets = setEntryDao.getSetsForEntrySync(entry.id)
        if (sets.isEmpty()) return null

        val workout = workoutSessionDao.getById(entry.workoutId) ?: return null
        val dateLabel = try {
            val date = java.time.LocalDate.parse(workout.dateIso)
            val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            "$dayOfWeek, $month ${date.dayOfMonth}"
        } catch (e: Exception) {
            workout.dateIso
        }

        return LastSessionPreview(dateLabel = dateLabel, sets = sets, notes = entry.notes)
    }

    suspend fun getLastSessionSupersetPreview(
        currentEntryId: Long,
        allCurrentEntries: List<ExerciseEntry>,
        currentWorkoutId: Long
    ): LastSessionSupersetPreview? {
        // Find the current entry
        val currentEntry = allCurrentEntries.firstOrNull { it.id == currentEntryId } ?: return null
        
        // If not part of a superset, return null
        if (currentEntry.groupId == null || currentEntry.sequenceType == "NONE") {
            return null
        }
        
        // Get all entries in this superset group, sorted by slot index
        val supersetGroupId = currentEntry.groupId
        val supersetEntries = allCurrentEntries
            .filter { it.groupId == supersetGroupId }
            .sortedBy { it.slotIndex }
        
        if (supersetEntries.isEmpty()) return null
        
        // For each entry in the superset, get the previous session preview
        val exercises = mutableListOf<SupersetExercisePreview>()
        var dateLabel = ""
        var notes: String? = null
        
        for (entry in supersetEntries) {
            val prevEntry = exerciseEntryDao.getMostRecentEntryForExercise(entry.exerciseId, currentWorkoutId)
                ?: continue
            val sets = setEntryDao.getSetsForEntrySync(prevEntry.id)
            if (sets.isEmpty()) continue
            
            val exercise = exerciseDao.getById(entry.exerciseId) ?: continue
            
            // Get date from the first entry's workout
            if (dateLabel.isEmpty()) {
                val workout = workoutSessionDao.getById(prevEntry.workoutId) ?: continue
                dateLabel = try {
                    val date = java.time.LocalDate.parse(workout.dateIso)
                    val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                    val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                    "$dayOfWeek, $month ${date.dayOfMonth}"
                } catch (e: Exception) {
                    workout.dateIso
                }
                notes = prevEntry.notes
            }
            
            exercises.add(SupersetExercisePreview(
                exerciseId = entry.exerciseId,
                exerciseName = exercise.name,
                iconName = exercise.iconName,
                sets = sets
            ))
        }
        
        return if (exercises.size > 1) {
            LastSessionSupersetPreview(dateLabel = dateLabel, exercises = exercises, notes = notes)
        } else {
            null // Only return superset preview if there are multiple exercises
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Workout session operations
    // ─────────────────────────────────────────────────────────────────────────

    val workoutsFlow: Flow<List<WorkoutSession>> = workoutSessionDao.getWorkoutsFlow()

    val archivedWorkoutsFlow: Flow<List<WorkoutSession>> = workoutSessionDao.getArchivedWorkoutsFlow()

    val dayTagsFlow: Flow<List<String>> = workoutSessionDao.getDistinctDayTagsFlow()

    suspend fun insertWorkout(session: WorkoutSession): Long = workoutSessionDao.insertWorkout(session)

    suspend fun updateWorkout(session: WorkoutSession) = workoutSessionDao.updateWorkout(session)

    suspend fun getWorkoutById(id: Long): WorkoutSession? = workoutSessionDao.getById(id)

    fun getWorkoutsByDayTag(dayTag: String): Flow<List<WorkoutSession>> =
        workoutSessionDao.getByDayTagFlow(dayTag)

    suspend fun archiveWorkout(id: Long) = workoutSessionDao.archive(id)

    suspend fun unarchiveWorkout(id: Long) = workoutSessionDao.unarchive(id)

    suspend fun permanentlyDeleteWorkout(id: Long) {
        val affectedExerciseIds = exerciseEntryDao.getEntriesForWorkoutSync(id)
            .map { it.exerciseId }
            .distinct()

        workoutSessionDao.deleteById(id)

        affectedExerciseIds.forEach { exerciseId ->
            rebuildPrsForExercise(exerciseId)
        }
    }

    /**
     * Deep-copy a workout with today's date. All entries and sets are duplicated by value.
     * Returns the new workout's ID.
     */
    suspend fun duplicateWorkout(sourceWorkoutId: Long): Long {
        val source = workoutSessionDao.getById(sourceWorkoutId) ?: return -1L

        val now = java.time.Instant.now()
        val todayIso = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

        val newSession = source.copy(
            id = 0L,
            dateIso = todayIso,
            dateUtc = now.toEpochMilli()
        )
        val newWorkoutId = workoutSessionDao.insertWorkout(newSession)

        // Copy all exercise entries
        val sourceEntries = exerciseEntryDao.getEntriesForWorkoutSync(sourceWorkoutId)
        // Map old entry IDs to new entry IDs (for groupId references)
        val entryIdMap = mutableMapOf<Long, Long>()

        for (entry in sourceEntries) {
            val newEntry = entry.copy(
                id = 0L,
                workoutId = newWorkoutId,
                groupId = null // will fix after all entries are created
            )
            val newEntryId = exerciseEntryDao.insertEntry(newEntry)
            entryIdMap[entry.id] = newEntryId

            // Copy all sets for this entry
            val sourceSets = setEntryDao.getSetsForEntrySync(entry.id)
            for (set in sourceSets) {
                setEntryDao.insertSet(set.copy(
                    id = 0L,
                    exerciseEntryId = newEntryId,
                    timestampUtc = now.toEpochMilli()
                ))
            }
        }

        // Fix groupId references for superset entries
        for (entry in sourceEntries) {
            val oldGroupId = entry.groupId ?: continue
            val newEntryId = entryIdMap[entry.id] ?: continue
            val newGroupId = entryIdMap[oldGroupId] ?: continue
            val currentEntry = exerciseEntryDao.getById(newEntryId) ?: continue
            exerciseEntryDao.updateEntry(currentEntry.copy(groupId = newGroupId))
        }

        return newWorkoutId
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exercise entry operations
    // ─────────────────────────────────────────────────────────────────────────

    fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>> =
        exerciseEntryDao.getEntriesForWorkout(workoutId)

    suspend fun insertExerciseEntry(entry: ExerciseEntry): Long = exerciseEntryDao.insertEntry(entry)

    suspend fun updateExerciseEntry(entry: ExerciseEntry) = exerciseEntryDao.updateEntry(entry)

    suspend fun getNextSlotIndex(workoutId: Long): Int =
        (exerciseEntryDao.getMaxSlotIndex(workoutId) ?: 0) + 1

    suspend fun deleteExerciseEntry(workoutId: Long, entryId: Long) {
        val entry = exerciseEntryDao.getById(entryId)
        exerciseEntryDao.deleteAndReindex(workoutId, entryId)
        if (entry != null) rebuildPrsForExercise(entry.exerciseId)
    }

    // ──────────────────────
    // Set entry operations
    // ──────────────────────

    fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>> = setEntryDao.getSetsForEntry(entryId)

    suspend fun insertSet(set: SetEntry): Long = setEntryDao.insertSet(set)

    suspend fun insertSetAndEvaluateHistoricalPr(set: SetEntry): Long {
        val newSetId = setEntryDao.insertSet(set)
        updateSetAndEvaluateHistoricalPr(set.copy(id = newSetId))
        return newSetId
    }

    suspend fun updateSet(set: SetEntry) = setEntryDao.updateSet(set)

    /**
     * Update one set and evaluate its historical PR flag once, relative to what existed
     * up to the workout day for the same exercise + reps.
     *
     * This does not rebuild or rewrite other sets' `is_pr` flags.
     */
    suspend fun updateSetAndEvaluateHistoricalPr(set: SetEntry) {
        val oldSet = if (set.id > 0) setEntryDao.getById(set.id) else null
        setEntryDao.updateSet(set)

        val reps = set.reps
        val weight = set.weightLbs
        val shouldCheck = reps != null && weight != null && set.isFailed == 0

        if (!shouldCheck) {
            if (set.isPr != 0) {
                setEntryDao.updateSet(set.copy(isPr = 0))
            }
            return
        }

        val exerciseId = setEntryDao.getExerciseIdForEntry(set.exerciseEntryId) ?: return
        val workoutId = setEntryDao.getWorkoutIdForEntry(set.exerciseEntryId) ?: return
        val workout = workoutSessionDao.getById(workoutId) ?: return

        val maxWeightSoFar = setEntryDao.getMaxWeightForExerciseRepsUpToWorkoutDate(
            exerciseId = exerciseId,
            reps = reps,
            upToWorkoutDateUtc = workout.dateUtc,
            excludeSetId = set.id
        )

        val isHistoricalPr = maxWeightSoFar == null || weight > maxWeightSoFar
        val newIsPr = if (isHistoricalPr) 1 else 0

        if (set.isPr != newIsPr) {
            setEntryDao.updateSet(set.copy(isPr = newIsPr))
        }

        syncGlobalPrBucket(exerciseId, reps)
        val oldReps = oldSet?.reps
        if (oldReps != null && oldReps != reps) {
            syncGlobalPrBucket(exerciseId, oldReps)
        }
    }

    private suspend fun syncGlobalPrBucket(exerciseId: Long, reps: Int) {
        val bestSet = setEntryDao.getBestSetForExerciseAndReps(exerciseId, reps)
        if (bestSet == null || bestSet.weightLbs == null) {
            exercisePrDao.deleteForExerciseAndReps(exerciseId, reps)
            return
        }

        exercisePrDao.upsert(
            ExercisePr(
                exerciseId = exerciseId,
                reps = reps,
                weightLbs = bestSet.weightLbs,
                setId = bestSet.id,
                timestampUtc = bestSet.timestampUtc
            )
        )
    }

    suspend fun getNextSetIndex(entryId: Long): Int =
        (setEntryDao.getMaxSetIndex(entryId) ?: 0) + 1

    suspend fun deleteSet(entryId: Long, setId: Long) {
        val set = setEntryDao.getById(setId)
        val entry = exerciseEntryDao.getById(entryId)
        setEntryDao.deleteAndReindex(entryId, setId)
        if (entry != null && set != null && set.reps != null) {
            syncGlobalPrBucket(entry.exerciseId, set.reps)
        }
    }

    /**
     * Get the last set recorded for an exercise (for autofill).
     */
    suspend fun getLastSetForExercise(exerciseId: Long): SetEntry? =
        setEntryDao.getLastSetForExercise(exerciseId)

    // ─────────────────────────────────────────────────────────────────────────
    // PR Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full PR rebuild for one exercise. Safe to call after deletions or bulk fixes.
     *
     * 1. Clears all `is_pr` flags on every set belonging to this exercise.
     * 2. Deletes all rows in `exercise_pr` for this exercise.
     * 3. Re-scans all non-failed sets with both weight and reps filled in.
     * 4. For each rep count, finds the single heaviest set and marks it
     *    `is_pr = 1`, then upserts it into `exercise_pr`.
     *
     * This approach is correct regardless of edits, re-orders, or deletions:
     * the truth is always derived from what's currently in the database.
     */
    suspend fun rebuildPrsForExercise(exerciseId: Long) {
        // Step 1 & 2: wipe stale state
        setEntryDao.clearPrFlagsForExercise(exerciseId)
        exercisePrDao.clearAllForExercise(exerciseId)

        // Step 3: load all qualifying sets (existing DAO query, already joins exercise_entry)
        val allSets = setEntryDao.getAllSetsForExercise(exerciseId)

        // Step 4: find max weight per rep count
        val bestPerReps = mutableMapOf<Int, SetEntry>()
        for (set in allSets) {
            val reps = set.reps ?: continue
            val weight = set.weightLbs ?: continue
            if (set.isFailed != 0) continue
            val current = bestPerReps[reps]
            if (current == null || weight > current.weightLbs!!) {
                bestPerReps[reps] = set
            }
        }

        // Step 5: persist
        for ((_, set) in bestPerReps) {
            setEntryDao.updateSet(set.copy(isPr = 1))
            exercisePrDao.upsert(
                ExercisePr(
                    exerciseId = exerciseId,
                    reps = set.reps!!,
                    weightLbs = set.weightLbs!!,
                    setId = set.id,
                    timestampUtc = set.timestampUtc
                )
            )
        }
    }

    /** Get all current global PRs for an exercise, ordered by rep count. */
    suspend fun getAllPrsForExercise(exerciseId: Long): List<ExercisePr> =
        exercisePrDao.getAllForExercise(exerciseId)

    /** Observe current PRs for an exercise as a reactive Flow. */
    fun getPrsForExerciseFlow(exerciseId: Long) =
        exercisePrDao.getAllForExerciseFlow(exerciseId)

    /** Get all exercise IDs that have at least one PR. */
    suspend fun getExerciseIdsWithPrs(): List<Long> =
        exercisePrDao.getExerciseIdsWithPrs()

    // ─────────────────────────────────────────────────────────────────────────
    // Image handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Copy an image from the given URI into app-managed storage.
     * Returns the internal file URI string.
     */
    fun copyImageToStorage(sourceUri: Uri, exerciseId: Long): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(context.filesDir, "images/exercises")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val extension = context.contentResolver.getType(sourceUri)
                ?.substringAfter("/") ?: "jpg"
            val fileName = "${exerciseId}_${System.currentTimeMillis()}.$extension"
            val destFile = File(imagesDir, fileName)

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete an image file from app storage.
     */
    fun deleteImage(imageUri: String): Boolean {
        return try {
            val file = File(Uri.parse(imageUri).path ?: return false)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer Presets operations
    // ─────────────────────────────────────────────────────────────────────────

    val timerPresetsFlow: Flow<List<TimerPreset>> = timerPresetDao.getPresetsFlow()

    suspend fun insertTimerPreset(preset: TimerPreset): Long = timerPresetDao.insertPreset(preset)

    suspend fun updateTimerPreset(preset: TimerPreset) = timerPresetDao.updatePreset(preset)

    suspend fun deleteTimerPreset(preset: TimerPreset) = timerPresetDao.deletePreset(preset)

    suspend fun getTimerPresetById(id: Long): TimerPreset? = timerPresetDao.getPresetById(id)

    /**
     * Export a local snapshot of the Room database into Downloads/Chiron.
     * Returns a MediaStore content URI that can be opened/shared via Android intents.
     */
    fun exportDataSnapshot(): Result<ExportedData> {
        return runCatching {
            val dbName = "chiron_database"
            val dbFile = context.getDatabasePath(dbName)
            require(dbFile.exists()) { "Database file not found" }

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "ce_$timestamp.db"

            // Best-effort WAL checkpoint so DB + WAL are as consistent as possible.
            try {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sqliteDb ->
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
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Chiron")
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
            // Get the actual filename from the URI (content:// URIs need special handling)
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
                require(isSqliteDatabaseFile(importDb)) { "Selected file is not a valid SQLite database" }

                // Now merge the imported database with the current one
                mergeImportedDatabase(importDb)
                "Import successful"
            } finally {
                importDb.delete()
            }
        }
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                // Query ContentResolver for DISPLAY_NAME column
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (columnIndex >= 0) {
                                cursor.getString(columnIndex)
                            } else {
                                uri.lastPathSegment
                            }
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
     * Internal function to merge an imported database with the current one.
     * Handles ID remapping and conflict resolution.
     */
    private suspend fun mergeImportedDatabase(importDbFile: File) {
        SQLiteDatabase.openDatabase(importDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { importDb ->
            // Step 1: Build exercise name -> current ID mapping
            val exerciseMapping = mutableMapOf<Long, Long>() // importId -> currentId
            val importExercisesMap = mutableMapOf<Long, Exercise>() // importId -> exercise data

            importDb.query("exercise", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val iconName = cursor.getString(cursor.getColumnIndexOrThrow("icon_name"))
                    val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))

                    val importExercise = Exercise(
                        id = id,
                        name = name,
                        iconName = iconName,
                        archived = archived
                    )
                    importExercisesMap[id] = importExercise

                    // Strategy: 1) Check if exercise with same ID exists → update it
                    // (preserves history, no key conflict)
                    val byId = exerciseDao.getById(id)
                    if (byId != null) {
                        // Exercise exists with same ID - update it to merge
                        exerciseDao.updateExercise(byId.copy(
                            name = name,
                            iconName = iconName,
                            archived = archived
                        ))
                        exerciseMapping[id] = id
                    } else {
                        // 2) Check if exercise with same name exists → link to it
                        val existing = exerciseDao.getByName(name)
                        if (existing != null) {
                            exerciseMapping[id] = existing.id
                        } else {
                            // 3) Create new exercise
                            val newId = exerciseDao.insertExercise(importExercise.copy(id = 0))
                            exerciseMapping[id] = newId
                        }
                    }
                }
            }

            // Step 2: Import WorkoutSessions (always new, just copy)
            val workoutMapping = mutableMapOf<Long, Long>() // importId -> currentId

            importDb.query("workout_session", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val dayTag = cursor.getString(cursor.getColumnIndexOrThrow("day_tag"))
                    val dateIso = cursor.getString(cursor.getColumnIndexOrThrow("date_iso"))
                    val dateUtc = cursor.getLong(cursor.getColumnIndexOrThrow("date_utc"))
                    val locationTag = cursor.getString(cursor.getColumnIndexOrThrow("location_tag"))
                    val notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                    val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))

                    val newId = workoutSessionDao.insertWorkout(
                        WorkoutSession(
                            id = 0,
                            dayTag = dayTag,
                            dateIso = dateIso,
                            dateUtc = dateUtc,
                            locationTag = locationTag,
                            notes = notes,
                            archived = archived
                        )
                    )
                    workoutMapping[id] = newId
                }
            }

            // Step 3: Import ExerciseEntry and remap IDs
            val entryMapping = mutableMapOf<Long, Long>() // importId -> currentId
            val importEntries = mutableMapOf<Long, ExerciseEntry>()

            importDb.query("exercise_entry", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val workoutId = cursor.getLong(cursor.getColumnIndexOrThrow("workout_id"))
                    val exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id"))
                    val slotIndex = cursor.getInt(cursor.getColumnIndexOrThrow("slot_index"))
                    val groupId = cursor.getLong(cursor.getColumnIndexOrThrow("group_id"))
                    val sequenceType = cursor.getString(cursor.getColumnIndexOrThrow("sequence_type"))
                    val notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                    val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))
                    val numExercisesInSuperset = cursor.getInt(cursor.getColumnIndexOrThrow("num_exercises_in_superset"))

                    val newWorkoutId = workoutMapping[workoutId] ?: return@use
                    val newExerciseId = exerciseMapping[exerciseId] ?: return@use

                    val entry = ExerciseEntry(
                        id = 0,
                        workoutId = newWorkoutId,
                        exerciseId = newExerciseId,
                        slotIndex = slotIndex,
                        groupId = null, // fix later
                        sequenceType = sequenceType,
                        notes = notes,
                        archived = archived,
                        numExercisesInSuperset = numExercisesInSuperset
                    )
                    val newEntryId = exerciseEntryDao.insertEntry(entry)
                    entryMapping[id] = newEntryId
                    importEntries[id] = entry.copy(id = newEntryId)
                }
            }

            // Fix groupId references for supersets
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

            // Step 4: Import SetEntry and remap IDs, also track setId mapping for PRs
            val setIdMapping = mutableMapOf<Long, Long>() // importSetId -> currentSetId
            
            importDb.query("set_entry", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val importSetId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val entryId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_entry_id"))
                    val reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps"))
                    val weightLbs = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_lbs"))
                    val setIndex = cursor.getInt(cursor.getColumnIndexOrThrow("set_index"))
                    val isFailed = cursor.getInt(cursor.getColumnIndexOrThrow("is_failed"))
                    val timestampUtc = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_utc"))
                    val isPr = cursor.getInt(cursor.getColumnIndexOrThrow("is_pr"))

                    val newEntryId = entryMapping[entryId] ?: continue

                    val newSetId = setEntryDao.insertSet(
                        SetEntry(
                            id = 0,
                            exerciseEntryId = newEntryId,
                            reps = reps,
                            weightLbs = weightLbs,
                            setIndex = setIndex,
                            isFailed = isFailed,
                            timestampUtc = timestampUtc,
                            isPr = isPr // preserve original PR flag
                        )
                    )
                    setIdMapping[importSetId] = newSetId
                }
            }

            // Step 5: Import ExercisePr with remapped set_id
            importDb.query("exercise_pr", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val exerciseId = cursor.getLong(cursor.getColumnIndexOrThrow("exercise_id"))
                    val reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps"))
                    val weightLbs = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_lbs"))
                    val setId = cursor.getLong(cursor.getColumnIndexOrThrow("set_id"))
                    val timestampUtc = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp_utc"))

                    val newExerciseId = exerciseMapping[exerciseId] ?: continue
                    val newSetId = setIdMapping[setId] ?: continue

                    // Merge: keep the higher weight (best PR)
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

            // Step 6: Import TimerPresets (merge by label + duration)
            importDb.query("timer_presets", null, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val duration = cursor.getInt(cursor.getColumnIndexOrThrow("duration_seconds"))
                    val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                    val archived = cursor.getInt(cursor.getColumnIndexOrThrow("archived"))

                    // Check if preset with same label and duration exists
                    val existing = timerPresetDao.getPresetByLabelAndDuration(label, duration)
                    if (existing == null) {
                        timerPresetDao.insertPreset(
                            TimerPreset(
                                id = 0,
                                durationSeconds = duration,
                                label = label,
                                archived = archived
                            )
                        )
                    }
                }
            }

            // Step 7: Validation rebuild - ensures PR consistency across exercises
            val importedExerciseIds = exerciseMapping.values.toSet()
            importedExerciseIds.forEach { exerciseId ->
                rebuildPrsForExercise(exerciseId)
            }
        }
    }
}
