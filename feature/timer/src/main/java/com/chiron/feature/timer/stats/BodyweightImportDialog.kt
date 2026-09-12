package com.chiron.feature.timer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiron.core.common.UnitConversion
import com.chiron.core.database.bodyweight.BodyweightFileParser
import com.chiron.core.database.bodyweight.BodyweightImportConfig
import com.chiron.core.database.bodyweight.ImportFileOrder
import com.chiron.core.database.bodyweight.ImportParseError
import com.chiron.core.database.bodyweight.ParsedWeightRow
import com.chiron.core.database.bodyweight.WeightImportUnit
import com.chiron.core.ui.theme.CoolGray
import com.chiron.core.ui.theme.DefaultCoolGray
import com.chiron.core.ui.theme.DefaultDeepCharcoal
import com.chiron.core.ui.theme.ElectricBlue
import com.chiron.core.ui.theme.PrGold
import com.chiron.core.ui.theme.SolidSlate
import com.chiron.core.ui.theme.ThinOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ImportSourceMode { PASTE, FILE }

private val HighlightYellow = Color(0xFFFDE047)
private val HighlightTextDark = Color(0xFF18181B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyweightImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (Sequence<String>, BodyweightImportConfig) -> Unit,
    displayInKg: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    var sourceMode by remember { mutableStateOf(ImportSourceMode.PASTE) }
    var unit by remember { mutableStateOf(if (displayInKg) WeightImportUnit.KG else WeightImportUnit.LBS) }
    var lineStrideText by remember { mutableStateOf("1") }
    var startColText by remember { mutableStateOf("1") }
    var fileOrder by remember { mutableStateOf(ImportFileOrder.OLDEST_FIRST) }
    var stepDaysText by remember { mutableStateOf("1") }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var pastedText by remember { mutableStateOf("") }
    var isEditingRaw by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                var name: String? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
                selectedFileName = name ?: uri.lastPathSegment ?: "weights.txt"
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() } ?: emptyList()
            }.onSuccess { lines ->
                fileLines = lines
            }
        }
    }

    val effectiveLines: List<String> = remember(sourceMode, fileLines, pastedText) {
        if (sourceMode == ImportSourceMode.FILE) {
            fileLines
        } else {
            pastedText.lineSequence().toList()
        }
    }

    val stride = lineStrideText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val col1Based = startColText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val startChar = (col1Based - 1).coerceAtLeast(0)
    val stepDays = stepDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    val config = remember(unit, stride, startChar, fileOrder, stepDays) {
        BodyweightImportConfig(
            unit = unit,
            lineStride = stride,
            startChar = startChar,
            fileOrder = fileOrder,
            stepDays = stepDays
        )
    }

    val previewResult = remember(effectiveLines, config) {
        if (effectiveLines.isNotEmpty()) {
            runCatching {
                BodyweightFileParser.parse(effectiveLines.asSequence(), config)
            }.getOrNull()
        } else null
    }

    val rowsByLineNumber = remember(previewResult) {
        previewResult?.rows?.associateBy { it.sourceLineNumber } ?: emptyMap()
    }
    val errorsByLineNumber = remember(previewResult) {
        previewResult?.errors?.associateBy { it.lineNumber } ?: emptyMap()
    }

    val canConfirm = previewResult != null && previewResult.rows.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Import Bodyweight Logs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source Selector: Instant Segmented Button Row (Zero Lag)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolidSlate)
                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                ) {
                    listOf(
                        ImportSourceMode.PASTE to "Paste Text",
                        ImportSourceMode.FILE to "Pick File"
                    ).forEach { (mode, label) ->
                        val isSelected = sourceMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    focusManager.clearFocus()
                                    sourceMode = mode
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Header Bar above text box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lineCount = effectiveLines.size
                    val validCount = previewResult?.rows?.size ?: 0
                    val headerLabel = if (sourceMode == ImportSourceMode.FILE) {
                        selectedFileName?.let { "$it ($validCount weights)" } ?: "No file selected"
                    } else {
                        if (lineCount == 0) "Input Text" else "$lineCount lines ($validCount weights)"
                    }

                    Text(
                        text = headerLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CoolGray,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sourceMode == ImportSourceMode.PASTE) {
                            if (pastedText.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { isEditingRaw = !isEditingRaw },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditingRaw) Icons.Default.Visibility else Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEditingRaw) "Preview" else "Edit", fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = {
                                        pastedText = ""
                                        isEditingRaw = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        pastedText = clip
                                        isEditingRaw = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", fontSize = 11.sp)
                            }
                        } else {
                            if (fileLines.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        selectedFileName = null
                                        fileLines = emptyList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { launcher.launch("*/*") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(if (selectedFileName != null) "Change File" else "Choose File", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Text Box: Fixed height, non-expanding, scrollable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DefaultDeepCharcoal)
                        .border(1.dp, ThinOutline, RoundedCornerShape(8.dp))
                ) {
                    if (sourceMode == ImportSourceMode.FILE && fileLines.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { launcher.launch("*/*") },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose .txt File")
                            }
                        }
                    } else if (sourceMode == ImportSourceMode.PASTE && (isEditingRaw || pastedText.isEmpty())) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { newText ->
                                val wasEmpty = pastedText.isEmpty()
                                pastedText = newText
                                if (wasEmpty && newText.length > 5 && newText.contains('\n')) {
                                    isEditingRaw = false
                                }
                            },
                            placeholder = { Text("Paste weight lines here...", color = CoolGray.copy(alpha = 0.6f)) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (pastedText.isEmpty()) {
                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        pastedText = clip
                                        isEditingRaw = false
                                    }
                                },
                                modifier = Modifier.align(Alignment.Center),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Paste from Clipboard")
                            }
                        }
                    } else if (effectiveLines.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No lines found to parse",
                                color = CoolGray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        // Scrollable Highlighted Line Preview
                        val dFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
                        val zone = remember { ZoneId.systemDefault() }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(effectiveLines) { idx, line ->
                                val lineNo = idx + 1
                                val row = rowsByLineNumber[lineNo]
                                val error = errorsByLineNumber[lineNo]

                                val dateStr = row?.let {
                                    runCatching {
                                        Instant.ofEpochMilli(it.timestampUtc).atZone(zone).format(dFmt)
                                    }.getOrNull()
                                }

                                val annotatedLine = remember(line, lineNo, config, row, error) {
                                    buildAnnotatedLine(line, config, row, error)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (row != null) SolidSlate.copy(alpha = 0.45f) else Color.Transparent)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Line Number
                                    Text(
                                        text = "$lineNo",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CoolGray.copy(alpha = 0.5f),
                                        modifier = Modifier.width(26.dp)
                                    )

                                    // Assigned Date Badge
                                    if (dateStr != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(ElectricBlue.copy(alpha = 0.2f))
                                                .border(0.5.dp, ElectricBlue.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ElectricBlue
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.04f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (error != null) "Err" else "—",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (error != null) MaterialTheme.colorScheme.error else CoolGray.copy(alpha = 0.35f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Annotated Line Text with Yellow Highlight
                                    Text(
                                        text = annotatedLine,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Parsed Weight Value
                                    if (row != null) {
                                        val displayWeight = if (unit == WeightImportUnit.KG) UnitConversion.lbsToKg(row.weightLbs) else row.weightLbs
                                        val uStr = if (unit == WeightImportUnit.KG) "kg" else "lbs"
                                        Text(
                                            text = "${UnitConversion.formatNumber(displayWeight)} $uStr",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrGold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Options: Stride and Start Column
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = lineStrideText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) lineStrideText = it },
                        label = { Text("Every X lines", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = startColText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) startColText = it },
                        label = { Text("Start column", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Options: Unit and Date Order
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Unit selector
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolidSlate)
                            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(WeightImportUnit.LBS, WeightImportUnit.KG).forEach { u ->
                            val isSelected = unit == u
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { unit = u }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (u == WeightImportUnit.LBS) "in lbs" else "in kg",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Order selector
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolidSlate)
                            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(ImportFileOrder.OLDEST_FIRST to "Oldest ↑", ImportFileOrder.NEWEST_FIRST to "Newest ↑").forEach { (ord, label) ->
                            val isSelected = fileOrder == ord
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { fileOrder = ord }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Summary status
                if (effectiveLines.isNotEmpty() && previewResult != null) {
                    val found = previewResult.rows.size
                    val skipped = previewResult.errors.size + (effectiveLines.size - previewResult.rows.size - previewResult.errors.size).coerceAtLeast(0)
                    Text(
                        text = "Found $found valid weights • $skipped skipped",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (found > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(effectiveLines.asSequence(), config)
                },
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Pre-highlights the extracted weight numeric snippet in bright yellow with dark contrast text.
 */
private fun buildAnnotatedLine(
    line: String,
    config: BodyweightImportConfig,
    row: ParsedWeightRow?,
    error: ImportParseError?
): AnnotatedString {
    if (row == null) {
        val isSkipped = error == null
        val alpha = if (isSkipped) 0.35f else 0.7f
        return buildAnnotatedString {
            withStyle(SpanStyle(color = DefaultCoolGray.copy(alpha = alpha))) {
                append(line)
            }
        }
    }

    val startChar = config.startChar
    val fl = config.fieldLength
    val slice = if (startChar in 0 until line.length) {
        if (fl != null) {
            line.substring(startChar, minOf(startChar + fl, line.length))
        } else {
            line.substring(startChar)
        }
    } else null

    var matchStartInLine: Int? = null
    var matchEndInLine: Int? = null

    if (slice != null) {
        val matches = Regex("""\d+(?:[.,]\d+)?""").findAll(slice).toList()
        val targetMatch = matches.firstOrNull { m ->
            val v = m.value.replace(',', '.').toDoubleOrNull()
            if (v == null) false
            else {
                val lbs = if (config.unit == WeightImportUnit.KG) UnitConversion.kgToLbs(v) else v
                kotlin.math.abs(lbs - row.weightLbs) < 0.01
            }
        } ?: matches.firstOrNull()

        if (targetMatch != null) {
            matchStartInLine = startChar + targetMatch.range.first
            matchEndInLine = startChar + targetMatch.range.last + 1
        }
    }

    return buildAnnotatedString {
        if (matchStartInLine != null && matchEndInLine != null &&
            matchStartInLine >= 0 && matchEndInLine <= line.length && matchStartInLine < matchEndInLine
        ) {
            if (matchStartInLine > 0) {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.9f))) {
                    append(line.substring(0, matchStartInLine))
                }
            }
            withStyle(
                SpanStyle(
                    background = HighlightYellow,
                    color = HighlightTextDark,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(line.substring(matchStartInLine, matchEndInLine))
            }
            if (matchEndInLine < line.length) {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.9f))) {
                    append(line.substring(matchEndInLine))
                }
            }
        } else {
            withStyle(SpanStyle(color = Color.White)) {
                append(line)
            }
        }
    }
}
