package com.gamebridge.km.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.gamebridge.km.data.AppSettings
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.MouseMode
import com.gamebridge.km.data.ProfileRepository
import com.gamebridge.km.data.SettingsRepository
import com.gamebridge.km.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The accessibility service: intercepts hardware keys (FLAG_REQUEST_FILTER_KEY_EVENTS),
 * captures mouse motion events on Android 14+ (setMotionEventSources) and injects
 * touch gestures (dispatchGesture).
 */
class BridgeService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BridgeService? = null
            private set
    }

    lateinit var engine: GestureEngine
        private set
    lateinit var router: InputRouter
        private set
    lateinit var overlayManager: OverlayManager
        private set

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var profileRepo: ProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var currentSettings = AppSettings()
    @Volatile private var currentProfile: KeymapProfile? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        settingsRepo = SettingsRepository(applicationContext)
        profileRepo = ProfileRepository(applicationContext)

        engine = GestureEngine(this)
        router = InputRouter(this, engine)
        overlayManager = OverlayManager(this, router, settingsRepo, profileRepo)
        router.overlay = overlayManager

        // Make sure key filtering is requested even if XML flags were stripped.
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }

        scope.launch {
            settingsRepo.settings.collect { s ->
                currentSettings = s
                router.onSettingsChanged(s)
            }
        }

        scope.launch {
            settingsRepo.settings
                .map { it.armedPackage }
                .distinctUntilChanged()
                .collect { pkg ->
                    if (pkg != null) {
                        currentProfile = profileRepo.activeProfileFor(pkg)
                        router.onProfileChanged(currentProfile)
                        // game may already be in the foreground
                        delay(500)
                        checkForegroundNow()
                    } else {
                        currentProfile = null
                        router.onProfileChanged(null)
                        router.endSession()
                    }
                }
        }

        Diagnostics.log("service", "connected (sdk=${Build.VERSION.SDK_INT}, mode=${currentSettings.mouseMode})")
    }

    private fun checkForegroundNow() {
        runCatching {
            val pkg = rootInActiveWindow?.packageName?.toString()
            if (pkg != null) onForegroundCandidate(pkg)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onForegroundCandidate(pkg)
        }
    }

    private fun onForegroundCandidate(pkg: String) {
        val armed = currentSettings.armedPackage ?: return

        if (pkg == armed) {
            if (!router.sessionActive) router.startSession(armed)
            return
        }

        if (router.sessionActive && pkg != packageName) {
            // Only end the session if the new foreground is a real launchable app
            // (system dialogs, IMEs and overlays must not kill a running match).
            val launchable = runCatching {
                packageManager.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(false)
            if (launchable) router.endSession()
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean =
        if (event != null && ::router.isInitialized) router.onKeyEvent(event) else false

    override fun onMotionEvent(event: MotionEvent) {
        if (::router.isInitialized) router.onMotionEvent(event)
    }

    override fun onInterrupt() {
        if (::router.isInitialized) router.endSession()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        if (::router.isInitialized) router.endSession()
        Diagnostics.log("service", "destroyed")
        super.onDestroy()
    }

    /** Enable/disable system-wide mouse capture (Android 14+) or hover fallback. */
    fun setMouseCapture(enabled: Boolean) {
        val useSystemCapture = when (currentSettings.mouseMode) {
            MouseMode.AUTO, MouseMode.CAPTURE -> Build.VERSION.SDK_INT >= 34
            else -> false
        }
        if (useSystemCapture) {
            runCatching {
                val info = serviceInfo
                info.motionEventSources =
                    if (enabled) InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_MOUSE_RELATIVE else 0
                serviceInfo = info
            }.onSuccess {
                Diagnostics.log("capture", "system mouse capture ${if (enabled) "ON" else "OFF"}")
            }.onFailure {
                Diagnostics.log("capture", "failed: ${it.message}")
            }
        } else {
            overlayManager.setHoverCapture(enabled)
        }
    }

    /** Launch the armed game from the service context. */
    fun launchArmedGame() {
        val pkg = currentSettings.armedPackage ?: return
        val intent: Intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}
