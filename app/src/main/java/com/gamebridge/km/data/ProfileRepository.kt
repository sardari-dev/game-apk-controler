package com.gamebridge.km.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "gamebridge")

/**
 * Stores keymap profiles as JSON in DataStore. Shared by the app UI and the
 * accessibility service (same process).
 */
class ProfileRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val profilesKey = stringPreferencesKey("profiles_json")
    private val activeMapKey = stringPreferencesKey("active_profile_by_package_json")

    private fun decodeList(raw: String?): List<KeymapProfile> =
        runCatching { json.decodeFromString<List<KeymapProfile>>(raw!!) }.getOrDefault(emptyList())

    val profiles: Flow<List<KeymapProfile>> = context.dataStore.data.map { prefs ->
        val stored = decodeList(prefs[profilesKey])
        if (stored.isEmpty()) DefaultProfiles.defaults() else stored
    }

    val activeMap: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<Map<String, String>>(prefs[activeMapKey] ?: "{}") }
            .getOrDefault(emptyMap())
    }

    suspend fun upsert(profile: KeymapProfile) {
        context.dataStore.edit { prefs ->
            val current = decodeList(prefs[profilesKey]).ifEmpty { DefaultProfiles.defaults() }
            val merged = current.filter { it.id != profile.id } + profile
            prefs[profilesKey] = json.encodeToString(merged)
        }
    }

    suspend fun delete(profileId: String) {
        context.dataStore.edit { prefs ->
            val current = decodeList(prefs[profilesKey])
            prefs[profilesKey] = json.encodeToString(current.filter { it.id != profileId })
        }
    }

    suspend fun byId(profileId: String): KeymapProfile? =
        profiles.first().firstOrNull { it.id == profileId }

    suspend fun activeProfileFor(packageName: String): KeymapProfile? {
        val active = activeMap.first()[packageName] ?: return profiles.first().firstOrNull()
        return byId(active)
    }

    suspend fun setActiveProfile(packageName: String, profileId: String) {
        context.dataStore.edit { prefs ->
            val map = runCatching {
                json.decodeFromString<Map<String, String>>(prefs[activeMapKey] ?: "{}")
            }.getOrDefault(emptyMap()).toMutableMap()
            map[packageName] = profileId
            prefs[activeMapKey] = json.encodeToString(map)
        }
    }

    /** Reset all profiles to factory defaults. */
    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(DefaultProfiles.defaults())
        }
    }
}
