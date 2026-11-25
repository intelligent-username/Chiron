package com.chiron.app.ui.settings

import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiron.core.common.UserSettingsRepository
import com.chiron.core.database.ChironRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: UserSettingsRepository,
    onExportData: suspend (android.net.Uri?) -> Result<ChironRepository.ExportedData>,
    onImportData: suspend (android.net.Uri) -> Result<String>,
    onBack: () -> Unit
) {
    val displayInKg by repository.displayInKgFlow.collectAsState(initial = false)
    val showWorkoutVolume by repository.showWorkoutVolumeFlow.collectAsState(initial = false)
    val spotifyEnabled by repository.spotifyEnabledFlow.collectAsState(initial = false)
    val matchThemeWithMedia by repository.matchThemeWithMediaFlow.collectAsState(initial = false)
    val distanceUnit by repository.distanceUnitFlow.collectAsState(initial = com.chiron.core.common.DistanceUnit.METERS)
    val metronomeControlsEnabled by repository.metronomeControlsEnabledFlow.collectAsState(initial = false)
    val customizeSingleTap by repository.metronomeCustomizeSingleTapFlow.collectAsState(initial = false)
    val customizeDoubleTap by repository.metronomeCustomizeDoubleTapFlow.collectAsState(initial = false)
    val customizeTripleTap by repository.metronomeCustomizeTripleTapFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAccessGranted by remember(context) {
        mutableStateOf(com.chiron.feature.timer.metronome.ChironNotificationListenerService.isNotificationAccessGranted(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted =
                    com.chiron.feature.timer.metronome.ChironNotificationListenerService.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var lastExport by remember { mutableStateOf<ChironRepository.ExportedData?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (isImporting || isExporting) return@launch
                isImporting = true
                val result = try {
                    onImportData(uri)
                } finally {
                    isImporting = false
                }
                
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(
                        message = result.getOrNull() ?: "Import successful"
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        message = "Import failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun exportData(destination: android.net.Uri?) {
        if (isExporting || isImporting) return
        scope.launch {
            isExporting = true
            val result = try {
                onExportData(destination)
            } finally {
                isExporting = false
            }
            if (result.isSuccess) {
                lastExport = result.getOrNull()
                snackbarHostState.showSnackbar("Data exported")
            } else {
                snackbarHostState.showSnackbar(
                    "Export failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                )
            }
        }
    }

    // Android 8/9 have no MediaStore.Downloads API. SAF needs no storage permission.
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        if (destination != null) exportData(destination)
    }

    LaunchedEffect(lastExport?.uri) {
        if (lastExport != null) {
            delay(25000)
            lastExport = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SettingsSectionHeading("Measurements")
            // Unit Setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { repository.setDisplayInKg(!displayInKg) }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Weight unit",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (displayInKg) "Kilograms (kg)" else "Pounds (lbs)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    modifier = Modifier.settingsSwitchSize(),
                    checked = displayInKg,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setDisplayInKg(checked) }
                    }
                )
            }

            SettingsDivider()

            // Distance unit setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val next = if (distanceUnit == com.chiron.core.common.DistanceUnit.METERS)
                                com.chiron.core.common.DistanceUnit.FEET
                            else
                                com.chiron.core.common.DistanceUnit.METERS
                            repository.setDistanceUnit(next)
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Distance unit",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = distanceUnit.displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    modifier = Modifier.settingsSwitchSize(),
                    checked = distanceUnit == com.chiron.core.common.DistanceUnit.FEET,
                    onCheckedChange = { checked ->
                        scope.launch {
                            repository.setDistanceUnit(
                                if (checked) com.chiron.core.common.DistanceUnit.FEET
                                else com.chiron.core.common.DistanceUnit.METERS
                            )
                        }
                    }
                )
            }

            SettingsSectionHeading("Playback")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { repository.setSpotifyEnabled(!spotifyEnabled) }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Spotify mini-player",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (spotifyEnabled) "Playback controls shown · Spotify required" else "Playback controls hidden · Spotify required",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    modifier = Modifier.settingsSwitchSize(),
                    checked = spotifyEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setSpotifyEnabled(checked) }
                    }
                )
            }

            if (spotifyEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { repository.setMatchThemeWithMedia(!matchThemeWithMedia) }
                        }
                        .padding(start = 32.dp, top = 4.dp, bottom = 12.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Match app theme with playing media",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Uses colors from album art",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        modifier = Modifier.settingsSwitchSize(),
                        checked = matchThemeWithMedia,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setMatchThemeWithMedia(checked) }
                        }
                    )
                }
            }

            // Metronome Controls Master Setting
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { repository.setMetronomeControlsEnabled(!metronomeControlsEnabled) }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Metronome controls",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Requires notification access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    modifier = Modifier.settingsSwitchSize(),
                    checked = metronomeControlsEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setMetronomeControlsEnabled(checked) }
                    }
                )
            }

            if (metronomeControlsEnabled) {
                if (!notificationAccessGranted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            .padding(start = 32.dp, top = 0.dp, bottom = 8.dp, end = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "*",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Tap to grant notification access in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Customize Single Tap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { repository.setMetronomeCustomizeSingleTap(!customizeSingleTap) }
                        }
                        .padding(start = 32.dp, top = 4.dp, bottom = 8.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Customize single tap",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (customizeSingleTap) "Pauses/plays metronome" else "Pauses/plays song",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        modifier = Modifier.settingsSwitchSize(),
                        checked = customizeSingleTap,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setMetronomeCustomizeSingleTap(checked) }
                        }
                    )
                }

                // Customize Double Tap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { repository.setMetronomeCustomizeDoubleTap(!customizeDoubleTap) }
                        }
                        .padding(start = 32.dp, top = 4.dp, bottom = 8.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Customize double tap",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (customizeDoubleTap) "Skips song" else "Does nothing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        modifier = Modifier.settingsSwitchSize(),
                        checked = customizeDoubleTap,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setMetronomeCustomizeDoubleTap(checked) }
                        }
                    )
                }

                // Customize Triple Tap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { repository.setMetronomeCustomizeTripleTap(!customizeTripleTap) }
                        }
                        .padding(start = 32.dp, top = 4.dp, bottom = 8.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Customize triple tap",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (customizeTripleTap) "Goes to previous song" else "Does nothing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        modifier = Modifier.settingsSwitchSize(),
                        checked = customizeTripleTap,
                        onCheckedChange = { checked ->
                            scope.launch { repository.setMetronomeCustomizeTripleTap(checked) }
                        }
                    )
                }
            }

            SettingsSectionHeading("Workout history")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = showWorkoutVolume,
                        role = Role.Switch,
                        onValueChange = { checked ->
                            scope.launch { repository.setShowWorkoutVolume(checked) }
                        }
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Show workout volume",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(end = 16.dp)
                )
                Switch(checked = showWorkoutVolume, onCheckedChange = null, modifier = Modifier.settingsSwitchSize())
            }

            SettingsSectionHeading("Data")

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportData(null)
                    else exportFileLauncher.launch("Chiron.zip")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && !isImporting
            ) {
                Text(
                    text = if (isExporting) "Exporting…" else "Export data",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = null, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    if (isImporting) return@OutlinedButton
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting && !isExporting
            ) {
                Text(
                    text = if (isImporting) "Importing…" else "Import data",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = null, fontWeight = FontWeight.SemiBold)
                )
            }

            lastExport?.let { exported ->
                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Exported to (tap to open):",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exported.locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(exported.uri, "application/octet-stream")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                                    .onFailure {
                                        clipboardManager.setText(AnnotatedString(exported.locationLabel))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Could not open directly. Location copied.")
                                        }
                                    }
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exported.uri.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Could not open Downloads app")
                                    }
                                }
                            }) {
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelLarge.copy(shadow = null, fontWeight = FontWeight.SemiBold)
                                )
                            }

                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(exported.locationLabel))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Location copied")
                                }
                            }) {
                                Text(
                                    text = "Copy Location",
                                    style = MaterialTheme.typography.labelLarge.copy(shadow = null, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

private fun Modifier.settingsSwitchSize(): Modifier = graphicsLayer {
    scaleX = 0.85f
    scaleY = 0.85f
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}
