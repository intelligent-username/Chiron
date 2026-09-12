package com.chiron.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.chiron.core.database.bodyweight.ImportDuplicateRule
import com.chiron.core.database.bodyweight.ParsedWeightRow
import com.chiron.core.model.BodyWeightEntry
import kotlinx.coroutines.flow.Flow

/** Counts for a transactional day-bucket import batch. */
data class BodyweightUpsertCounts(
    val inserted: Int = 0,
    val overwritten: Int = 0,
    val skipped: Int = 0
)

internal const val BodyweightDayMs = 86_400_000L

/** Truncate epoch millis to UTC midnight. */
internal fun bodyweightDayStart(timestampUtc: Long): Long =
    Math.floorDiv(timestampUtc, BodyweightDayMs) * BodyweightDayMs

/** Collapse exact-timestamp duplicates, last in file order wins. */
internal fun collapseExactTimestamps(rows: List<ParsedWeightRow>): List<ParsedWeightRow> {
    val byInstant = LinkedHashMap<Long, ParsedWeightRow>(rows.size)
    for (row in rows) byInstant[row.timestampUtc] = row
    return byInstant.values.toList()
}

/** Group by day bucket, last in file order wins per day. */
internal fun groupImportByDay(rows: List<ParsedWeightRow>): Map<Long, ParsedWeightRow> {
    val byDay = LinkedHashMap<Long, ParsedWeightRow>(rows.size)
    for (row in rows) byDay[bodyweightDayStart(row.timestampUtc)] = row
    return byDay
}

/** Latest entry per day bucket, tie-break by id. */
internal fun latestEntryPerDay(entries: List<BodyWeightEntry>): Map<Long, BodyWeightEntry> {
    return entries.groupBy { bodyweightDayStart(it.timestampUtc) }
        .mapValues { (_, list) ->
            list.maxWith(compareBy<BodyWeightEntry> { it.timestampUtc }.thenBy { it.id })
        }
}

@Dao
interface BodyWeightDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: BodyWeightEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyWeightEntry): Long

    @Update
    suspend fun update(entry: BodyWeightEntry)

    @Query("DELETE FROM body_weight_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_weight_entry WHERE timestamp_utc >= :startUtc AND timestamp_utc <= :endUtc")
    suspend fun deleteBetween(startUtc: Long, endUtc: Long): Int

    @Query("SELECT * FROM body_weight_entry ORDER BY timestamp_utc ASC, id ASC")
    fun getAllFlow(): Flow<List<BodyWeightEntry>>

    @Query("SELECT * FROM body_weight_entry ORDER BY timestamp_utc ASC, id ASC")
    suspend fun getAllSync(): List<BodyWeightEntry>

    @Query("SELECT * FROM body_weight_entry WHERE timestamp_utc <= :timestampUtc ORDER BY timestamp_utc DESC, id DESC LIMIT 1")
    suspend fun getLatestAtOrBefore(timestampUtc: Long): BodyWeightEntry?

    @Transaction
    suspend fun upsertBodyweights(
        rows: List<ParsedWeightRow>,
        duplicateRule: ImportDuplicateRule
    ): BodyweightUpsertCounts {
        if (rows.isEmpty()) return BodyweightUpsertCounts()
        val byDay = groupImportByDay(collapseExactTimestamps(rows))
        val existingByDay = latestEntryPerDay(getAllSync())
        var inserted = 0
        var overwritten = 0
        var skipped = 0
        byDay.entries.chunked(500).forEach { chunk ->
            chunk.forEach { (day, row) ->
                val target = existingByDay[day]
                if (target == null) {
                    insert(dayEntry(row))
                    inserted++
                } else if (duplicateRule == ImportDuplicateRule.SKIP_EXISTING) {
                    skipped++
                } else {
                    update(target.copy(timestampUtc = row.timestampUtc, weightLbs = row.weightLbs, source = "import"))
                    overwritten++
                }
            }
        }
        return BodyweightUpsertCounts(inserted, overwritten, skipped)
    }
}

internal fun dayEntry(row: ParsedWeightRow): BodyWeightEntry =
    BodyWeightEntry(timestampUtc = row.timestampUtc, weightLbs = row.weightLbs, source = "import")
