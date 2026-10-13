package com.gamebridge.km.bridge

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.gamebridge.km.data.AppSettings
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.TriggerKind

/**
 * Central brain: routes hardware key/mouse events to the right controller
 * depending on session & lock state.
 */
class InputRouter(
    private val service: BridgeService,
    private val engine: GestureEngine
) {

    val joystick = JoystickController(engine)
    val camera = CameraController(engine)
    val actions = ActionDispatcher(engine) { geom }

    /** Set post-construction to break the dependency cycle. */
    lateinit var overlay: com.gamebridge.km.overlay.OverlayManager

    var settings: AppSettings = AppSettings()
        private set
    var profile: KeymapProfile? = null
        private set
    var geom: ScreenGeom = ScreenGeom(1080f, 2400f)

    var sessionActive = false
        private set
    var locked = false
        private set

    /** In-game editor grabs the next key/mouse-button press for assignment. */
    var keyCaptureCallback: ((Int) -> Unit)? = null
    var mouseCaptureCallback: ((TriggerKind) -> Unit)? = null

    // hover fallback position tracking
    private var haveLastPos = false
    private var lastX = 0f
    private var lastY = 0f
    private var moveLogCounter = 0

    init {
        actions.onAimStateChanged = { camera.setAimHeld(it) }
    }

    fun onSettingsChanged(s: AppSettings) {
        settings = s
        reconfigure()
    }

    fun onProfileChanged(p: KeymapProfile?) {
        profile = p
        reconfigure()
    }

    private fun reconfigure() {
        val p = profile
        joystick.updateConfig(p?.joystick)
        actions.updateBindings(p?.bindings ?: emptyList())
        camera.configure(
            p?.camera,
            settings.sensX, settings.sensY, settings.adsScale, settings.invertY,
            p?.sensitivity ?: 1f, p?.adsSensitivityScale ?: 0.65f
        )
    }

    fun updateGeometry() {
        geom = ScreenGeom.from(service)
        joystick.updateGeometry(geom)
        camera.updateGeometry(geom)
    }

    // ------------------------------------------------------------- session

    fun startSession(packageName: String) {
        if (sessionActive) return
        sessionActive = true
        updateGeometry()
        haveLastPos = false
        overlay.showFloatie()
        if (settings.autoLock) setLocked(true)
        Diagnostics.log("session", "started for $packageName (profile=${profile?.name})")
    }

    fun endSession() {
        if (!sessionActive) return
        sessionActive = false
        setLockedInternal(false)
        joystick.releaseAll()
        camera.stop()
        actions.releaseAll()
        engine.releaseAll()
        overlay.hideAll()
        Diagnostics.log("session", "ended")
    }

    // ---------------------------------------------------------------- lock

    fun setLocked(value: Boolean) {
        if (locked == value) return
        setLockedInternal(value)
    }

    fun toggleLock() = setLocked(!locked)

    private fun setLockedInternal(value: Boolean) {
        locked = value
        if (value) {
            haveLastPos = false
            service.setMouseCapture(true)
        } else {
            service.setMouseCapture(false)
            camera.stop()
            actions.releaseAll()
        }
        overlay.onLockChanged(locked)
        Diagnostics.log("lock", if (value) "LOCKED" else "FREE")
    }

    // ---------------------------------------------------------------- keys

    fun onKeyEvent(event: KeyEvent): Boolean {
        keyCaptureCallback?.let { cb ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                cb(event.keyCode)
            }
            return true // swallow everything while listening for an assignment
        }

        if (!sessionActive) return false

        if (event.repeatCount > 0) return isMappedKey(event.keyCode)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKey(event.keyCode)
            KeyEvent.ACTION_UP -> handleKeyUp(event.keyCode)
            else -> false
        }
    }

    private fun handleKey(keyCode: Int): Boolean {
        val editorOpen = overlay.isEditorOpen()

        if (editorOpen) {
            if (keyCode == settings.menuKeyCode) overlay.saveAndCloseEditor()
            return true // editor swallows everything
        }

        if (keyCode == settings.menuKeyCode) { overlay.togglePanel(); return true }
        if (keyCode == settings.lockKeyCode) { toggleLock(); return true }

        if (joystick.onKey(keyCode, true)) return true
        if (actions.onKey(keyCode, true)) return true
        return false
    }

    private fun handleKeyUp(keyCode: Int): Boolean {
        if (overlay.isEditorOpen()) return true
        // menu/lock keys are instantaneous, no up handling
        if (keyCode == settings.menuKeyCode || keyCode == settings.lockKeyCode) return true
        if (joystick.onKey(keyCode, false)) return true
        if (actions.onKey(keyCode, false)) return true
        return false
    }

    private fun isMappedKey(keyCode: Int): Boolean {
        val p = profile ?: return false
        val joy = p.joystick
        if (keyCode == joy.keyUp || keyCode == joy.keyDown ||
            keyCode == joy.keyLeft || keyCode == joy.keyRight || keyCode == joy.walkKey
        ) return true
        return p.bindings.any { it.trigger.kind == TriggerKind.KEY && it.trigger.keyCode == keyCode }
    }

    // ---------------------------------------------------------------- mouse

    /** System-captured mouse events (Android 14+, only while locked). */
    fun onMotionEvent(event: MotionEvent) {
        val fromMouse = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
                event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        if (!fromMouse) return

        when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v != 0f) {
                    if (locked) actions.onWheel(v > 0f)
                    Diagnostics.log("mouse", "wheel ${if (v > 0) "up" else "down"}")
                }
            }

            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if (dx == 0f && dy == 0f) {
                    // fallback: position difference (degrades at screen edges)
                    if (haveLastPos) {
                        dx = event.x - lastX
                        dy = event.y - lastY
                    }
                }
                haveLastPos = true
                lastX = event.x
                lastY = event.y

                if (locked && (dx != 0f || dy != 0f)) camera.onDelta(dx, dy)

                if (moveLogCounter++ % 40 == 0) {
                    Diagnostics.log(
                        "mouse",
                        "move rel=(${event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)},${event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)}) pos=(${event.x.toInt()},${event.y.toInt()})"
                    )
                }
            }

            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (locked) {
                    kindFor(event.actionButton)?.let { actions.onMouseButton(it, true) }
                }
                Diagnostics.log("mouse", "button press ${kindFor(event.actionButton)}")
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (locked) {
                    kindFor(event.actionButton)?.let { actions.onMouseButton(it, false) }
                }
            }
        }
    }

    // Hover-fallback entry points (Android < 14)
    fun onHoverDelta(dx: Float, dy: Float) {
        if (locked) camera.onDelta(dx, dy)
    }

    fun onHoverButton(kind: TriggerKind, down: Boolean) {
        if (locked) actions.onMouseButton(kind, down)
    }

    fun onHoverWheel(up: Boolean) {
        if (locked) actions.onWheel(up)
    }

    private fun kindFor(button: Int): TriggerKind? = when (button) {
        MotionEvent.BUTTON_PRIMARY -> TriggerKind.MOUSE_PRIMARY
        MotionEvent.BUTTON_SECONDARY -> TriggerKind.MOUSE_SECONDARY
        MotionEvent.BUTTON_TERTIARY -> TriggerKind.MOUSE_TERTIARY
        MotionEvent.BUTTON_BACK -> TriggerKind.MOUSE_BACK
        MotionEvent.BUTTON_FORWARD -> TriggerKind.MOUSE_FORWARD
        else -> null
    }
}
