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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * User preferences stored via DataStore.
 */
class UserSettingsRepository(private val context: Context) {

    companion object {
        private val DISPLAY_IN_KG = booleanPreferencesKey("display_in_kg")
        private val CUSTOM_LOCATIONS = stringSetPreferencesKey("custom_locations")
        private val SPOTIFY_ENABLED = booleanPreferencesKey("spotify_enabled")
        private val MATCH_THEME_WITH_MEDIA = booleanPreferencesKey("match_theme_with_media")
        private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        private val CURRENT_TAB = stringPreferencesKey("current_tab")
        private val EDITING_WORKOUT_ID = longPreferencesKey("editing_workout_id")
        private val HAS_BACKFILLED_1RM = booleanPreferencesKey("has_backfilled_1rm")
    }

    val displayInKgFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_IN_KG] ?: false
    }

    suspend fun setDisplayInKg(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DISPLAY_IN_KG] = value
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
}
