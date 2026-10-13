package com.gamebridge.km.bridge

import android.os.Handler
import android.os.Looper
import com.gamebridge.km.data.CameraConfig
import kotlin.math.hypot

/**
 * Turns relative mouse deltas into a camera (look) drag.
 *
 * While deltas arrive, a continuous drag stroke is kept alive from the camera
 * anchor point. When the cumulative drift becomes too large the stroke is
 * seamlessly re-anchored (finger up + immediately down at the anchor — the
 * game sees no movement between the two, so the camera does not spin).
 */
class CameraController(private val engine: GestureEngine) {

    private val handler = Handler(Looper.getMainLooper())

    private var cfg: CameraConfig? = null
    private var geom: ScreenGeom? = null

    private var sensX = 1f
    private var sensY = 1f
    private var adsScale = 1f
    private var invertY = false

    private var profileScale = 1f
    private var adsProfileScale = 0.65f

    private var offsetX = 0f
    private var offsetY = 0f
    private var pendingX = 0f
    private var pendingY = 0f
    private var active = false
    private var ticking = false

    private val ticker = object : Runnable {
        override fun run() {
            ticking = false
            tick()
        }
    }

    fun updateGeometry(g: ScreenGeom) { geom = g }

    fun configure(
        c: CameraConfig?,
        sensX: Float,
        sensY: Float,
        adsScaleSetting: Float,
        invertY: Boolean,
        profileSens: Float,
        profileAds: Float
    ) {
        cfg = c
        this.sensX = sensX
        this.sensY = sensY
        this.adsScale = adsScaleSetting
        this.invertY = invertY
        this.profileScale = profileSens
        this.adsProfileScale = profileAds
    }

    fun setAimHeld(aim: Boolean) {
        adsScale = if (aim) adsProfileScale else 1f
    }

    fun onDelta(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        pendingX += dx
        pendingY += dy
        schedule()
    }

    private fun schedule() {
        if (!ticking) {
            ticking = true
            handler.postDelayed(ticker, 16)
        }
    }

    private fun tick() {
        val g = geom ?: return
        val c = cfg ?: return
        if (pendingX == 0f && pendingY == 0f) return

        val dx = pendingX * sensX * profileScale
        val dy = pendingY * sensY * profileScale * adsScale * (if (invertY) -1f else 1f)
        pendingX = 0f; pendingY = 0f

        offsetX += dx
        offsetY += dy

        val anchorX = c.anchorX * g.w
        val anchorY = c.anchorY * g.h
        val maxDrift = 0.20f * g.minDim

        if (hypot(offsetX, offsetY) > maxDrift) {
            // re-anchor: up + immediate down at anchor (no move event between → no spin)
            if (active) engine.end(GestureEngine.SLOT_CAM)
            engine.begin(GestureEngine.SLOT_CAM, anchorX, anchorY)
            active = true
            offsetX = 0f; offsetY = 0f
        } else {
            if (!active) {
                engine.begin(GestureEngine.SLOT_CAM, anchorX + dx, anchorY + dy)
                active = true
            }
            engine.target(GestureEngine.SLOT_CAM, anchorX + offsetX, anchorY + offsetY)
        }
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        ticking = false
        pendingX = 0f; pendingY = 0f
        offsetX = 0f; offsetY = 0f
        if (active) {
            engine.end(GestureEngine.SLOT_CAM)
            active = false
        }
    }
}
