package com.chiron.feature.timer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val FUTURE_WEIGHT_DATE_ERROR = "Choose today or an earlier date for weigh-ins"

fun isFutureWeightDate(
    timestampUtc: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Boolean = Instant.ofEpochMilli(timestampUtc).atZone(zone).toLocalDate().isAfter(today)
