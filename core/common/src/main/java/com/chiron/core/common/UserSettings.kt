package com.chiron.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * User preferences stored via DataStore.
 */
class UserSettingsRepository(private val context: Context) {

    companion object {
        private val DISPLAY_IN_KG = booleanPreferencesKey("display_in_kg")
        private val SHOW_WORKOUT_VOLUME = booleanPreferencesKey("show_workout_volume")
        private val CUSTOM_LOCATIONS = stringSetPreferencesKey("custom_locations")
        private val SPOTIFY_ENABLED = booleanPreferencesKey("spotify_enabled")
        private val MATCH_THEME_WITH_MEDIA = booleanPreferencesKey("match_theme_with_media")
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val CURRENT_TAB = stringPreferencesKey("current_tab")
        private val EDITING_WORKOUT_ID = longPreferencesKey("editing_workout_id")
        private val HAS_BACKFILLED_1RM = booleanPreferencesKey("has_backfilled_1rm")
        private val METRONOME_CONTROLS_ENABLED = booleanPreferencesKey("metronome_controls_enabled")
        private val METRONOME_CUSTOMIZE_SINGLE_TAP = booleanPreferencesKey("metronome_customize_single_tap")
        private val METRONOME_CUSTOMIZE_DOUBLE_TAP = booleanPreferencesKey("metronome_customize_double_tap")
        private val METRONOME_CUSTOMIZE_TRIPLE_TAP = booleanPreferencesKey("metronome_customize_triple_tap")
        private val BACKUP_BOOLEAN_KEYS = listOf(
            DISPLAY_IN_KG, SHOW_WORKOUT_VOLUME, SPOTIFY_ENABLED, MATCH_THEME_WITH_MEDIA,
            METRONOME_CONTROLS_ENABLED, METRONOME_CUSTOMIZE_SINGLE_TAP,
            METRONOME_CUSTOMIZE_DOUBLE_TAP, METRONOME_CUSTOMIZE_TRIPLE_TAP
        )
    }

    /** Portable preferences only: never restore device permissions, navigation IDs or migration flags. */
    suspend fun exportBackup(): String {
        val prefs = context.dataStore.data.first()
        return JSONObject().apply {
            put("version", 1)
            BACKUP_BOOLEAN_KEYS.forEach { put(it.name, prefs[it] ?: false) }
            put(DISTANCE_UNIT.name, DistanceUnit.fromString(prefs[DISTANCE_UNIT]).key)
            put(CUSTOM_LOCATIONS.name, JSONArray((prefs[CUSTOM_LOCATIONS] ?: emptySet()).sorted()))
        }.toString()
    }

    fun validateBackup(serialized: String) {
        parseBackup(serialized)
    }

    suspend fun restoreBackup(
        serialized: String?,
        additionalLocations: List<String> = emptyList(),
        mergeLocations: Boolean = true
    ) {
        val backup = serialized?.let(::parseBackup)
        context.dataStore.edit { prefs ->
            backup?.booleans?.forEach { (key, value) -> prefs[key] = value }
            backup?.distanceUnit?.let { prefs[DISTANCE_UNIT] = it }
            val locations = (backup?.locations.orEmpty() + additionalLocations)
                .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            prefs[CUSTOM_LOCATIONS] =
                (if (mergeLocations) prefs[CUSTOM_LOCATIONS].orEmpty() else emptySet()) + locations
        }
    }

    private data class Backup(
        val booleans: Map<Preferences.Key<Boolean>, Boolean>,
        val distanceUnit: String?,
        val locations: Set<String>
    )

