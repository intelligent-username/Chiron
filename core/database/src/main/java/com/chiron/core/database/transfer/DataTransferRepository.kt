package com.chiron.core.database.transfer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.ExercisePrDao
import com.chiron.core.database.dao.GoalDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.TimerPresetDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.Goal
import com.chiron.core.model.GoalExercise
import com.chiron.core.model.SetEntry
import com.chiron.core.model.TimerPreset
import com.chiron.core.model.WorkoutSession
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles database export (snapshot + SVG icons bundle to Downloads/Chiron)
 * and conflict-free appending import.
 *
 * Post-import PR consistency is delegated via [onRebuildPrs], and custom location
 * synchronization via [onImportLocations].
 */
class DataTransferRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val setEntryDao: SetEntryDao,
    private val timerPresetDao: TimerPresetDao,
    private val exercisePrDao: ExercisePrDao,
    private val goalDao: GoalDao,
    private val onRebuildPrs: suspend (exerciseId: Long) -> Unit,
    private val onImportLocations: (suspend (List<String>) -> Unit)? = null
) {
    data class ExportedData(
        val uri: Uri,
        val locationLabel: String,
        val fileName: String
    )

    /**
     * Export a local snapshot of the Room database + all exercise SVG icons and images
     * as a portable .zip archive into Downloads/Chiron.
     * Returns a MediaStore content URI that can be opened/shared via Android intents.
     */
    fun exportDataSnapshot(): Result<ExportedData> {
        return runCatching {
            val dbName = "chiron_database"
            val dbFile = context.getDatabasePath(dbName)
            require(dbFile.exists()) { "Database file not found" }

            val fileName = resolveExportFileName()

            // Best-effort WAL checkpoint so DB + WAL are consistent.
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
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
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
                    ZipOutputStream(BufferedOutputStream(output)).use { zipOut ->
                        // 1. Database file
                        zipOut.putNextEntry(ZipEntry("chiron_database.db"))
                        FileInputStream(dbFile).use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()

                        // 2. App-stored custom icons (from filesDir/icons/)
                        val customIconsDir = File(context.filesDir, "icons")
                        if (customIconsDir.exists() && customIconsDir.isDirectory) {
                            customIconsDir.listFiles()?.forEach { iconFile ->
                                if (iconFile.isFile) {
                                    zipOut.putNextEntry(ZipEntry("icons/${iconFile.name}"))
                                    FileInputStream(iconFile).use { input -> input.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                }
                            }
                        }

                        // 3. App-stored exercise images (from filesDir/images/exercises/)
                        val imagesDir = File(context.filesDir, "images/exercises")
                        if (imagesDir.exists() && imagesDir.isDirectory) {
                            imagesDir.listFiles()?.forEach { imgFile ->
                                if (imgFile.isFile) {
                                    zipOut.putNextEntry(ZipEntry("images/${imgFile.name}"))
                                    FileInputStream(imgFile).use { input -> input.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                }
                            }
                        }

                        // 4. Bundled asset icons (from assets/icons/)
                        try {
                            val assetIcons = context.assets.list("icons") ?: emptyArray()
                            assetIcons.forEach { assetName ->
                                zipOut.putNextEntry(ZipEntry("icons/$assetName"))
                                context.assets.open("icons/$assetName").use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        } catch (_: Exception) {
                        }
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
     * Returns "Chiron.zip" if available, otherwise "Chiron2.zip", "Chiron3.zip", etc.
     */
    private fun resolveExportFileName(): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND (${MediaStore.Downloads.DISPLAY_NAME} LIKE ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?)"
        } else null
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/Chiron/", "Chiron%.zip", "Chiron%.db")
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

        if ("Chiron.zip" !in existingNames) return "Chiron.zip"
        var index = 2
        while ("Chiron$index.zip" in existingNames) index++
        return "Chiron$index.zip"
    }

    /**
     * Import data from an exported .zip archive or legacy .db file.
     * Appends data to existing state without replacing it:
     * - Exercises: appended if new; matched by name to reuse local ID.
     * - WorkoutSessions: appended into history; deduplicated on exact (dateUtc, dateIso, dayTag).
     * - ExerciseEntry/SetEntry: appended into sessions.
     * - Superset Groups: accurately remapped to local entry IDs.
     * - Locations: extracted from all workouts and appended to custom locations.
     * - Icons/Images: extracted into internal storage. If an icon has the same name, replaces old icon with the same name.
     * - TimerPresets: appended if new.
     * - Goals: appended if new, merged by name.
     * - ExercisePr: rebuilt from scratch post-import for full statistical accuracy.
     */
    suspend fun importDataFromFile(fileUri: Uri): Result<String> {
        return runCatching {
            val displayName = getDisplayNameFromUri(fileUri)
            require(!displayName.isNullOrBlank()) { "Unable to determine filename" }

            val tempImportFile = File.createTempFile("chiron_import", ".tmp", context.cacheDir)

            try {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    FileOutputStream(tempImportFile).use { output ->
                        inputStream.copyTo(output)
                    }
                } ?: error("Cannot open import file")

                if (!tempImportFile.exists()) error("File not found")

                var iconsImportedCount = 0
                val dbFileToMerge: File

                if (isZipFile(tempImportFile)) {
                    // Extract icons and database from zip
                    val targetIconsDir = File(context.filesDir, "icons")
                    if (!targetIconsDir.exists()) targetIconsDir.mkdirs()

                    val targetImagesDir = File(context.filesDir, "images/exercises")
                    if (!targetImagesDir.exists()) targetImagesDir.mkdirs()

                    val extractedDb = File.createTempFile("extracted_chiron", ".db", context.cacheDir)
                    var foundDb = false

                    ZipInputStream(FileInputStream(tempImportFile)).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory) {
                                when {
                                    name.endsWith(".db", ignoreCase = true) || name == "chiron_database" -> {
                                        FileOutputStream(extractedDb).use { out ->
                                            zipIn.copyTo(out)
                                        }
                                        foundDb = true
                                    }
                                    name.startsWith("icons/") -> {
                                        val iconFileName = name.removePrefix("icons/")
                                        if (iconFileName.isNotBlank()) {
                                            val destFile = File(targetIconsDir, iconFileName)
                                            // Conflict resolution: replace old icons with same name
                                            if (destFile.exists()) {
                                                destFile.delete()
                                            }
                                            FileOutputStream(destFile).use { out ->
                                                zipIn.copyTo(out)
                                            }
                                            iconsImportedCount++
                                        }
                                    }
                                    name.startsWith("images/") -> {
                                        val imageFileName = name.removePrefix("images/")
                                        if (imageFileName.isNotBlank()) {
                                            val destFile = File(targetImagesDir, imageFileName)
                                            // Conflict resolution: replace old images with same name
                                            if (destFile.exists()) {
                                                destFile.delete()
                                            }
                                            FileOutputStream(destFile).use { out ->
                                                zipIn.copyTo(out)
                                            }
                                        }
                                    }
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }

                    require(foundDb && isSqliteDatabaseFile(extractedDb)) {
                        "Valid database not found inside backup zip"
                    }
                    dbFileToMerge = extractedDb
                } else if (isSqliteDatabaseFile(tempImportFile)) {
                    dbFileToMerge = tempImportFile
                } else {
                    error("Selected file is neither a Chiron backup (.zip) nor a valid SQLite database (.db)")
                }

                try {
                    mergeImportedDatabase(dbFileToMerge)
                } finally {
                    if (dbFileToMerge != tempImportFile) {
                        dbFileToMerge.delete()
                    }
                }

                if (iconsImportedCount > 0) {
                    "Import successful (appended data & updated $iconsImportedCount icons)"
                } else {
                    "Import successful (appended data)"
                }
            } finally {
                tempImportFile.delete()
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

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val zipSig = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
        val header = ByteArray(4)
        return try {
            FileInputStream(file).use { input ->
                val count = input.read(header)
                count == 4 && header.contentEquals(zipSig)
            }
        } catch (_: Exception) {
            false
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
     * Merge and append an imported database with the current one, handling ID remapping
     * and conflict resolution for all entity types.
     */
    private suspend fun mergeImportedDatabase(importDb: File) {
        val db = SQLiteDatabase.openDatabase(
            importDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )

        try {
            // Track all unique locations across imported workouts
            val importedLocations = mutableSetOf<String>()

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
                        val name = cursor.getString(iName)?.trim() ?: continue
                        if (name.isBlank()) continue

                        val importedArchived = if (iArchived >= 0) cursor.getInt(iArchived) else 0
                        val importedImageUri = if (iImageUri >= 0 && !cursor.isNull(iImageUri)) cursor.getString(iImageUri) else null
                        val importedDesc = if (iDesc >= 0 && !cursor.isNull(iDesc)) cursor.getString(iDesc) else null
                        val importedIcon = if (iIcon >= 0 && !cursor.isNull(iIcon)) cursor.getString(iIcon) else "default"
                        val importedIsWeight = if (iIsWeight >= 0) cursor.getInt(iIsWeight) else 1
                        val importedIsRep = if (iIsRep >= 0) cursor.getInt(iIsRep) else 1
                        val importedIsTime = if (iIsTime >= 0) cursor.getInt(iIsTime) else 0
                        val importedIsDist = if (iIsDist >= 0) cursor.getInt(iIsDist) else 0

                        val existing = exerciseDao.getByNameAnyStatus(name)
                        if (existing != null) {
                            exerciseIdMap[importedId] = existing.id
                            // If imported exercise is active and local was archived, unarchive it
                            if (importedArchived == 0 && existing.archived != 0) {
                                exerciseDao.unarchive(existing.id)
                            }
                            // Update metadata if existing had placeholder / missing details
                            val needsUpdate = (existing.description.isNullOrBlank() && !importedDesc.isNullOrBlank()) ||
                                    (existing.iconName == "default" && importedIcon != "default") ||
                                    (existing.imageUri.isNullOrBlank() && !importedImageUri.isNullOrBlank())
                            if (needsUpdate) {
                                exerciseDao.updateExercise(
                                    existing.copy(
                                        description = existing.description ?: importedDesc,
                                        iconName = if (existing.iconName == "default") importedIcon else existing.iconName,
                                        imageUri = existing.imageUri ?: importedImageUri
                                    )
                                )
                            }
                        } else {
                            val newId = exerciseDao.insertExercise(
                                Exercise(
                                    name = name,
                                    imageUri = importedImageUri,
                                    description = importedDesc,
                                    iconName = importedIcon,
                                    archived = importedArchived,
                                    isWeightBased = importedIsWeight,
                                    isRepBased = importedIsRep,
                                    isTimeBased = importedIsTime,
                                    isDistanceBased = importedIsDist
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
                    val dayTag = cursor.getString(iDayTag) ?: "Untitled Workout"
                    val dateIso = cursor.getString(iDateIso) ?: ""
                    val dateUtc = cursor.getLong(iDateUtc)
                    val locationTag = cursor.getString(iLocation) ?: ""
                    val notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null
                    val archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0
                    val endTime = if (iEndTime >= 0 && !cursor.isNull(iEndTime)) cursor.getLong(iEndTime) else null

                    if (locationTag.isNotBlank()) {
                        importedLocations.add(locationTag.trim())
                    }

                    // Check for existing matching workout session
                    val existing = workoutSessionDao.findMatchingSession(dateUtc, dateIso, dayTag)
                    if (existing != null) {
                        workoutIdMap[importedId] = existing.id
                        // Enhance existing workout if it was missing location/notes/endTime
                        val needsUpdate = (existing.locationTag.isBlank() && locationTag.isNotBlank()) ||
                                (existing.notes.isNullOrBlank() && !notes.isNullOrBlank()) ||
                                (existing.endTimeUtc == null && endTime != null)
                        if (needsUpdate) {
                            workoutSessionDao.updateWorkout(
                                existing.copy(
                                    locationTag = if (existing.locationTag.isBlank()) locationTag else existing.locationTag,
                                    notes = existing.notes ?: notes,
                                    endTimeUtc = existing.endTimeUtc ?: endTime
                                )
                            )
                        }
                    } else {
                        val newId = workoutSessionDao.insertWorkout(
                            WorkoutSession(
                                dayTag = dayTag,
                                dateIso = dateIso,
                                dateUtc = dateUtc,
                                locationTag = locationTag,
                                notes = notes,
                                archived = archived,
                                endTimeUtc = endTime
                            )
                        )
                        workoutIdMap[importedId] = newId
                    }
                }
            }

            // ── 3. Exercise Entries ───────────────────────────────────────────
            // Map: importedEntryId -> localEntryId
            val entryIdMap = mutableMapOf<Long, Long>()
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
                    if (iId < 0 || iWorkoutId < 0 || iExerciseId < 0 || iSlotIndex < 0) continue
                    val importedId = cursor.getLong(iId)
                    val localWorkoutId = workoutIdMap[cursor.getLong(iWorkoutId)] ?: continue
                    val localExerciseId = exerciseIdMap[cursor.getLong(iExerciseId)] ?: continue
                    val slotIndex = cursor.getInt(iSlotIndex)
                    val importedGroupId = if (iGroupId >= 0 && !cursor.isNull(iGroupId)) cursor.getLong(iGroupId) else null
                    val seqType = if (iSeqType >= 0) cursor.getString(iSeqType) ?: "NONE" else "NONE"
                    val notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null
                    val archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0
                    val numSuperset = if (iNumSuperset >= 0) cursor.getInt(iNumSuperset) else 2

                    val existingEntry = exerciseEntryDao.findMatchingEntry(localWorkoutId, localExerciseId, slotIndex)
                    if (existingEntry != null) {
                        entryIdMap[importedId] = existingEntry.id
                        importedGroupIds[existingEntry.id] = importedGroupId
                    } else {
                        val newId = exerciseEntryDao.insertEntry(
                            ExerciseEntry(
                                workoutId = localWorkoutId,
                                exerciseId = localExerciseId,
                                slotIndex = slotIndex,
                                groupId = null, // resolved in second pass below
                                sequenceType = seqType,
                                notes = notes,
                                archived = archived,
                                numExercisesInSuperset = numSuperset
                            )
                        )
                        entryIdMap[importedId] = newId
                        importedGroupIds[newId] = importedGroupId
                    }
                }
            }

            // Remap group_id for superset entries
            for ((localId, importedGroupId) in importedGroupIds) {
                if (importedGroupId != null) {
                    val localGroupId = entryIdMap[importedGroupId]
                    if (localGroupId != null) {
                        val entry = exerciseEntryDao.getById(localId) ?: continue
                        if (entry.groupId != localGroupId) {
                            exerciseEntryDao.updateEntry(entry.copy(groupId = localGroupId))
                        }
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
                    if (iEntryId < 0 || iSetIndex < 0) continue
                    val localEntryId = entryIdMap[cursor.getLong(iEntryId)] ?: continue
                    val setIndex = cursor.getInt(iSetIndex)
                    val weight = if (iWeight >= 0 && !cursor.isNull(iWeight)) cursor.getDouble(iWeight) else null
                    val reps = if (iReps >= 0 && !cursor.isNull(iReps)) cursor.getInt(iReps) else null
                    val duration = if (iDuration >= 0 && !cursor.isNull(iDuration)) cursor.getInt(iDuration) else null
                    val distance = if (iDistance >= 0 && !cursor.isNull(iDistance)) cursor.getDouble(iDistance) else null
                    val isFailed = if (iIsFailed >= 0) cursor.getInt(iIsFailed) else 0
                    val tempo = if (iTempo >= 0 && !cursor.isNull(iTempo)) cursor.getString(iTempo) else null
                    val notes = if (iNotes >= 0 && !cursor.isNull(iNotes)) cursor.getString(iNotes) else null
                    val timestamp = if (iTs >= 0) cursor.getLong(iTs) else System.currentTimeMillis()

                    val existingSet = setEntryDao.findMatchingSet(localEntryId, setIndex)
                    if (existingSet == null) {
                        setEntryDao.insertSet(
                            SetEntry(
                                exerciseEntryId = localEntryId,
                                setIndex = setIndex,
                                weightLbs = weight,
                                reps = reps,
                                durationSeconds = duration,
                                distanceMeters = distance,
                                isFailed = isFailed,
                                tempo = tempo,
                                notes = notes,
                                timestampUtc = timestamp,
                                isPr = 0 // accurately computed on rebuild
                            )
                        )
                    } else if (existingSet.weightLbs == null && existingSet.reps == null && (weight != null || reps != null || duration != null || distance != null)) {
                        setEntryDao.updateSet(
                            existingSet.copy(
                                weightLbs = weight,
                                reps = reps,
                                durationSeconds = duration,
                                distanceMeters = distance,
                                isFailed = isFailed,
                                tempo = tempo,
                                notes = notes ?: existingSet.notes,
                                timestampUtc = timestamp
                            )
                        )
                    }

                    val exerciseId = setEntryDao.getExerciseIdForEntry(localEntryId)
                    if (exerciseId != null) affectedExerciseIds.add(exerciseId)
                }
            }

            // ── 5. Timer Presets ──────────────────────────────────────────────
            val hasTimerPresetsTable = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='timer_presets'", null
            )?.use { it.moveToFirst() } == true

            if (hasTimerPresetsTable) {
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
            }

            // ── 6. Goals & Goal Exercises ─────────────────────────────────────
            val hasGoalTable = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='goal'", null
            )?.use { it.moveToFirst() } == true

            if (hasGoalTable) {
                val goalIdMap = mutableMapOf<Long, Long>()

                db.rawQuery("SELECT * FROM goal", null)?.use { cursor ->
                    val iId = cursor.getColumnIndex("id")
                    val iName = cursor.getColumnIndex("name")
                    val iWeeklyTarget = cursor.getColumnIndex("weekly_target")
                    val iArchived = cursor.getColumnIndex("archived")
                    while (cursor.moveToNext()) {
                        val importedId = cursor.getLong(iId)
                        val name = cursor.getString(iName)?.trim() ?: continue
                        if (name.isBlank()) continue

                        val existing = goalDao.getGoalByNameAnyStatus(name)
                        val localId = if (existing != null) {
                            if (existing.archived != 0 && (iArchived < 0 || cursor.getInt(iArchived) == 0)) {
                                goalDao.updateGoal(existing.copy(archived = 0))
                            }
                            existing.id
                        } else {
                            goalDao.insertGoal(
                                Goal(
                                    name = name,
                                    weeklyTarget = if (iWeeklyTarget >= 0) cursor.getInt(iWeeklyTarget) else 2,
                                    archived = if (iArchived >= 0) cursor.getInt(iArchived) else 0
                                )
                            )
                        }
                        goalIdMap[importedId] = localId
                    }
                }

                val hasGoalExerciseTable = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='goal_exercise'", null
                )?.use { it.moveToFirst() } == true

                if (hasGoalExerciseTable) {
                    db.rawQuery("SELECT * FROM goal_exercise", null)?.use { cursor ->
                        val iGoalId = cursor.getColumnIndex("goal_id")
                        val iExerciseId = cursor.getColumnIndex("exercise_id")
                        while (cursor.moveToNext()) {
                            val localGoalId = goalIdMap[cursor.getLong(iGoalId)] ?: continue
                            val localExerciseId = exerciseIdMap[cursor.getLong(iExerciseId)] ?: continue
                            goalDao.insertJunction(
                                GoalExercise(goalId = localGoalId, exerciseId = localExerciseId)
                            )
                        }
                    }
                }
            }

            // ── 7. Locations Synchronization ─────────────────────────────────
            if (importedLocations.isNotEmpty()) {
                onImportLocations?.invoke(importedLocations.toList())
            }

            // ── 8. Fix Workout End Times ───────────────────────────────────────
            workoutSessionDao.retroactiveInferEndTimes()

            // ── 9. Rebuild PRs for all affected exercises ─────────────────────
            for (exerciseId in affectedExerciseIds) {
                onRebuildPrs(exerciseId)
            }

        } finally {
            db.close()
        }
    }
}
