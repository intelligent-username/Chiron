package com.chiron.core.common

import com.chiron.core.model.WorkoutSession
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

    fun formatWorkoutDateLabel(dateIso: String): String {
        return try {
            val date = java.time.LocalDate.parse(dateIso)
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "$dayOfWeek, $month ${date.dayOfMonth}"
        } catch (e: Exception) {
            dateIso
        }
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
