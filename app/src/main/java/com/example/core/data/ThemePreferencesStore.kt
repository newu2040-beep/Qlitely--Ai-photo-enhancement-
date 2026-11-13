package com.example.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.designsystem.QltyThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.qltyThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "qlitey_appearance_prefs"
)

data class AppearanceSettings(
    val isDarkMode: Boolean = true,
    val themePreset: QltyThemePreset = QltyThemePreset.OBSIDIAN_VIOLET,
    val hasPromptedPermissions: Boolean = false
)

class ThemePreferencesStore(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val HAS_PROMPTED_PERMISSIONS = booleanPreferencesKey("has_prompted_permissions")
    }

    val appearanceFlow: Flow<AppearanceSettings> = context.qltyThemeDataStore.data.map { prefs ->
        AppearanceSettings(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            themePreset = QltyThemePreset.fromId(prefs[Keys.THEME_PRESET]),
            hasPromptedPermissions = prefs[Keys.HAS_PROMPTED_PERMISSIONS] ?: false
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.qltyThemeDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_MODE] = enabled
        }
    }

    suspend fun setThemePreset(preset: QltyThemePreset) {
        context.qltyThemeDataStore.edit { prefs ->
            prefs[Keys.THEME_PRESET] = preset.id
        }
    }

    suspend fun markPermissionsPrompted() {
        context.qltyThemeDataStore.edit { prefs ->
            prefs[Keys.HAS_PROMPTED_PERMISSIONS] = true
        }
    }
}
