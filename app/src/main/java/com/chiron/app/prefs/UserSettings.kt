package com.chiron.app.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        context.dataStore.edit { prefs ->
            val current = prefs[CUSTOM_LOCATIONS] ?: emptySet()
            prefs[CUSTOM_LOCATIONS] = current + location
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
}

/**
 * Data class for settings snapshot.
 */
data class UserSettings(
    val displayInKg: Boolean = false,
    val customLocations: List<String> = emptyList(),
    val spotifyEnabled: Boolean = false,
    val matchThemeWithMedia: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

/** Canonical unit for distance display. DB always stores meters. */
enum class DistanceUnit(val key: String, val displayLabel: String) {
    METERS("meters", "Meters"),
    FEET("feet", "Feet");

    companion object {
        fun fromString(value: String?): DistanceUnit =
            values().firstOrNull { it.key == value } ?: METERS
    }
}
