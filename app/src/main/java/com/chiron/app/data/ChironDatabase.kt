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
                        // Populate default exercises on first launch using raw SQL to avoid recursion issues
                        val defaults = listOf(
                            "Bench Press" to "benchpress",
                            "Incline Bench Press" to "incline-bench",
                            "Machine Chest Press" to "chest-press",
                            "Overhead Press" to "overhead-press",
                            "Chest Flies" to "machine",
                            "Lateral Raises" to "lateral-raise",
                            "Push-ups" to "push-up",
                            "Dips" to "dip",
                            
                            "Pull-ups" to "pull-up",
                            "Lat Pulldowns" to "pulldown",
                            "Barbell Rows" to "barbell",
                            "Cable Rows" to "cables",
                            "Deadlift" to "deadlift",
                            
                            "Squat" to "squat",
                            "Leg Press" to "leg-press",
                            "Lunges" to "lunge",
                            "Leg Extension" to "leg-extension",
                            "Leg Curl" to "leg-curl",
                            "Hip Thrusts" to "hip-thrust",
                            "Calf Raises" to "default",
                            
                            "Smith Machine Squat" to "smith",
                            "Box Jumps" to "jump",
                            "Band Pull Aparts" to "bands",
                            "Cable Crossovers" to "cables",
                            "Ring Dips" to "rings",

                            "Bicep Curls" to "curl",
                            "Hammer Curls" to "curl",
                            "Tricep Pushdowns" to "pushdown",
                            "Skullcrushers" to "barbell",
                            
                            "Sit-ups" to "sit-up",
                            "Leg Raises" to "leg-raise",
                            "Plank" to "plate",
                            
                            "Kettlebell Swing" to "kettlebell",
                            "Medicine Ball Slate" to "medicine-ball",
                            "Stationary Bike" to "stationary-bike"
                        )

                        db.beginTransaction()
                        try {
                            defaults.forEach { (name, iconName) ->
                                val values = android.content.ContentValues().apply {
                                    put("name", name)
                                    put("icon_name", iconName)
                                    put("archived", 0)
                                }
                                db.insert("exercise", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, values)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
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
