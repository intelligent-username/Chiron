package com.chiron.core.database.migrations

import androidx.room.migration.Migration

/**
 * Aggregator array of all Room database migrations.
 */
object ChironMigrations {

    val MIGRATION_1_2 = Migrations1To11.MIGRATION_1_2
    val MIGRATION_2_3 = Migrations1To11.MIGRATION_2_3
    val MIGRATION_3_4 = Migrations1To11.MIGRATION_3_4
    val MIGRATION_4_5 = Migrations1To11.MIGRATION_4_5
    val MIGRATION_5_6 = Migrations1To11.MIGRATION_5_6
    val MIGRATION_6_7 = Migrations1To11.MIGRATION_6_7
    val MIGRATION_7_8 = Migrations1To11.MIGRATION_7_8
    val MIGRATION_8_9 = Migrations1To11.MIGRATION_8_9
    val MIGRATION_9_10 = Migrations1To11.MIGRATION_9_10
    val MIGRATION_10_11 = Migrations1To11.MIGRATION_10_11
    val MIGRATION_11_12 = Migrations1To11.MIGRATION_11_12

    val MIGRATION_12_13 = Migrations12To15.MIGRATION_12_13
    val MIGRATION_13_14 = Migrations12To15.MIGRATION_13_14
    val MIGRATION_14_15 = Migrations12To15.MIGRATION_14_15

    /** Array of all migrations to pass to Room database builder. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15
    )
}
