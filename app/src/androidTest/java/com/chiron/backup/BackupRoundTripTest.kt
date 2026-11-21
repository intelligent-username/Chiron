package com.chiron.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chiron.core.common.UserSettingsRepository
import com.chiron.core.database.ChironDatabase
import com.chiron.core.database.ChironRepository
import com.chiron.core.database.transfer.BackupArchive
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.model.Exercise
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** All databases, preferences and assets live under isolated temporary roots, never real app data. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val databases = mutableListOf<ChironDatabase>()
    private val roots = mutableListOf<File>()

    @After
    fun cleanup() {
        databases.forEach { it.close() }
        roots.forEach { it.deleteRecursively() }
    }

    @Test
    fun roundTripRestoresWalRowsBodyweightSettingsAndImagesIdempotently() = runBlocking {
        val sourceContext = isolatedContext()
        val destinationContext = isolatedContext()
        val sourceDb = openDatabase(sourceContext)
        val destinationDb = openDatabase(destinationContext)
        val sourceSettings = UserSettingsRepository(sourceContext)
        val destinationSettings = UserSettingsRepository(destinationContext)
        sourceSettings.setDisplayInKg(true)
        sourceSettings.setShowWorkoutVolume(true)
        sourceSettings.addCustomLocation("Unused custom location")
        val image = File(sourceContext.filesDir, "images/exercises/7_123.jpg").apply {
            parentFile!!.mkdirs()
            writeText("imported image")
        }
        val exerciseId = sourceDb.exerciseDao().insertExercise(
            Exercise(
                name = "Bodyweight row", isBodyweight = 1, percentBodyweight = 60.0,
                imageUri = Uri.fromFile(image).toString()
            )
        )
        val workoutId = sourceDb.workoutSessionDao().insertWorkout(
            WorkoutSession(dayTag = "Test", dateUtc = 1_700_000_000_000L, dateIso = "2023-11-14", locationTag = "Gym")
        )
        val entryId = sourceDb.exerciseEntryDao().insertEntry(
            ExerciseEntry(workoutId = workoutId, exerciseId = exerciseId, slotIndex = 1)
        )
        sourceDb.setEntryDao().insertSet(
            SetEntry(exerciseEntryId = entryId, setIndex = 1, weightLbs = 20.0, reps = 10, timestampUtc = 1_700_000_000_000L)
        )
        sourceDb.bodyWeightDao().insert(
            BodyWeightEntry(timestampUtc = 1_700_000_000_000L, weightLbs = 180.0, note = "weigh-in")
        )
        val oldImage = File(destinationContext.filesDir, "images/exercises/${image.name}").apply {
            parentFile!!.mkdirs()
            writeText("old image")
        }
        val source = repository(sourceContext, sourceDb, sourceSettings)
        val destination = repository(destinationContext, destinationDb, destinationSettings)
        val archive = File(sourceContext.cacheDir, "backup.zip")
        source.exportDataSnapshot(Uri.fromFile(archive)).getOrThrow()
        destination.importDataFromFile(Uri.fromFile(archive)).getOrThrow()
        destination.importDataFromFile(Uri.fromFile(archive)).getOrThrow()

        val restored = destinationDb.exerciseDao().getByNameAnyStatus("Bodyweight row")!!
        assertEquals(1, restored.isBodyweight)
        assertEquals(60.0, restored.percentBodyweight, 0.0)
        assertEquals(Uri.fromFile(oldImage).toString(), restored.imageUri)
        assertEquals("imported image", oldImage.readText())
        assertEquals(1, destinationDb.bodyWeightDao().getAllSync().size)
        assertEquals("weigh-in", destinationDb.bodyWeightDao().getAllSync().single().note)
        assertEquals(1, destinationDb.workoutSessionDao().getWorkoutsFlow().first().size)
        assertTrue(destinationSettings.displayInKgFlow.first())
        assertTrue(destinationSettings.showWorkoutVolumeFlow.first())
        assertTrue("Unused custom location" in destinationSettings.customLocationsFlow.first())
    }

    @Test
    fun failedMergeRollsBackDatabaseAndDoesNotOverwriteImages() = runBlocking {
        val sourceContext = isolatedContext()
        val destinationContext = isolatedContext()
        val sourceDb = openDatabase(sourceContext)
        val destinationDb = openDatabase(destinationContext)
        val sourceSettings = UserSettingsRepository(sourceContext)
        val destinationSettings = UserSettingsRepository(destinationContext)
        sourceDb.exerciseDao().insertExercise(Exercise(name = "Must roll back"))
        val image = File(sourceContext.filesDir, "images/exercises/same.jpg").apply {
            parentFile!!.mkdirs()
            writeText("new")
        }
        val original = File(destinationContext.filesDir, "images/exercises/${image.name}").apply {
            parentFile!!.mkdirs()
            writeText("original")
        }
        val archive = File(sourceContext.cacheDir, "good.zip")
        repository(sourceContext, sourceDb, sourceSettings).exportDataSnapshot(Uri.fromFile(archive)).getOrThrow()
        val staged = BackupArchive.extract(archive.inputStream(), File(sourceContext.cacheDir, "unpacked"))
        android.database.sqlite.SQLiteDatabase.openDatabase(
            staged.database.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use {
            it.execSQL(
                "INSERT INTO body_weight_entry(timestamp_utc, weight_lbs, note, source) " +
                    "VALUES (1700000000000, -1, NULL, 'manual')"
            )
        }
        val broken = File(sourceContext.cacheDir, "broken.zip")
        BackupArchive.write(broken.outputStream(), staged.database, sourceSettings.exportBackup(), emptyMap(), listOf(image))
        val result = repository(destinationContext, destinationDb, destinationSettings)
            .importDataFromFile(Uri.fromFile(broken))
        assertTrue(result.isFailure)
        assertNull(destinationDb.exerciseDao().getByNameAnyStatus("Must roll back"))
        assertEquals("original", original.readText())
        assertFalse(destinationSettings.displayInKgFlow.first())
    }

    private fun isolatedContext(): Context {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "backup_test_").toFile().also { roots.add(it) }
        return object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        }
    }

    private fun openDatabase(context: Context): ChironDatabase =
        Room.databaseBuilder(context, ChironDatabase::class.java, "test_${UUID.randomUUID()}.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build().also { databases.add(it) }

    private fun repository(context: Context, db: ChironDatabase, settings: UserSettingsRepository) =
        ChironRepository(
            context, db.exerciseDao(), db.workoutSessionDao(), db.exerciseEntryDao(),
            db.setEntryDao(), db.timerPresetDao(), db.exercisePrDao(), db.exercise1rmEstimateDao(),
            db.goalDao(), bodyWeightDao = db.bodyWeightDao(), volumeAnalyticsDao = db.volumeAnalyticsDao(),
            database = db, settingsRepository = settings
        )
}
