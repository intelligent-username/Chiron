package com.chiron.core.model

/**
 * Contract shape for one weigh-in. Canonical lbs plus UTC millis moment.
 *
 * B1a adaptation point: B1a owns Room annotations (@Entity body_weight_entry,
 * indices on timestamp_utc, DAO, Migration 12->13). This plain type lets the
 * pure resolver compile and test before B1a lands. B1a must keep field names
 * and types, adding annotations only.
 *
 * @param id row id, 0 before insert.
 * @param timestampUtc weigh moment, epoch millis UTC.
 * @param weightLbs canonical weight in lbs. Never null at schema level.
 * @param note optional free text.
 * @param source how the row was created, e.g. manual or import.
 */
data class BodyWeightEntry(
    val id: Long = 0L,
    val timestampUtc: Long,
    val weightLbs: Double,
    val note: String? = null,
    val source: String? = null
)
