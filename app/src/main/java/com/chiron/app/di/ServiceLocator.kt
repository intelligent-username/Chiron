package com.chiron.app.di

import android.content.Context
import com.chiron.app.data.ChironDatabase
import com.chiron.app.data.ChironRepository
import com.chiron.app.prefs.UserSettingsRepository
import com.chiron.app.viewmodel.ExercisesViewModel
import com.chiron.app.viewmodel.GoalsViewModel
import com.chiron.app.viewmodel.HistoryViewModel
import com.chiron.app.viewmodel.TimerViewModel
import com.chiron.app.viewmodel.VolumeViewModel

/**
 * Simple service locator for manual dependency injection.
 * No DI framework per spec.
 */
object ServiceLocator {

    private lateinit var applicationContext: Context

    private val database: ChironDatabase by lazy {
        ChironDatabase.getInstance(applicationContext)
    }

    val repository: ChironRepository by lazy {
        ChironRepository(
            context = applicationContext,
            exerciseDao = database.exerciseDao(),
            workoutSessionDao = database.workoutSessionDao(),
            exerciseEntryDao = database.exerciseEntryDao(),
            setEntryDao = database.setEntryDao(),
            timerPresetDao = database.timerPresetDao(),
            exercisePrDao = database.exercisePrDao(),
            exercise1rmEstimateDao = database.exercise1rmEstimateDao(),
            goalDao = database.goalDao()
        )
    }

    val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(applicationContext)
    }

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel Factories
    // ─────────────────────────────────────────────────────────────────────────

    val historyViewModelFactory: HistoryViewModel.Factory by lazy {
        HistoryViewModel.Factory(repository, userSettingsRepository)
    }

    val exercisesViewModelFactory: ExercisesViewModel.Factory by lazy {
        ExercisesViewModel.Factory(repository)
    }

    val timerViewModelFactory: TimerViewModel.Factory by lazy {
        TimerViewModel.Factory(repository)
    }

    val volumeViewModelFactory: VolumeViewModel.Factory by lazy {
        VolumeViewModel.Factory(repository)
    }

    val goalsViewModelFactory: GoalsViewModel.Factory by lazy {
        GoalsViewModel.Factory(repository)
    }
}

