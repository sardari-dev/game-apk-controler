package com.gamebridge.km.data

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "gamebridge_settings")

enum class MouseMode { AUTO, CAPTURE, HOVER }

@Serializable
data class AppSettings(
    val sensX: Float = 1.0f,
    val sensY: Float = 1.0f,
    val adsScale: Float = 0.65f,
    val invertY: Boolean = false,
    val autoLock: Boolean = true,
    val lockKeyCode: Int = KeyEvent.KEYCODE_F8,
    val menuKeyCode: Int = KeyEvent.KEYCODE_F7,
    val floatieX: Float = 0.04f,
    val floatieY: Float = 0.38f,
    val acceptedDisclaimer: Boolean = false,
    val mouseMode: MouseMode = MouseMode.AUTO,
    /** Session armed from the app: package + profile to use when the game opens. */
    val armedPackage: String? = null,
    val armedProfileId: String? = null
)

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("settings_json")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        runCatching { json.decodeFromString<AppSettings>(prefs[key] ?: "") }
            .getOrElse { AppSettings() }
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val cur = runCatching { json.decodeFromString<AppSettings>(prefs[key] ?: "") }
                .getOrElse { AppSettings() }
            prefs[key] = json.encodeToString(transform(cur))
        }
    }
}
