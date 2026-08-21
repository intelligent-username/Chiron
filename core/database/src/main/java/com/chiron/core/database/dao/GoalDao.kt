package com.chiron.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chiron.core.model.Goal
import com.chiron.core.model.GoalExercise
import kotlinx.coroutines.flow.Flow

/** A single logged set timestamp for a goal exercise, used for per-day progress bucketing. */
data class SetTimestampRow(val timestampUtc: Long, val exerciseId: Long)

@Dao
abstract class GoalDao {

    @Query("SELECT * FROM goal WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    abstract fun getActiveGoalsFlow(): Flow<List<Goal>>

    @Query("SELECT * FROM goal ORDER BY name COLLATE NOCASE ASC")
    abstract fun getAllGoalsFlow(): Flow<List<Goal>>

    @Query("SELECT * FROM goal_exercise")
    abstract fun getJunctionsFlow(): Flow<List<GoalExercise>>

    @Query("SELECT * FROM goal_exercise WHERE goal_id = :goalId")
    abstract suspend fun getJunctionsForGoal(goalId: Long): List<GoalExercise>

    /**
     * Emits whenever the set_entry table changes (insert/update/delete), so goal
     * progress can refresh immediately when exercises are added to or removed
     * from workouts — not just when goals/junctions/exercises themselves change.
     */
    @Query("SELECT COUNT(*) FROM set_entry")
    abstract fun getSetEntryCountFlow(): Flow<Int>

    @Query("SELECT * FROM goal WHERE id = :id")
    abstract suspend fun getGoalById(id: Long): Goal?

    @Query("SELECT * FROM goal WHERE name = :name AND archived = 0 LIMIT 1")
    abstract suspend fun getGoalByName(name: String): Goal?

    @Query("SELECT * FROM goal WHERE TRIM(name) = TRIM(:name) COLLATE NOCASE LIMIT 1")
    abstract suspend fun getGoalByNameAnyStatus(name: String): Goal?

    @Query(
        "SELECT s.timestamp_utc AS timestampUtc, e.exercise_id AS exerciseId " +
            "FROM set_entry s INNER JOIN exercise_entry e ON s.exercise_entry_id = e.id " +
            "WHERE e.exercise_id IN (:exerciseIds)"
    )
    abstract suspend fun getSetTimestampsForExercises(exerciseIds: List<Long>): List<SetTimestampRow>

    @Insert
    abstract suspend fun insertGoal(goal: Goal): Long

    @Update
    abstract suspend fun updateGoal(goal: Goal)

    @Query("UPDATE goal SET archived = 1 WHERE id = :id")
    abstract suspend fun archiveGoal(id: Long)

    @Query("DELETE FROM goal WHERE id = :id")
    abstract suspend fun deleteGoal(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertJunction(junction: GoalExercise)

    @Query("DELETE FROM goal_exercise WHERE goal_id = :goalId")
    abstract suspend fun deleteJunctionsForGoal(goalId: Long)

    @Transaction
    open suspend fun saveGoalWithExercises(goal: Goal, exerciseIds: List<Long>) {
        val id = if (goal.id == 0L) {
            insertGoal(goal)
        } else {
            updateGoal(goal)
            goal.id
        }
        deleteJunctionsForGoal(id)
        exerciseIds.forEach { exerciseId ->
            insertJunction(GoalExercise(goalId = id, exerciseId = exerciseId))
        }
    }
}
