package com.chiron.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chiron.core.database.migrations.ChironMigrations
import com.chiron.core.database.seed.DatabaseInitialSeeder
import com.chiron.core.database.dao.ExerciseDao
import com.chiron.core.database.dao.BodyWeightDao
import com.chiron.core.database.dao.ExerciseEntryDao
import com.chiron.core.database.dao.ExercisePrDao
import com.chiron.core.database.dao.SetEntryDao
import com.chiron.core.database.dao.WorkoutSessionDao
import com.chiron.core.database.dao.TimerPresetDao
import com.chiron.core.model.Exercise
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.model.ExerciseEntry
import com.chiron.core.model.ExercisePr
import com.chiron.core.model.SetEntry
import com.chiron.core.model.WorkoutSession
import com.chiron.core.model.TimerPreset
import com.chiron.core.database.dao.Exercise1rmEstimateDao
import com.chiron.core.database.dao.GoalDao
import com.chiron.core.database.dao.VolumeAnalyticsDao
import com.chiron.core.model.Exercise1rmEstimate
import com.chiron.core.model.Goal
import com.chiron.core.model.GoalExercise

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        ExerciseEntry::class,
        SetEntry::class,
        TimerPreset::class,
        ExercisePr::class,
        Exercise1rmEstimate::class,
        Goal::class,
        GoalExercise::class,
        BodyWeightEntry::class
    ],
    version = 15,
    exportSchema = false
)
abstract class ChironDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun setEntryDao(): SetEntryDao
    abstract fun volumeAnalyticsDao(): VolumeAnalyticsDao
    abstract fun timerPresetDao(): TimerPresetDao
    abstract fun exercisePrDao(): ExercisePrDao
    abstract fun exercise1rmEstimateDao(): Exercise1rmEstimateDao
    abstract fun goalDao(): GoalDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        @Volatile
        private var INSTANCE: ChironDatabase? = null

        fun getInstance(context: Context): ChironDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChironDatabase::class.java,
                    "chiron_database"
                )
                .addMigrations(*ChironMigrations.ALL)
                .fallbackToDestructiveMigrationOnDowngrade()
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseInitialSeeder.callback)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
