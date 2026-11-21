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
import com.example.domain.model.LightLeakMode
import com.example.domain.model.SavedComboPreset
import com.example.domain.model.VideoFilterStyle
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
    val frameBorderEnabled: Boolean = true,
    val instantPreviewEnabled: Boolean = false,
    val animatedEffectsEnabled: Boolean = true,
    val lightLeakMode: LightLeakMode = LightLeakMode.AUTO,
    val exportLosslessPng: Boolean = false,
    val diptychEnabled: Boolean = false,
    val sampleSceneNight: Boolean = false,
    val selfTimerSeconds: Int = 0,
    val favoriteFilmIds: Set<String> = emptySet(),
    val savedCombos: List<SavedComboPreset> = emptyList(),
    val greasePencilFrameIds: Set<Long> = emptySet(),
    val videoFilterStyle: VideoFilterStyle = VideoFilterStyle.SUPER_8_KODACHROME
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
        val FRAME_BORDER = booleanPreferencesKey("frame_border_enabled")
        val INSTANT_PREVIEW = booleanPreferencesKey("instant_preview_enabled")
        val ANIMATED_EFFECTS = booleanPreferencesKey("animated_effects_enabled")
        val LIGHT_LEAK_MODE = stringPreferencesKey("light_leak_mode")
        val LOSSLESS_PNG = booleanPreferencesKey("export_lossless_png")
        val DIPTYCH = booleanPreferencesKey("diptych_enabled")
        val SAMPLE_NIGHT = booleanPreferencesKey("sample_scene_night")
        val SELF_TIMER = intPreferencesKey("self_timer_seconds")
        val FAVORITE_FILMS = stringSetPreferencesKey("favorite_film_ids")
        val SAVED_COMBOS = stringSetPreferencesKey("saved_combo_presets")
        val GREASE_PENCIL_FRAMES = stringSetPreferencesKey("grease_pencil_frame_ids")
        val VIDEO_FILTER_STYLE = stringPreferencesKey("video_filter_style")
    }

    val settingsFlow: Flow<CameraSettings> = context.argentiqueDataStore.data.map { prefs ->
        val legacyEnabled = prefs[Keys.DATE_STAMP] ?: true
        val savedStyle = prefs[Keys.DATE_STAMP_STYLE]?.let { DateStampStyle.fromNameOrDefault(it) }
            ?: if (legacyEnabled) DateStampStyle.QUARTZ_90S else DateStampStyle.OFF
        val parsedCombos = (prefs[Keys.SAVED_COMBOS] ?: emptySet())
            .mapNotNull { SavedComboPreset.deserialize(it) }
            .sortedBy { it.name.lowercase() }
        val parsedMarks = (prefs[Keys.GREASE_PENCIL_FRAMES] ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        val parsedVideoStyle = prefs[Keys.VIDEO_FILTER_STYLE]
            ?.let { raw -> VideoFilterStyle.entries.firstOrNull { it.name == raw } }
            ?: VideoFilterStyle.SUPER_8_KODACHROME
        CameraSettings(
            soundEnabled = prefs[Keys.SOUND] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            gridEnabled = prefs[Keys.GRID] ?: true,
            dateStampEnabled = savedStyle != DateStampStyle.OFF,
            dateStampStyle = savedStyle,
            frameBorderEnabled = prefs[Keys.FRAME_BORDER] ?: true,
            instantPreviewEnabled = prefs[Keys.INSTANT_PREVIEW] ?: false,
            animatedEffectsEnabled = prefs[Keys.ANIMATED_EFFECTS] ?: true,
            lightLeakMode = LightLeakMode.fromNameOrDefault(prefs[Keys.LIGHT_LEAK_MODE]),
            exportLosslessPng = prefs[Keys.LOSSLESS_PNG] ?: false,
            diptychEnabled = prefs[Keys.DIPTYCH] ?: false,
            sampleSceneNight = prefs[Keys.SAMPLE_NIGHT] ?: false,
            selfTimerSeconds = prefs[Keys.SELF_TIMER] ?: 0,
            favoriteFilmIds = prefs[Keys.FAVORITE_FILMS] ?: emptySet(),
            savedCombos = parsedCombos,
            greasePencilFrameIds = parsedMarks,
            videoFilterStyle = parsedVideoStyle
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

    suspend fun setFrameBorderEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.FRAME_BORDER] = value }
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

    suspend fun setInstantPreviewEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.INSTANT_PREVIEW] = value }
    }

    suspend fun setAnimatedEffectsEnabled(value: Boolean) {
        context.argentiqueDataStore.edit { it[Keys.ANIMATED_EFFECTS] = value }
    }

    suspend fun setLightLeakMode(mode: LightLeakMode) {
        context.argentiqueDataStore.edit { it[Keys.LIGHT_LEAK_MODE] = mode.name }
    }

    suspend fun toggleFavoriteFilm(filmId: String) {
        context.argentiqueDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_FILMS] ?: emptySet()
            prefs[Keys.FAVORITE_FILMS] = if (filmId in current) current - filmId else current + filmId
        }
    }

    suspend fun saveComboPreset(preset: SavedComboPreset) {
        context.argentiqueDataStore.edit { prefs ->
            val current = (prefs[Keys.SAVED_COMBOS] ?: emptySet())
                .filterNot { SavedComboPreset.deserialize(it)?.id == preset.id }
                .toSet()
            prefs[Keys.SAVED_COMBOS] = current + preset.serialize()
        }
    }

    suspend fun deleteComboPreset(presetId: String) {
        context.argentiqueDataStore.edit { prefs ->
            val current = (prefs[Keys.SAVED_COMBOS] ?: emptySet())
                .filterNot { SavedComboPreset.deserialize(it)?.id == presetId }
                .toSet()
            prefs[Keys.SAVED_COMBOS] = current
        }
    }

    suspend fun toggleGreasePencilFrame(frameId: Long) {
        val keyStr = frameId.toString()
        context.argentiqueDataStore.edit { prefs ->
            val current = prefs[Keys.GREASE_PENCIL_FRAMES] ?: emptySet()
            prefs[Keys.GREASE_PENCIL_FRAMES] = if (keyStr in current) current - keyStr else current + keyStr
        }
    }

    suspend fun setVideoFilterStyle(style: VideoFilterStyle) {
        context.argentiqueDataStore.edit { prefs ->
            prefs[Keys.VIDEO_FILTER_STYLE] = style.name
        }
    }
}
