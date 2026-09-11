package com.chiron.core.database.bodyweight

import android.content.Context
import android.net.Uri
import com.chiron.core.database.dao.BodyWeightDao
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a committed import, parser errors included. */
data class BodyweightImportSummary(
    val inserted: Int = 0,
    val overwritten: Int = 0,
    val skipped: Int = 0,
    val errorCount: Int = 0
) {
    fun message(): String {
        val base = "Imported ${inserted + overwritten} weights"
        val parts = mutableListOf<String>()
        if (overwritten > 0) parts.add("$overwritten overwritten")
        if (skipped > 0) parts.add("$skipped lines skipped")
        if (errorCount > 0) parts.add("$errorCount lines skipped")
        return if (parts.isEmpty()) base else "$base (${parts.joinToString(", ")})"
    }
}

/** SAF file access plus parser plus transactional upsert. */
class BodyweightImportRepository(
    private val context: Context,
    private val dao: BodyWeightDao
) {
    suspend fun preview(
        uri: Uri,
        config: BodyweightImportConfig
    ): Result<BodyweightParseResult> = withContext(Dispatchers.IO) {
        runCatching { openReader(uri).use { BodyweightFileParser.parseStream(it, config) } }
    }

    suspend fun import(
        uri: Uri,
        config: BodyweightImportConfig
    ): Result<BodyweightImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = openReader(uri).use { BodyweightFileParser.parseStream(it, config) }
            val counts = dao.upsertBodyweights(parsed.rows, config.duplicateRule)
            BodyweightImportSummary(counts.inserted, counts.overwritten, counts.skipped, parsed.errors.size)
        }
    }

    private fun openReader(uri: Uri): BufferedReader {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open import file")
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
    }
}
