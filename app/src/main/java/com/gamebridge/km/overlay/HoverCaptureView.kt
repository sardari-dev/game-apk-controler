package com.gamebridge.km.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.gamebridge.km.bridge.InputRouter
import com.gamebridge.km.data.TriggerKind

/**
 * Android < 14 fallback: a transparent full-screen layer that tracks mouse
 * hover movement / buttons / wheel. Also attempts requestPointerCapture() for
 * true relative deltas (works only when the window can take focus, but the
 * attempt is harmless).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HoverCaptureView(
    context: Context,
    private val router: InputRouter
) : View(context) {

    private var lastX = -1f
    private var lastY = -1f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching { requestPointerCapture() }
        com.gamebridge.km.bridge.Diagnostics.log("hover", "hover layer attached")
    }

    override fun onDetachedFromWindow() {
        runCatching { releasePointerCapture() }
        super.onDetachedFromWindow()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (lastX >= 0f) router.onHoverDelta(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_CANCEL -> {
                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                router.onHoverWheel(event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                kindFor(event.actionButton)?.let { router.onHoverButton(it, true) }
                return true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                kindFor(event.actionButton)?.let { router.onHoverButton(it, false) }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /** True relative deltas if pointer capture succeeded. */
    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE ->
                router.onHoverDelta(event.x, event.y)
            MotionEvent.ACTION_SCROLL ->
                router.onHoverWheel(event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f)
            MotionEvent.ACTION_BUTTON_PRESS ->
                kindFor(event.actionButton)?.let { router.onHoverButton(it, true) }
            MotionEvent.ACTION_BUTTON_RELEASE ->
                kindFor(event.actionButton)?.let { router.onHoverButton(it, false) }
        }
        return true
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
