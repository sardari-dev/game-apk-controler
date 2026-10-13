package com.gamebridge.km.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/** Screen geometry; converts profile fractions to pixels. */
data class ScreenGeom(val w: Float, val h: Float) {
    val minDim: Float get() = kotlin.math.min(w, h)
    companion object {
        fun from(service: AccessibilityService): ScreenGeom {
            return try {
                val wm = service.getSystemService(android.view.WindowManager::class.java)
                if (wm != null && android.os.Build.VERSION.SDK_INT >= 30) {
                    val b = wm.currentWindowMetrics.bounds
                    ScreenGeom(b.width().toFloat(), b.height().toFloat())
                } else {
                    @Suppress("DEPRECATION")
                    val d = service.getSystemService(android.view.Display::class.java)
                    val pt = android.graphics.Point()
                    @Suppress("DEPRECATION")
                    d?.getRealSize(pt)
                    ScreenGeom(pt.x.toFloat(), pt.y.toFloat())
                }
            } catch (t: Throwable) {
                ScreenGeom(1080f, 2400f)
            }
        }
    }
}

/**
 * Injects touch gestures via AccessibilityService.dispatchGesture().
 *
 * Uses "continuing strokes" (StrokeDescription.continueStroke) so pointers can
 * stay down across multiple dispatched gestures — required for a virtual
 * joystick (WASD drag) and camera look (mouse drag).
 *
 * All state is confined to a single background thread; every public method
 * just posts an operation. Only ONE master gesture is in flight at a time and
 * each dispatched gesture contains updates for ALL active slots (so it is safe
 * regardless of whether a new dispatch cancels prior ones).
 */
class GestureEngine(private val service: AccessibilityService) {

    companion object {
        const val SLOT_JOY = "joy"
        const val SLOT_CAM = "cam"
        fun slotFor(bindingId: String) = "a_$bindingId"

        private const val SEGMENT_MS = 24L
        private const val TAP_MS = 48L
        private const val KEEPALIVE_MS = 400L
        private const val WATCHDOG_MS = 2000L
    }

    private class Slot {
        var down = false
        var ending = false
        var curX = 0f; var curY = 0f
        var targetX = 0f; var targetY = 0f
        var lastStroke: GestureDescription.StrokeDescription? = null
        var lastDispatch = 0L
        var hasNextBegin = false
        var nextX = 0f; var nextY = 0f
    }

    private class OneShot(val x: Float, val y: Float, val durationMs: Long)

    private val thread = HandlerThread("GB-GestureEngine").apply { start() }
    private val handler = Handler(thread.looper)

    private val slots = HashMap<String, Slot>()
    private val oneShots = ArrayDeque<OneShot>()
    private var inFlight = false
    private var flightStart = 0L
    private var tickPosted = false

    // diagnostics
    @Volatile var strokesDispatched = 0L; private set
    @Volatile var strokesCancelled = 0L; private set
    @Volatile var activeSlots = 0; private set

    private val watchdog = Runnable {
        if (inFlight && SystemClock.uptimeMillis() - flightStart >= WATCHDOG_MS) {
            Diagnostics.log("engine", "watchdog: dispatch stuck, resetting")
            inFlight = false
            resetAfterCancel()
            scheduleTick()
        }
    }

    private val tick = Runnable {
        tickPosted = false
        if (inFlight) return@Runnable
        buildAndDispatch()
    }

    /** Press a pointer down at (x, y). If the slot is already down it is re-anchored. */
    fun begin(slotId: String, x: Float, y: Float) = post {
        val s = slot(slotId)
        if (s.down) {
            s.ending = true
            s.hasNextBegin = true; s.nextX = x; s.nextY = y
        } else {
            s.down = true; s.ending = false
            s.curX = x; s.curY = y; s.targetX = x; s.targetY = y
            s.lastStroke = null; s.hasNextBegin = false
        }
        scheduleTick()
    }

    /** Move an active pointer toward (x, y). */
    fun target(slotId: String, x: Float, y: Float) = post {
        val s = slots[slotId] ?: return@post
        if (s.down && !s.ending) {
            s.targetX = x; s.targetY = y
            scheduleTick()
        }
    }

    /** Lift the pointer up at its current target position. */
    fun end(slotId: String) = post {
        val s = slots[slotId] ?: return@post
        if (s.down) { s.ending = true; s.hasNextBegin = false; scheduleTick() }
    }

    /** Quick tap at (x, y). */
    fun tap(x: Float, y: Float, durationMs: Long = TAP_MS) = post {
        oneShots.add(OneShot(x, y, durationMs))
        scheduleTick()
    }

    /** Lift everything (joystick, camera, held actions). */
    fun releaseAll() = post {
        slots.values.forEach { if (it.down) { it.ending = true; it.hasNextBegin = false } }
        scheduleTick()
    }

