package com.chiron.app.ui.dialogs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chiron.core.common.DistanceUnit
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.model.TimerPreset
import com.chiron.feature.exercises.ExercisesViewModel
import com.chiron.feature.exercises.PrScreen
import com.chiron.feature.timer.AddPresetDialog
import com.chiron.feature.timer.BodyweightImportDialog
import com.chiron.feature.timer.PresetsSheet

/**
 * Hosts root-level modal dialogs and bottom sheets for ChironApp.
 */
@Composable
fun ChironDialogHost(
    isPrScreenOpen: Boolean,
    prTargetExerciseId: Long?,
    exercisesViewModel: ExercisesViewModel,
    displayInKg: Boolean,
    distanceUnit: DistanceUnit,
    onClosePrScreen: () -> Unit,
    onOpenWorkoutFromPr: (Long, Long) -> Unit,
    isPresetsOpen: Boolean,
    presets: List<TimerPreset>,
    countdownSeconds: Int,
    onSelectPreset: (Int) -> Unit,
    onOpenAddPresetDialog: () -> Unit,
    onDeletePreset: (TimerPreset) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onDismissPresets: () -> Unit,
    showAddPresetDialog: Boolean,
    onDismissAddPreset: () -> Unit,
    onSaveAddPreset: (String, Int) -> Unit,
    showBodyweightImportDialog: Boolean,
    onDismissBodyweightImport: () -> Unit,
    onConfirmBodyweightImport: (Sequence<String>, BodyweightImportConfig) -> Unit
) {
    if (isPrScreenOpen) {
        PrScreen(
            viewModel = exercisesViewModel,
            displayInKg = displayInKg,
            distanceUnit = distanceUnit,
            initialExerciseId = prTargetExerciseId,
            onClose = onClosePrScreen,
            onOpenWorkout = onOpenWorkoutFromPr,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (isPresetsOpen) {
        PresetsSheet(
            presets = presets,
            currentDuration = countdownSeconds,
            onSelectPreset = onSelectPreset,
            onAddPreset = onOpenAddPresetDialog,
            onDeletePreset = onDeletePreset,
            onEditPreset = onEditPreset,
            onDismiss = onDismissPresets
        )
    }

    if (showAddPresetDialog) {
        AddPresetDialog(
            onDismiss = onDismissAddPreset,
            onSave = onSaveAddPreset
        )
    }

    if (showBodyweightImportDialog) {
        BodyweightImportDialog(
            onDismiss = onDismissBodyweightImport,
            onConfirm = onConfirmBodyweightImport,
            displayInKg = displayInKg
        )
    }
}
