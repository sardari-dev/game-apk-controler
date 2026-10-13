package com.gamebridge.km.bridge

import com.gamebridge.km.data.JoystickConfig
import kotlin.math.sqrt

/**
 * Maps WASD-style keys to an on-screen analog joystick drag.
 *
 * Pressing a direction key starts a touch at the stick center and drags toward
 * the direction; changing keys drags to the new point; releasing all keys
 * returns the stick to center and lifts the finger — exactly how a human
 * controls a floating joystick.
 */
class JoystickController(private val engine: GestureEngine) {

    private var cfg: JoystickConfig? = null
    private var geom: ScreenGeom? = null
    private val held = HashSet<Int>()
    private var walkHeld = false
    private var active = false

    fun updateGeometry(g: ScreenGeom) { geom = g }

    fun updateConfig(c: JoystickConfig?) {
        if (cfg != c) {
            cfg = c
            releaseAll()
        }
    }

    /** Returns true if the event was consumed as a movement key. */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        val c = cfg ?: return false
        val isWalk = keyCode == c.walkKey
        val isDir = keyCode == c.keyUp || keyCode == c.keyDown ||
                keyCode == c.keyLeft || keyCode == c.keyRight
        if (!isWalk && !isDir) return false
        if (down) {
            if (isWalk) walkHeld = true else held.add(keyCode)
        } else {
            if (isWalk) walkHeld = false else held.remove(keyCode)
        }
        apply()
        return true
    }

    private fun apply() {
        val c = cfg ?: return
        val g = geom ?: return
        val centerX = c.centerX * g.w
        val centerY = c.centerY * g.h
        val radius = c.radiusFrac * g.minDim

        var dx = 0f
        var dy = 0f
        if (c.keyLeft in held) dx -= 1f
        if (c.keyRight in held) dx += 1f
        if (c.keyUp in held) dy -= 1f
        if (c.keyDown in held) dy += 1f

        if (dx == 0f && dy == 0f) {
            if (active) {
                // release: return stick to center, then lift finger
                engine.target(GestureEngine.SLOT_JOY, centerX, centerY)
                engine.end(GestureEngine.SLOT_JOY)
                active = false
            }
            return
        }

        val len = sqrt(dx * dx + dy * dy) // 1.0 or 1.414 for diagonals
        val magnitude = (if (walkHeld) c.walkDeflection else 1f) * radius
        val px = centerX + (dx / len) * magnitude
        val py = centerY + (dy / len) * magnitude

        if (!active) {
            engine.begin(GestureEngine.SLOT_JOY, centerX, centerY)
            active = true
        }
        engine.target(GestureEngine.SLOT_JOY, px, py)
    }

    fun releaseAll() {
        held.clear()
        walkHeld = false
        if (active) {
            engine.end(GestureEngine.SLOT_JOY)
            active = false
        }
    }

    fun isActive(): Boolean = active
}
