package com.chiron.app.util

import com.chiron.app.data.entities.WorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateUtils {

    fun formatWorkoutCardDate(workout: WorkoutSession): String {
        val date = Instant.ofEpochMilli(workout.dateUtc).atZone(ZoneId.systemDefault())
        val now = ZonedDateTime.now()
        val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val day = date.dayOfMonth
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return if (date.year < now.year) {
            "$month $day$suffix, ${date.year}"
        } else {
            "$month $day$suffix"
        }
    }

    fun formatWorkoutEditorDate(workout: WorkoutSession): String {
        return formatWorkoutCardDate(workout)
    }

    /**
     * Formats start and end times to: DD/MM/YYYY HH:mm - HH:mm
     */
    fun formatWorkoutStartEndDisplay(workout: WorkoutSession): String {
        val startZdt = Instant.ofEpochMilli(workout.dateUtc).atZone(ZoneId.systemDefault())
        val endEpoch = workout.endTimeUtc ?: workout.dateUtc
        val endZdt = Instant.ofEpochMilli(endEpoch).atZone(ZoneId.systemDefault())

        val dateStr = startZdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val startStr = startZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        val endStr = endZdt.format(DateTimeFormatter.ofPattern("HH:mm"))

        return "$dateStr $startStr - $endStr"
    }

    fun tryParseWorkoutStartEndDisplay(input: String, workout: WorkoutSession): WorkoutSession? {
        // Expected something like: "20/07/2024 14:30 - 15:45"
        val regex = Regex("""(\d{2}/\d{2}/\d{4})\s+(\d{2}[:/]\d{2})\s*(?:-|--|–|—)\s*(\d{2}[:/]\d{2})""")
        val match = regex.find(input.trim()) ?: return null

        val datePart = match.groupValues[1]
        val startTimePart = match.groupValues[2]
        val endTimePart = match.groupValues[3]

        return parseWorkoutTimes(datePart, startTimePart, endTimePart, workout)
    }

    fun parseWorkoutTimes(dateStr: String, startStr: String, endStr: String, workout: WorkoutSession): WorkoutSession? {
        val datePart = dateStr.trim()
        val startTimePart = startStr.trim().replace("/", ":")
        val endTimePart = endStr.trim().replace("/", ":")

        if (datePart.length < 8 || startTimePart.length < 4 || endTimePart.length < 4) return null
        
        return try {
            val startDateTimeStr = "$datePart $startTimePart"
            val endDateTimeStr = "$datePart $endTimePart"
            
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            
            val startZdt = java.time.LocalDateTime.parse(startDateTimeStr, formatter).atZone(ZoneId.systemDefault())
            val endZdt = java.time.LocalDateTime.parse(endDateTimeStr, formatter).atZone(ZoneId.systemDefault())

            var endEpoch = endZdt.toInstant().toEpochMilli()
            val startEpoch = startZdt.toInstant().toEpochMilli()

            // If end is before start, assume it crossed midnight
            if (endEpoch < startEpoch) {
                endEpoch += 86400000L
            }

            workout.copy(
                dateUtc = startEpoch,
                endTimeUtc = endEpoch,
                dateIso = startZdt.toLocalDate().toString()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getDateStr(workout: WorkoutSession): String {
        val startZdt = Instant.ofEpochMilli(workout.dateUtc).atZone(ZoneId.systemDefault())
        return startZdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    fun getEndDateStr(workout: WorkoutSession): String {
        val endEpoch = workout.endTimeUtc ?: workout.dateUtc
        val endZdt = Instant.ofEpochMilli(endEpoch).atZone(ZoneId.systemDefault())
        return endZdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
    
    fun getStartStr(workout: WorkoutSession): String {
        val startZdt = Instant.ofEpochMilli(workout.dateUtc).atZone(ZoneId.systemDefault())
        return startZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    
    fun getEndStr(workout: WorkoutSession): String {
        val endEpoch = workout.endTimeUtc ?: workout.dateUtc
        val endZdt = Instant.ofEpochMilli(endEpoch).atZone(ZoneId.systemDefault())
        return endZdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    fun parseWorkoutDateTimes(
        startDateStr: String,
        startTimeStr: String,
        endDateStr: String,
        endTimeStr: String,
        workout: WorkoutSession
    ): WorkoutSession? {
        val startDatePart = startDateStr.trim()
        val startTimePart = startTimeStr.trim().replace("/", ":")
        val endDatePart = endDateStr.trim()
        val endTimePart = endTimeStr.trim().replace("/", ":")

        if (
            startDatePart.length < 8 ||
            endDatePart.length < 8 ||
            startTimePart.length < 4 ||
            endTimePart.length < 4
        ) return null

        return try {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

            val startZdt = java.time.LocalDateTime
                .parse("$startDatePart $startTimePart", formatter)
                .atZone(ZoneId.systemDefault())

            var endZdt = java.time.LocalDateTime
                .parse("$endDatePart $endTimePart", formatter)
                .atZone(ZoneId.systemDefault())

            // Preserve old behavior when only times are changed and end becomes earlier than start.
            if (endZdt.toInstant().toEpochMilli() < startZdt.toInstant().toEpochMilli() && startDatePart == endDatePart) {
                endZdt = endZdt.plusDays(1)
            }

            workout.copy(
                dateUtc = startZdt.toInstant().toEpochMilli(),
                endTimeUtc = endZdt.toInstant().toEpochMilli(),
                dateIso = startZdt.toLocalDate().toString()
            )
        } catch (e: Exception) {
            null
        }
    }
}
