package com.chiron.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chiron.app.data.dao.ExerciseDao
import com.chiron.app.data.dao.ExerciseEntryDao
import com.chiron.app.data.dao.SetEntryDao
import com.chiron.app.data.dao.WorkoutSessionDao
import com.chiron.app.data.entities.Exercise
import com.chiron.app.data.entities.ExerciseEntry
import com.chiron.app.data.entities.SetEntry
import com.chiron.app.data.entities.WorkoutSession

@Database(
    entities = [
        Exercise::class,
        WorkoutSession::class,
        ExerciseEntry::class,
        SetEntry::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ChironDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun setEntryDao(): SetEntryDao

    companion object {
        @Volatile
        private var INSTANCE: ChironDatabase? = null

        fun getInstance(context: Context): ChironDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChironDatabase::class.java,
                    "chiron_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
