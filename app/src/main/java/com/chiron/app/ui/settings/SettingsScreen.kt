package com.chiron.app.ui.settings

import android.app.DownloadManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.chiron.app.data.ChironRepository
import com.chiron.app.prefs.UserSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: UserSettingsRepository,
    onExportData: () -> Result<ChironRepository.ExportedData>,
    onImportData: suspend (android.net.Uri) -> Result<String>,
    onBack: () -> Unit
) {
    val displayInKg by repository.displayInKgFlow.collectAsState(initial = false)
    val spotifyEnabled by repository.spotifyEnabledFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                isImporting = true
                val result = onImportData(uri)
                isImporting = false
                
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
                .padding(16.dp)
        ) {
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
                Column {
                    Text(
                        text = "Units",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (displayInKg) "Kilograms (kg)" else "Pounds (lbs)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = displayInKg,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setDisplayInKg(checked) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spotify Mini-Player
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
                Column {
                    Text(
                        text = "Spotify Mini-Player",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (spotifyEnabled) "Showing playback controls\n(Must have Spotify installed)" else "Hidden\n(Must have Spotify installed)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = spotifyEnabled,
                    onCheckedChange = { checked ->
                        scope.launch { repository.setSpotifyEnabled(checked) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isExporting) return@Button
                    scope.launch {
                        isExporting = true
                        val result = onExportData()
                        if (result.isSuccess) {
                            val exported = result.getOrNull()
                            lastExport = exported
                            snackbarHostState.showSnackbar(
                                message = "Data exported"
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Export failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                            )
                        }
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                Text(if (isExporting) "Exporting..." else "Export Data")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isImporting) return@Button
                    filePickerLauncher.launch("application/octet-stream")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting
            ) {
                Text(if (isImporting) "Importing..." else "Import Data")
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
                                Text("Open")
                            }

                            OutlinedButton(onClick = {
                                clipboardManager.setText(AnnotatedString(exported.locationLabel))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Location copied")
                                }
                            }) {
                                Text("Copy Location")
                            }
                        }
                    }
                }
            }
        }
    }
}
