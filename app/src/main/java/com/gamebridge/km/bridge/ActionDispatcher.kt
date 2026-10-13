package com.gamebridge.km.bridge

import com.gamebridge.km.data.ActionType
import com.gamebridge.km.data.KeyBinding
import com.gamebridge.km.data.TriggerKind

/**
 * Executes mapped key/button actions (tap / hold / toggle) through the
 * GestureEngine. Each binding owns its own engine slot.
 */
class ActionDispatcher(
    private val engine: GestureEngine,
    private var geomProvider: () -> ScreenGeom
) {

    private var bindings: List<KeyBinding> = emptyList()
    private val holdDown = HashSet<String>()   // binding ids currently held
    private val toggleOn = HashSet<String>()   // binding ids toggled on

    /** Notifies the camera controller that ADS state changed. */
    var onAimStateChanged: ((Boolean) -> Unit)? = null

    fun updateBindings(list: List<KeyBinding>) {
        releaseAll()
        bindings = list
    }

    fun onKey(keyCode: Int, down: Boolean): Boolean {
        val b = bindings.firstOrNull { it.trigger.kind == TriggerKind.KEY && it.trigger.keyCode == keyCode }
            ?: return false
        handle(b, down)
        return true
    }

    fun onMouseButton(kind: TriggerKind, down: Boolean): Boolean {
        val b = bindings.firstOrNull { it.trigger.kind == kind } ?: return false
        handle(b, down)
        return true
    }

    fun onWheel(up: Boolean): Boolean {
        val kind = if (up) TriggerKind.WHEEL_UP else TriggerKind.WHEEL_DOWN
        val b = bindings.firstOrNull { it.trigger.kind == kind } ?: return false
        val g = geomProvider()
        engine.tap(b.x * g.w, b.y * g.h)
        return true
    }

    private fun handle(b: KeyBinding, down: Boolean) {
        val g = geomProvider()
        val x = b.x * g.w
        val y = b.y * g.h
        val slot = GestureEngine.slotFor(b.id)
        when (b.type) {
            ActionType.TAP -> if (down) engine.tap(x, y)
            ActionType.HOLD -> {
                if (down) {
                    engine.begin(slot, x, y); holdDown.add(b.id)
                } else {
                    engine.end(slot); holdDown.remove(b.id)
                }
            }
            ActionType.TOGGLE -> if (down) {
                if (toggleOn.contains(b.id)) {
                    engine.end(slot); toggleOn.remove(b.id)
                } else {
                    engine.begin(slot, x, y); toggleOn.add(b.id)
                }
            }
        }
        if (b.isAim) onAimStateChanged?.invoke(isAimActive())
    }

    private fun isAimActive(): Boolean =
        bindings.any { it.isAim && (it.id in holdDown || it.id in toggleOn) }

    fun releaseAll() {
        (holdDown + toggleOn).forEach { id -> engine.end(GestureEngine.slotFor(id)) }
        holdDown.clear()
        toggleOn.clear()
        onAimStateChanged?.invoke(false)
    }
}
