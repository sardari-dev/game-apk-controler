package com.gamebridge.km.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import com.gamebridge.km.R
import com.gamebridge.km.bridge.BridgeService
import com.gamebridge.km.bridge.InputRouter
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.ProfileRepository
import com.gamebridge.km.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns every in-game overlay window (accessibility overlays — no extra
 * permission needed): floating button, quick panel, keymap editor and the
 * hover-capture fallback layer.
 */
class OverlayManager(
    private val service: BridgeService,
    private val router: InputRouter,
    private val settingsRepo: SettingsRepository,
    private val profileRepo: ProfileRepository
) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themed: Context = android.view.ContextThemeWrapper(service, R.style.Theme_GameBridge)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var floatie: FloatingButtonView? = null
    private var panel: QuickPanelView? = null
    private var editor: KeymapEditorOverlay? = null
    private var hoverLayer: HoverCaptureView? = null

    private val flagsNoFocus = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    // ------------------------------------------------------------- floatie

    fun showFloatie() = mainHandler.post {
        if (floatie?.isAttachedToWindow == true) {
            floatie?.setLocked(router.locked)
            return@post
        }
        val view = FloatingButtonView(
            themed,
            onToggle = { togglePanel() },
            onPositionChanged = { fx, fy ->
                scope.launch { settingsRepo.update { it.copy(floatieX = fx, floatieY = fy) } }
            }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flagsNoFocus,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val geom = router.geom
            x = (router.settings.floatieX * geom.w).toInt()
            y = (router.settings.floatieY * geom.h).toInt()
        }
        view.setLocked(router.locked)
        if (attach(view, params)) floatie = view
    }

    fun hideFloatie() = mainHandler.post {
        detach(floatie); floatie = null
    }

    // --------------------------------------------------------------- panel

    fun togglePanel() = mainHandler.post {
        if (panel?.isAttachedToWindow == true) hidePanel() else showPanel()
    }

    private fun showPanel() {
        val profile = router.profile
        val view = QuickPanelView(
            themed,
            profileName = profile?.name ?: "—",
            locked = router.locked,
            sensitivity = router.settings.sensX,
            onToggleLock = { router.toggleLock() },
            onSensitivityChanged = { v ->
                scope.launch { settingsRepo.update { it.copy(sensX = v, sensY = v) } }
            },
            onEditKeymap = { showEditor() },
            onHide = {
                hidePanel()
                hideFloatie()
                scope.launch {
                    delay(10_000)
                    if (router.sessionActive) showFloatie()
                }
            },
            onExit = { router.endSession() }
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flagsNoFocus,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (themed.resources.displayMetrics.density * 300).toInt()
        }
        if (attach(view, params)) panel = view
    }

    fun hidePanel() = mainHandler.post {
        detach(panel); panel = null
    }

    // -------------------------------------------------------------- editor

    fun showEditor() = mainHandler.post {
        if (editor?.isAttachedToWindow == true) return@post
        hidePanel()
        val profile = router.profile ?: return@post
        router.setLocked(false)
        val view = KeymapEditorOverlay(
            themed,
            router,
            profile
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (attach(view, params)) editor = view
    }

    fun saveAndCloseEditor() = mainHandler.post {
        val e = editor ?: return@post
        val saved = e.buildProfile()
        detach(e); editor = null
        scope.launch {
            profileRepo.upsert(saved)
            router.onProfileChanged(saved)
        }
    }

    fun closeEditorWithoutSaving() = mainHandler.post {
        detach(editor); editor = null
    }

    fun isEditorOpen(): Boolean = editor?.isAttachedToWindow == true

    // -------------------------------------------------------- hover fallback

    /** Android < 14 fallback: full-screen layer that tracks the mouse pointer. */
    fun setHoverCapture(enabled: Boolean) = mainHandler.post {
        if (enabled) {
            if (hoverLayer?.isAttachedToWindow == true) return@post
            val view = HoverCaptureView(themed, router)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            if (attach(view, params)) hoverLayer = view
        } else {
            detach(hoverLayer); hoverLayer = null
        }
    }

    // ------------------------------------------------------------- general

    fun onLockChanged(locked: Boolean) {
        floatie?.setLocked(locked)
        if (locked) {
            hidePanel()
        } else {
            panel?.updateLockState(false)
        }
    }

    fun hideAll() = mainHandler.post {
        hidePanel()
        closeEditorWithoutSaving()
        hideFloatie()
        setHoverCapture(false)
    }

    fun destroy() {
        scope.cancel()
        hideAll()
    }

    // -------------------------------------------------------------- helpers

    private fun attach(view: android.view.View, params: WindowManager.LayoutParams): Boolean {
        return try {
            wm.addView(view, params)
            true
        } catch (t: Throwable) {
            com.gamebridge.km.bridge.Diagnostics.log("overlay", "addView failed: ${t.message}")
            false
        }
    }

    private fun detach(view: android.view.View?) {
        if (view == null || !view.isAttachedToWindow) return
        try {
            wm.removeView(view)
        } catch (t: Throwable) {
            com.gamebridge.km.bridge.Diagnostics.log("overlay", "removeView failed: ${t.message}")
        }
    }
}
