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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.chiron.core.database.bodyweight.ImportDateStrategy
import com.chiron.core.database.bodyweight.ImportDelimiter
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
    val dialogScrollState = rememberScrollState()

    var sourceMode by remember { mutableStateOf(ImportSourceMode.PASTE) }
    var delimiter by remember { mutableStateOf(ImportDelimiter.AUTO) }
    var unit by remember { mutableStateOf(if (displayInKg) WeightImportUnit.KG else WeightImportUnit.LBS) }
    var startRowText by remember { mutableStateOf("1") }
    var lineStrideText by remember { mutableStateOf("1") }
    var startColText by remember { mutableStateOf("1") }
    var fieldLengthText by remember { mutableStateOf("") }
    var fileOrder by remember { mutableStateOf(ImportFileOrder.OLDEST_FIRST) }
    var stepDaysText by remember { mutableStateOf("7") }
    var autoDetectDates by remember { mutableStateOf(true) }
    var refYearText by remember { mutableStateOf(LocalDate.now().year.toString()) }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var rawFileText by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }
    var isEditingRaw by remember { mutableStateOf(true) }

    fun tryAutoConfigure(text: String) {
        if (text.isNotBlank()) {
            val items = BodyweightFileParser.splitText(text, delimiter, ignoreBlank = true)
            val pattern = BodyweightFileParser.detectPattern(items)
            if (pattern != null) {
                startRowText = pattern.startRow.toString()
                lineStrideText = pattern.stride.toString()
                unit = pattern.unit
                stepDaysText = pattern.stepDays.toString()
            }
        }
    }

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
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.onSuccess { text ->
                rawFileText = text
                tryAutoConfigure(text)
            }
        }
    }

    val rawInput = if (sourceMode == ImportSourceMode.FILE) rawFileText else pastedText

    val effectiveItems: List<String> = remember(rawInput, delimiter) {
        BodyweightFileParser.splitText(rawInput, delimiter, ignoreBlank = true)
    }

    val stride = lineStrideText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val startRow = startRowText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val col1Based = startColText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val startChar = (col1Based - 1).coerceAtLeast(0)
    val fieldLength = fieldLengthText.toIntOrNull()?.takeIf { it > 0 }
    val stepDays = stepDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val refYear = refYearText.toIntOrNull() ?: LocalDate.now().year
    val anchorDateUtc = remember(refYear) {
        LocalDate.of(refYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    val config = remember(
        unit, stride, startChar, fieldLength, startRow, delimiter,
        fileOrder, stepDays, autoDetectDates, anchorDateUtc
    ) {
        BodyweightImportConfig(
            unit = unit,
            lineStride = stride,
            startChar = startChar,
            fieldLength = fieldLength,
            startRow = startRow,
            headerSkipLines = (startRow - 1).coerceAtLeast(0),
            delimiter = delimiter,
            fileOrder = fileOrder,
            dateStrategy = if (autoDetectDates) ImportDateStrategy.AUTO_DETECT else ImportDateStrategy.ONE_PER_DAY_BACKWARDS,
            stepDays = stepDays,
            autoDetectDates = autoDetectDates,
            anchorDateUtc = anchorDateUtc
        )
    }

    val previewResult = remember(effectiveItems, config) {
        if (effectiveItems.isNotEmpty()) {
            runCatching {
                BodyweightFileParser.parse(effectiveItems.asSequence(), config)
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
    val validCount = previewResult?.rows?.size ?: 0

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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source Selector: Instant Segmented Button Row
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
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
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
                    val itemCount = effectiveItems.size
                    val headerLabel = if (sourceMode == ImportSourceMode.FILE) {
                        selectedFileName?.let { "$it • $itemCount items ($validCount weights)" } ?: "No file selected"
                    } else {
                        if (itemCount == 0) "Input Text" else "$itemCount items ($validCount weights)"
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
                                        tryAutoConfigure(clip)
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
                            if (rawFileText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        selectedFileName = null
                                        rawFileText = ""
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
                    if (sourceMode == ImportSourceMode.FILE && rawFileText.isEmpty()) {
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
                                if (wasEmpty && newText.length > 5) {
                                    tryAutoConfigure(newText)
                                    if (newText.contains('\n') || newText.contains('|')) {
                                        isEditingRaw = false
                                    }
                                }
                            },
                            placeholder = { Text("Paste weights or logs here...", color = CoolGray.copy(alpha = 0.6f)) },
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
                                        tryAutoConfigure(clip)
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
                    } else if (effectiveItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No items found to parse",
                                color = CoolGray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        // Scrollable Highlighted Item Preview
                        val dFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
                        val zone = remember { ZoneId.systemDefault() }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(effectiveItems) { idx, itemText ->
                                val itemNo = idx + 1
                                val row = rowsByLineNumber[itemNo]
                                val error = errorsByLineNumber[itemNo]

                                val dateStr = row?.let {
                                    runCatching {
                                        Instant.ofEpochMilli(it.timestampUtc).atZone(zone).format(dFmt)
                                    }.getOrNull()
                                }

                                val annotatedLine = remember(itemText, itemNo, config, row, error) {
                                    buildAnnotatedLine(itemText, config, row, error)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (row != null) SolidSlate.copy(alpha = 0.5f) else Color.Transparent)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item Number
                                    Text(
                                        text = "#$itemNo",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CoolGray.copy(alpha = 0.6f),
                                        modifier = Modifier.width(32.dp)
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

                                    // Annotated Item Text with Yellow Highlight
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

                // Controls Section 1: Item & Column Navigation (Primary User Request)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = startRowText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) startRowText = it },
                        label = { Text("Start item #", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = lineStrideText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) lineStrideText = it },
                        label = { Text("Every X items", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = startColText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) startColText = it },
                        label = { Text("Start char", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Controls Section 2: Delimiter & Unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delimiter Segmented Selector
                    Row(
                        modifier = Modifier
                            .weight(1.4f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolidSlate)
                            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            ImportDelimiter.AUTO to "Auto",
                            ImportDelimiter.PIPES to "| Pipes",
                            ImportDelimiter.LINES to "\\n Lines",
                            ImportDelimiter.COMMAS to ", Commas"
                        ).forEach { (d, label) ->
                            val isSelected = delimiter == d
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { delimiter = d }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Unit Selector
                    Row(
                        modifier = Modifier
                            .weight(0.8f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolidSlate)
                            .border(1.dp, ThinOutline, RoundedCornerShape(8.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(WeightImportUnit.KG to "kg", WeightImportUnit.LBS to "lbs").forEach { (u, label) ->
                            val isSelected = unit == u
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { unit = u }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 8.dp),
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

                // Controls Section 3: Dates & Ordering
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = stepDaysText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) stepDaysText = it },
                        label = { Text("Days step", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = refYearText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) refYearText = it },
                        label = { Text("Year", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Order selector
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
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
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else CoolGray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Controls Section 4: Auto-detect dates toggle & Status Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { autoDetectDates = !autoDetectDates }
                            .background(if (autoDetectDates) ElectricBlue.copy(alpha = 0.2f) else SolidSlate)
                            .border(1.dp, if (autoDetectDates) ElectricBlue else ThinOutline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (autoDetectDates) "✓ Auto-detect dates" else "Manual step dates",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (autoDetectDates) ElectricBlue else CoolGray
                        )
                    }

                    if (effectiveItems.isNotEmpty() && previewResult != null) {
                        val skipped = previewResult.errors.size + (effectiveItems.size - previewResult.rows.size - previewResult.errors.size).coerceAtLeast(0)
                        Text(
                            text = "$validCount valid • $skipped skipped",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (validCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(effectiveItems.asSequence(), config)
                },
                enabled = canConfirm,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (validCount > 0) "Import $validCount Weights" else "Confirm Import")
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
        val matches = BodyweightFileParser.WEIGHT_NUMBER_REGEX.findAll(slice).toList()
        val targetMatch = matches.firstOrNull { m ->
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (v == null) false
            else {
                val lbs = if (config.unit == WeightImportUnit.KG) UnitConversion.kgToLbs(v) else v
                kotlin.math.abs(lbs - row.weightLbs) < 0.05
            }
        } ?: matches.firstOrNull()

        if (targetMatch != null) {
            val grp = targetMatch.groups[1] ?: targetMatch.groups[0]!!
            matchStartInLine = startChar + grp.range.first
            matchEndInLine = startChar + grp.range.last + 1
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
