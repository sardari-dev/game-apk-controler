package com.gamebridge.km.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamebridge.km.bridge.BridgeService
import com.gamebridge.km.bridge.Diagnostics
import com.gamebridge.km.data.AppSettings
import com.gamebridge.km.data.DefaultProfiles
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.ProfileRepository
import com.gamebridge.km.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameAppInfo(val pkg: String, val label: String, val isKnown: Boolean)

class BridgeViewModel(app: Application) : AndroidViewModel(app) {

    private val profileRepo = ProfileRepository(app)
    private val settingsRepo = SettingsRepository(app)

    val profiles: StateFlow<List<KeymapProfile>> = profileRepo.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val activeMap: StateFlow<Map<String, String>> = profileRepo.activeMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val serviceConnected: StateFlow<Boolean> = flow {
        while (true) {
            emit(BridgeService.instance != null)
            delay(700)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), false)

    val diagnostics: StateFlow<List<String>> = Diagnostics.lines

    private val _games = MutableStateFlow<List<GameAppInfo>>(emptyList())
    val games: StateFlow<List<GameAppInfo>> = _games

    private val _selectedPackage = MutableStateFlow<String?>(null)
    val selectedPackage: StateFlow<String?> = _selectedPackage

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val list = loadGames()
            _games.value = list
            // default to the first known game if installed
            _selectedPackage.value = list.firstOrNull { it.isKnown }?.pkg
                ?: list.firstOrNull()?.pkg
        }
    }

    private fun loadGames(): List<GameAppInfo> {
        val pm = getApplication<Application>().packageManager
        val known = DefaultProfiles.knownGamePackages
        return pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                GameAppInfo(
                    pkg = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    isKnown = it.packageName in known
                )
            }
            .sortedWith(
                compareBy(
                    { if (it.isKnown) known.indexOf(it.pkg) else 1000 },
                    { it.label.lowercase() }
                )
            )
    }

    fun selectPackage(pkg: String) {
        _selectedPackage.value = pkg
    }

    fun activeProfileFor(pkg: String?): KeymapProfile? {
        if (pkg == null) return null
        val profiles = profiles.value
        val activeId = activeMap.value[pkg]
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

    fun launchGame() {
        val pkg = _selectedPackage.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val profile = activeProfileFor(pkg)
            settingsRepo.update {
                it.copy(armedPackage = pkg, armedProfileId = profile?.id)
            }
            val intent = app.packageManager.getLaunchIntentForPackage(pkg) ?: return@launch
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(intent) }
        }
    }

    fun setActiveProfile(pkg: String, profileId: String) {
        viewModelScope.launch { profileRepo.setActiveProfile(pkg, profileId) }
    }

    fun saveProfile(profile: KeymapProfile) {
        viewModelScope.launch {
            profileRepo.upsert(profile)
            // refresh the running service immediately if it uses this profile
            val svc = BridgeService.instance
            if (svc != null && settings.value.armedProfileId == profile.id) {
                svc.router.onProfileChanged(profile)
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    companion object {

        fun isAccessibilityEnabled(context: Context): Boolean {
            return runCatching {
                val expected = ComponentName(context, BridgeService::class.java)
                val enabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0
                ) == 1
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return@runCatching false
                enabled && services.split(':').any { entry ->
                    runCatching {
                        ComponentName.unflattenFromString(entry) == expected
                    }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }

        fun hasMouse(context: Context): Boolean = inputDevices(context) { d ->
            !d.isVirtual && (d.sources and (InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_MOUSE_RELATIVE)) != 0
        }

        fun hasPhysicalKeyboard(context: Context): Boolean = inputDevices(context) { d ->
            !d.isVirtual && d.keyboardType != InputDevice.KEYBOARD_TYPE_NONE &&
                    (d.sources and InputDevice.SOURCE_KEYBOARD) != 0
        }

        private inline fun inputDevices(
            context: Context,
            predicate: (InputDevice) -> Boolean
        ): Boolean {
            return runCatching {
                val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
                    ?: return false
                im.inputDeviceIds.any { id ->
                    InputDevice.getDevice(id)?.let(predicate) == true
                }
            }.getOrDefault(false)
        }

        fun keyLabel(keyCode: Int): String =
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
    }
}