    private fun parseBackup(serialized: String): Backup {
        val json = JSONObject(serialized)
        require(json.getInt("version") == 1) { "Unsupported settings backup version" }
        val booleans = BACKUP_BOOLEAN_KEYS.filter { json.has(it.name) }.associateWith {
            val value = json.get(it.name)
            require(value is Boolean) { "Invalid setting: ${it.name}" }
            value
        }
        val unit = if (json.has(DISTANCE_UNIT.name)) {
            json.getString(DISTANCE_UNIT.name).also { value ->
                require(DistanceUnit.entries.any { it.key == value }) { "Invalid distance unit" }
            }
        } else null
        val locations = (if (json.has(CUSTOM_LOCATIONS.name)) json.getJSONArray(CUSTOM_LOCATIONS.name) else null)?.let { array ->
            (0 until array.length()).map { index ->
                val value = array.get(index)
                require(value is String) { "Invalid custom location" }
                value
            }.toSet()
        }.orEmpty()
        return Backup(booleans, unit, locations)
    }

    val displayInKgFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_IN_KG] ?: false
    }

    suspend fun setDisplayInKg(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DISPLAY_IN_KG] = value
        }
    }

    val showWorkoutVolumeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_WORKOUT_VOLUME] ?: false
    }

    suspend fun setShowWorkoutVolume(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_WORKOUT_VOLUME] = value
        }
    }

    val customLocationsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_LOCATIONS]?.toList()?.sorted() ?: emptyList()
    }

    suspend fun addCustomLocation(location: String) {
        if (location.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LOCATIONS] ?: emptySet()
            prefs[CUSTOM_LOCATIONS] = current + location.trim()
        }
    }

    suspend fun addCustomLocations(locations: List<String>) {
        val valid = locations.map { it.trim() }.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LOCATIONS] ?: emptySet()
            prefs[CUSTOM_LOCATIONS] = current + valid
        }
    }

    val spotifyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SPOTIFY_ENABLED] ?: false
    }

    suspend fun setSpotifyEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SPOTIFY_ENABLED] = value
        }
    }

    val matchThemeWithMediaFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MATCH_THEME_WITH_MEDIA] ?: false
    }

    suspend fun setMatchThemeWithMedia(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[MATCH_THEME_WITH_MEDIA] = value
        }
    }

    val distanceUnitFlow: Flow<DistanceUnit> = context.dataStore.data.map { prefs ->
        DistanceUnit.fromString(prefs[DISTANCE_UNIT])
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { prefs ->
            prefs[DISTANCE_UNIT] = unit.key
        }
    }

    // Persist last selected tab (string to stay flexible)
    val currentTabFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_TAB] ?: "history"
    }

    suspend fun setCurrentTab(tab: String) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_TAB] = tab
        }
    }

    val editingWorkoutIdFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[EDITING_WORKOUT_ID]?.takeIf { it > 0L }
    }

    suspend fun setEditingWorkoutId(value: Long?) {
        context.dataStore.edit { prefs ->
            if (value == null || value <= 0L) {
                prefs.remove(EDITING_WORKOUT_ID)
            } else {
                prefs[EDITING_WORKOUT_ID] = value
            }
        }
    }

    suspend fun hasBackfilled1rm(): Boolean {
        return context.dataStore.data.map { it[HAS_BACKFILLED_1RM] ?: false }.first()
    }

    suspend fun setBackfilled1rm(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HAS_BACKFILLED_1RM] = value
        }
    }

    val metronomeControlsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[METRONOME_CONTROLS_ENABLED] ?: false
    }

    suspend fun setMetronomeControlsEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[METRONOME_CONTROLS_ENABLED] = value
        }
    }

    val metronomeCustomizeSingleTapFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[METRONOME_CUSTOMIZE_SINGLE_TAP] ?: false
    }

    suspend fun setMetronomeCustomizeSingleTap(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[METRONOME_CUSTOMIZE_SINGLE_TAP] = value
        }
    }

    val metronomeCustomizeDoubleTapFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[METRONOME_CUSTOMIZE_DOUBLE_TAP] ?: false
    }

    suspend fun setMetronomeCustomizeDoubleTap(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[METRONOME_CUSTOMIZE_DOUBLE_TAP] = value
        }
    }

    val metronomeCustomizeTripleTapFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[METRONOME_CUSTOMIZE_TRIPLE_TAP] ?: false
    }

    suspend fun setMetronomeCustomizeTripleTap(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[METRONOME_CUSTOMIZE_TRIPLE_TAP] = value
        }
    }
}
