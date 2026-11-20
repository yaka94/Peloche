package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DateStampStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.argentiqueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "argentique_settings"
)

data class CameraSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val gridEnabled: Boolean = true,
    val dateStampEnabled: Boolean = true,
    val dateStampStyle: DateStampStyle = DateStampStyle.QUARTZ_90S,
    val exportLosslessPng: Boolean = false,
    val diptychEnabled: Boolean = false,
    val sampleSceneNight: Boolean = false,
    val selfTimerSeconds: Int = 0,
    val favoriteFilmIds: Set<String> = emptySet()
) {
    val effectiveDateStampStyle: DateStampStyle
        get() = if (dateStampEnabled) dateStampStyle else DateStampStyle.OFF
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val GRID = booleanPreferencesKey("grid_enabled")
        val DATE_STAMP = booleanPreferencesKey("date_stamp_enabled")
        val DATE_STAMP_STYLE = stringPreferencesKey("date_stamp_style")
        val LOSSLESS_PNG = booleanPreferencesKey("export_lossless_png")
        val DIPTYCH = booleanPreferencesKey("diptych_enabled")
        val SAMPLE_NIGHT = booleanPreferencesKey("sample_scene_night")
        val SELF_TIMER = intPreferencesKey("self_timer_seconds")
        val FAVORITE_FILMS = stringSetPreferencesKey("favorite_film_ids")
    }

    val settingsFlow: Flow<CameraSettings> = context.argentiqueDataStore.data.map { prefs ->
        val legacyEnabled = prefs[Keys.DATE_STAMP] ?: true
        val savedStyle = prefs[Keys.DATE_STAMP_STYLE]?.let { DateStampStyle.fromNameOrDefault(it) }
            ?: if (legacyEnabled) DateStampStyle.QUARTZ_90S else DateStampStyle.OFF
        CameraSettings(
            soundEnabled = prefs[Keys.SOUND] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            gridEnabled = prefs[Keys.GRID] ?: true,
            dateStampEnabled = savedStyle != DateStampStyle.OFF,
            dateStampStyle = savedStyle,
            exportLosslessPng = prefs[Keys.LOSSLESS_PNG] ?: false,
            diptychEnabled = prefs[Keys.DIPTYCH] ?: false,
            sampleSceneNight = prefs[Keys.SAMPLE_NIGHT] ?: false,
            selfTimerSeconds = prefs[Keys.SELF_TIMER] ?: 0,
            favoriteFilmIds = prefs[Keys.FAVORITE_FILMS] ?: emptySet()
        )
    }

    suspend fun setSoundEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.SOUND] = value }
    }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.HAPTICS] = value }
    }

    suspend fun setGridEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.GRID] = value }
    }

    suspend fun setDateStampEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { prefs ->
            prefs[Keys.DATE_STAMP] = value
            if (!value) {
                prefs[Keys.DATE_STAMP_STYLE] = DateStampStyle.OFF.name
            } else {
                val cur = DateStampStyle.fromNameOrDefault(prefs[Keys.DATE_STAMP_STYLE])
                if (cur == DateStampStyle.OFF) {
                    prefs[Keys.DATE_STAMP_STYLE] = DateStampStyle.QUARTZ_90S.name
                }
            }
        }
    }

    suspend fun setDateStampStyle(style: DateStampStyle) {
        context.argentiqueDataStore.edit { prefs ->
            prefs[Keys.DATE_STAMP_STYLE] = style.name
            prefs[Keys.DATE_STAMP] = style != DateStampStyle.OFF
        }
    }

    suspend fun setExportLosslessPng(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.LOSSLESS_PNG] = value }
    }

    suspend fun setDiptychEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.DIPTYCH] = value }
    }

    suspend fun setSampleSceneNight(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.SAMPLE_NIGHT] = value }
    }

    suspend fun setSelfTimerSeconds(seconds: Int) {
        context.argentiqueDataStore.edit { it[Keys.SELF_TIMER] = seconds }
    }

    suspend fun toggleFavoriteFilm(filmId: String) {
        context.argentiqueDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_FILMS] ?: emptySet()
            prefs[Keys.FAVORITE_FILMS] = if (filmId in current) current - filmId else current + filmId
        }
    }
}