    /** Fully clear engine state without dispatching ups (used when session dies). */
    fun hardReset() = post {
        slots.clear()
        oneShots.clear()
        inFlight = false
        handler.removeCallbacks(watchdog)
    }

    private fun post(op: () -> Unit) { handler.post { op() } }

    private fun slot(id: String) = slots.getOrPut(id) { Slot() }

    private fun scheduleTick() {
        if (!tickPosted) { tickPosted = true; handler.post(tick) }
    }

    private fun path(x0: Float, y0: Float, x1: Float, y1: Float): Path = Path().apply {
        moveTo(x0, y0); lineTo(x1, y1)
    }

    private fun endTime(stroke: GestureDescription.StrokeDescription): Long =
        stroke.startTime + stroke.duration

    private fun buildAndDispatch() {
        val builder = GestureDescription.Builder()
        val now = SystemClock.uptimeMillis()
        val commits = mutableListOf<() -> Unit>()
        var any = false

        for ((_, s) in slots) {
            when {
                s.ending && s.lastStroke != null -> {
                    val last = s.lastStroke!!
                    val stroke = last.continueStroke(
                        path(s.curX, s.curY, s.targetX, s.targetY),
                        endTime(last), SEGMENT_MS, false
                    )
                    builder.addStroke(stroke); any = true; s.lastDispatch = now
                    commits += {
                        s.lastStroke = null; s.down = false; s.ending = false
                        if (s.hasNextBegin) {
                            s.hasNextBegin = false
                            s.down = true
                            s.curX = s.nextX; s.curY = s.nextY
                            s.targetX = s.nextX; s.targetY = s.nextY
                            s.lastStroke = null
                        }
                    }
                }
                s.down && s.lastStroke == null -> {
                    // fresh pointer down
                    val stroke = GestureDescription.StrokeDescription(
                        path(s.curX, s.curY, s.targetX, s.targetY), 0, SEGMENT_MS, true
                    )
                    builder.addStroke(stroke); any = true; s.lastDispatch = now
                    commits += {
                        s.lastStroke = stroke
                        s.curX = s.targetX; s.curY = s.targetY
                    }
                }
                s.down && s.lastStroke != null -> {
                    val needsMove = s.targetX != s.curX || s.targetY != s.curY
                    val needsKeepalive = now - s.lastDispatch > KEEPALIVE_MS
                    if (needsMove || needsKeepalive) {
                        val last = s.lastStroke!!
                        val stroke = last.continueStroke(
                            path(s.curX, s.curY, s.targetX, s.targetY),
                            endTime(last), SEGMENT_MS, true
                        )
                        builder.addStroke(stroke); any = true; s.lastDispatch = now
                        commits += {
                            s.lastStroke = stroke
                            s.curX = s.targetX; s.curY = s.targetY
                        }
                    }
                }
                // ending with no stroke ever dispatched: just clean up
                s.ending && s.lastStroke == null -> {
                    s.ending = false; s.down = false; s.hasNextBegin = false
                }
            }
        }

        while (oneShots.isNotEmpty()) {
            val os = oneShots.removeFirst()
            builder.addStroke(
                GestureDescription.StrokeDescription(path(os.x, os.y, os.x, os.y), 0, os.durationMs, false)
            )
            any = true
        }

        activeSlots = slots.count { it.value.down }

        if (!any) return

        inFlight = true
        flightStart = SystemClock.uptimeMillis()
        handler.postDelayed(watchdog, WATCHDOG_MS)

        val dispatched = service.dispatchGesture(builder.build(), object :
            AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) {
                strokesDispatched++
                handler.post {
                    handler.removeCallbacks(watchdog)
                    inFlight = false
                    commits.forEach { it() }
                    activeSlots = slots.count { it.value.down }
                    if (slots.isNotEmpty() && slots.any { it.value.down || it.value.ending }) scheduleTick()
                }
            }

            override fun onCancelled(gesture: GestureDescription?) {
                strokesCancelled++
                handler.post {
                    handler.removeCallbacks(watchdog)
                    Diagnostics.log("engine", "gesture cancelled (strokes=$strokesDispatched cancels=$strokesCancelled)")
                    inFlight = false
                    resetAfterCancel()
                    scheduleTick()
                }
            }
        }, handler)

        if (!dispatched) {
            handler.removeCallbacks(watchdog)
            inFlight = false
            resetAfterCancel()
            Diagnostics.log("engine", "dispatchGesture returned false")
        }
    }

    private fun resetAfterCancel() {
        // Re-arm every logically-down slot so the next tick re-presses it.
        slots.values.forEach { if (it.down) it.lastStroke = null; it.ending = false }
        oneShots.clear()
    }
}
