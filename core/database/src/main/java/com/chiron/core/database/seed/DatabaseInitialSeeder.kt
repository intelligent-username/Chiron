package com.chiron.core.database.seed

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Handles initial exercise catalog seeding on database creation and startup cleanup.
 */
object DatabaseInitialSeeder {

    private data class DefaultExerciseSeed(
        val name: String,
        val iconName: String,
        val isBodyweight: Boolean = false,
        val percentBodyweight: Double = 100.0
    )

    private val DEFAULT_EXERCISES = listOf(
        DefaultExerciseSeed("Ab Twister", "ab-twister"),
        DefaultExerciseSeed("Australian Pull Ups", "pull-up", isBodyweight = true, percentBodyweight = 60.0),
        DefaultExerciseSeed("Band Pull Aparts", "bands"),
        DefaultExerciseSeed("Barbell Row", "barbell-row"),
        DefaultExerciseSeed("Bench Press", "benchpress"),
        DefaultExerciseSeed("Cable Crossover", "cable-crossover"),
        DefaultExerciseSeed("Cable Row", "cables"),
        DefaultExerciseSeed("Machine Chest Press", "chest-press"),
        DefaultExerciseSeed("Bicep Curl", "curl"),
        DefaultExerciseSeed("Hammer Curl", "hammer-curl"),
        DefaultExerciseSeed("Deadlift", "deadlift"),
        DefaultExerciseSeed("Dips", "dip", isBodyweight = true, percentBodyweight = 100.0),
        DefaultExerciseSeed("Farmer Carry", "farmers-carry"),
        DefaultExerciseSeed("Fly Machine", "fly-machine"),
        DefaultExerciseSeed("Cardio", "heart-rate"),
        DefaultExerciseSeed("Hip Thrust", "hip-thrust"),
        DefaultExerciseSeed("Incline Bench Press", "incline-bench"),
        DefaultExerciseSeed("Incline Machine Press", "incline-press-machine"),
        DefaultExerciseSeed("Box Jumps", "jump"),
        DefaultExerciseSeed("Kettlebell Swing", "kettlebell"),
        DefaultExerciseSeed("Landmine Rotation", "landmine-rotation"),
        DefaultExerciseSeed("Lateral Raises", "lateral-raise"),
        DefaultExerciseSeed("Leg Curl", "leg-curl"),
        DefaultExerciseSeed("Leg Extension", "leg-extension"),
        DefaultExerciseSeed("Leg Press", "leg-press"),
        DefaultExerciseSeed("Leg Raises", "leg-raise", isBodyweight = true, percentBodyweight = 50.0),
        DefaultExerciseSeed("Lunges", "lunge"),
        DefaultExerciseSeed("Machine Row", "machine-row"),
        DefaultExerciseSeed("Pec Deck", "machine"),
        DefaultExerciseSeed("Medicine Ball Slam", "medicine-ball"),
        DefaultExerciseSeed("Overhead Press", "overhead-press"),
        DefaultExerciseSeed("Plank", "45-plate"),
        DefaultExerciseSeed("Preacher Curl", "preacher-curl"),
        DefaultExerciseSeed("Pull Ups", "pull-up", isBodyweight = true, percentBodyweight = 100.0),
        DefaultExerciseSeed("Lat Pulldown", "pulldown"),
        DefaultExerciseSeed("Push ups", "push-up", isBodyweight = true, percentBodyweight = 60.0),
        DefaultExerciseSeed("Tricep Pushdown", "pushdown"),
        DefaultExerciseSeed("Ring Dips", "rings", isBodyweight = true, percentBodyweight = 100.0),
        DefaultExerciseSeed("Sit-ups", "sit-up", isBodyweight = true, percentBodyweight = 50.0),
        DefaultExerciseSeed("Good Morning", "good-morning"),
        DefaultExerciseSeed("Smith Machine Squat", "smith"),
        DefaultExerciseSeed("Squat", "squat"),
        DefaultExerciseSeed("Stationary Bike", "stationary_bike"),
        DefaultExerciseSeed("Treadmill", "treadmill")
    )

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.beginTransaction()
            try {
                DEFAULT_EXERCISES.forEach { seed ->
                    val values = ContentValues().apply {
                        put("name", seed.name)
                        put("icon_name", seed.iconName)
                        put("archived", 0)
                        put("is_bodyweight", if (seed.isBodyweight) 1 else 0)
                        put("percent_bodyweight", seed.percentBodyweight)
                    }
                    db.insert("exercise", SQLiteDatabase.CONFLICT_IGNORE, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
