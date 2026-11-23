package com.chiron.feature.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.model.BodyWeightEntry
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.DefaultDeepCharcoal
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TransferOption(val label: String) {
    IMPORT("Import weights"),
    TEXT("Text export (re-importable)"),
    CSV("CSV export (timestamps)")
}

private val ExportDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** One line per weigh-in: the weight comes before the date so the importer reads it as the value. */
fun weightTextExport(entries: List<BodyWeightEntry>, zone: ZoneId): String =
    entries.sortedBy { it.timestampUtc }.joinToString("\n") { entry ->
        val date = Instant.ofEpochMilli(entry.timestampUtc).atZone(zone).toLocalDate()
        "${trimWeight(entry.weightLbs)} lbs · ${date.format(ExportDate)}"
    }

internal fun weightCsvExport(entries: List<BodyWeightEntry>): String =
    buildString {
        append("timestamp_utc_ms,weight_lbs")
        entries.sortedBy { it.timestampUtc }.forEach {
            append('\n')
            append(it.timestampUtc)
            append(',')
            append(trimWeight(it.weightLbs))
        }
    }

private fun trimWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

private const val IMPORT_EXAMPLE = """180.2 lbs · 2026-01-10
179.6 lbs · 2026-01-17
178.9 · 2026-01-24

Jan 31, 2026: 178.4 lbs
| Jul 4, 2025 | 181.2 |
| Jul 11, 2025 | 180.1 |"""

@Composable
fun BodyweightTransferDialog(
    entries: List<BodyWeightEntry>,
    localInKg: Boolean,
    onDismiss: () -> Unit,
    onOpenImport: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val zone = remember { ZoneId.systemDefault() }
    var option by remember { mutableStateOf(TransferOption.TEXT) }
    var copied by remember { mutableStateOf(false) }

    val text = remember(entries, zone) { weightTextExport(entries, zone) }
    val csv = remember(entries) { weightCsvExport(entries) }

    fun preview(body: String): String {
        if (body.isBlank()) return "Nothing to export yet."
        val lines = body.lineSequence().take(8).toList()
        val extra = body.lineSequence().count() - lines.size
        return buildString {
            append(lines.joinToString("\n"))
            if (extra > 0) append("\n… ${extra} more line${if (extra == 1) "" else "s"}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weight data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TransferOption.entries.forEach { item ->
                    val selected = option == item
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else ThinOutline,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    item == TransferOption.IMPORT -> "In"
                                    else -> "${entries.size} rows"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = CoolGray
                            )
                        }
                    }
                    if (selected) {
                        Text(
                            when (item) {
                                TransferOption.IMPORT -> "Accepts one value per line or item. Dates can be ISO (2026-01-10) or written (Jan 10, 2026); notebook rows with pipes work too. The importer also lets you choose start item, every X items, start character, field length, units, order and date stepping."
                                TransferOption.TEXT -> "Each line is one weigh-in with its date, so this exact text imports back cleanly."
                                TransferOption.CSV -> "Two columns for spreadsheets: timestamp in milliseconds and weight in pounds."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CoolGray
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(DefaultDeepCharcoal, RoundedCornerShape(8.dp))
                                .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                text = when (item) {
                                    TransferOption.IMPORT -> IMPORT_EXAMPLE
                                    TransferOption.TEXT -> preview(text)
                                    TransferOption.CSV -> preview(csv)
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (option) {
                TransferOption.IMPORT -> Button(onClick = { onDismiss(); onOpenImport() }) {
                    Text("Open importer")
                }
                else -> Button(onClick = {
                    clipboard.setText(AnnotatedString(if (option == TransferOption.TEXT) text else csv))
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy export") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
