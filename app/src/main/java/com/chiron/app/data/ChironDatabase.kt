package com.chiron.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        ExerciseEntry::class,
        SetEntry::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ChironDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun setEntryDao(): SetEntryDao

    companion object {
        @Volatile
        private var INSTANCE: ChironDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise ADD COLUMN icon_name TEXT DEFAULT 'default'")
            }
        }

        fun getInstance(context: Context): ChironDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChironDatabase::class.java,
                    "chiron_database"
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate default exercises on first launch
                        CoroutineScope(Dispatchers.IO).launch {
                            val exerciseDao = getInstance(context).exerciseDao()
                            val defaultExercises = listOf(
                                Exercise(name = "Bench Press", iconName = "benchpress"),
                                Exercise(name = "Incline Bench Press", iconName = "incline_bench"),
                                Exercise(name = "Machine Chest Press", iconName = "chest_press"),
                                Exercise(name = "Overhead Press", iconName = "overhead_press"),
                                Exercise(name = "Chest Flies", iconName = "machine"),
                                Exercise(name = "Hammer Curls", iconName = "curl"),
                                Exercise(name = "Machine Preacher Curls", iconName = "curl"),
                                Exercise(name = "Deadlift", iconName = "deadlift"),
                                Exercise(name = "Leg Extension", iconName = "leg_extension"),
                                Exercise(name = "Leg Curl", iconName = "leg_curl"),
                                Exercise(name = "Squat", iconName = "squat"),
                                Exercise(name = "Leg Raises", iconName = "leg_raise"),
                                Exercise(name = "Tricep Pushdowns", iconName = "pushdown")
                            )
                            defaultExercises.forEach { exerciseDao.insertExercise(it) }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
