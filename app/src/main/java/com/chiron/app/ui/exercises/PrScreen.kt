package com.chiron.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.app.data.entities.Exercise
import com.chiron.app.ui.components.ExerciseAsyncIcon
import com.chiron.app.ui.theme.CoolGray
import com.chiron.app.ui.theme.ElectricBlue
import com.chiron.app.ui.theme.ThinOutline
import com.chiron.app.ui.theme.SolidSlate
import com.chiron.app.ui.theme.PrGold
import com.chiron.app.viewmodel.ExercisesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrScreen(
    viewModel: ExercisesViewModel,
    displayInKg: Boolean,
    distanceUnit: com.chiron.app.prefs.DistanceUnit,
    initialExerciseId: Long? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var exercisesWithPrs by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    LaunchedEffect(uiState.showArchived) {
        val ids = viewModel.getExerciseIdsWithPrs().toSet()
        val source = if (uiState.showArchived) uiState.archivedExercises else uiState.exercises
        exercisesWithPrs = source.filter { it.id in ids }
    }

    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    LaunchedEffect(initialExerciseId, exercisesWithPrs) {
        val targetId = initialExerciseId ?: return@LaunchedEffect
        val target = exercisesWithPrs.firstOrNull { it.id == targetId }
        if (target != null) {
            selectedExercise = target
        }
    }
    androidx.activity.compose.BackHandler(enabled = uiState.prSearchQuery.isNotBlank()) {
        viewModel.clearPrSearch()
    }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = PrGold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Personal Records", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            PrExerciseListPanel(
                uiState = uiState,
                exercisesWithPrs = exercisesWithPrs,
                selectedExercise = selectedExercise,
                focusManager = focusManager,
                viewModel = viewModel,
                onSelectExercise = { selectedExercise = it }
            )

            VerticalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val exercise = selectedExercise
                if (exercise == null) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = PrGold.copy(alpha = 0.25f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Select an exercise to see\nyour best performances",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    PrDetailPanel(
                        exercise = exercise,
                        viewModel = viewModel,
                        displayInKg = displayInKg,
                        distanceUnit = distanceUnit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrExerciseListPanel(
    uiState: com.chiron.app.viewmodel.ExercisesUiState,
    exercisesWithPrs: List<Exercise>,
    selectedExercise: Exercise?,
    focusManager: androidx.compose.ui.focus.FocusManager,
    viewModel: ExercisesViewModel,
    onSelectExercise: (Exercise) -> Unit
) {
    val displayedList = if (uiState.prSearchQuery.isNotBlank()) {
        val ids = exercisesWithPrs.map { it.id }.toSet()
        uiState.prSearchResults.filter { it.id in ids }
    } else {
        exercisesWithPrs
    }

    Column(modifier = Modifier.width(180.dp).fillMaxHeight()) {
        // Flat borderless search input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SolidSlate)
                .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = CoolGray
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = uiState.prSearchQuery,
                onValueChange = viewModel::updatePrSearchQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(ElectricBlue),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (uiState.prSearchQuery.isEmpty()) {
                            Text(
                                "Search",
                                style = MaterialTheme.typography.bodySmall,
                                color = CoolGray
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (uiState.prSearchQuery.isNotBlank()) {
                IconButton(onClick = { viewModel.clearPrSearch() }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp), tint = CoolGray)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
            if (displayedList.isEmpty()) {
                item {
                    Text(
                        text = if (uiState.prSearchQuery.isNotBlank()) "No matching exercises" else "No PRs yet.\nFinish some sets!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
            items(displayedList) { exercise ->
                val isSelected = selectedExercise?.id == exercise.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) SolidSlate else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = ThinOutline,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectExercise(exercise) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExerciseAsyncIcon(
                            iconName = exercise.iconName,
                            contentDescription = exercise.name,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
