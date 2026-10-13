package com.gamebridge.km.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/** Small draggable floating button shown while a game session is active. */
@SuppressLint("ClickableViewAccessibility")
class FloatingButtonView(
    context: Context,
    private val onToggle: () -> Unit,
    private val onPositionChanged: (Float, Float) -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val sizePx = (52 * density).toInt()
    private val slop = 8 * density

    private var downRawX = 0f
    private var downRawY = 0f
    private var paramX = 0
    private var paramY = 0
    private var moved = false

    private val icon: ImageView

    var locked = false
        private set

    init {
        icon = ImageView(context).apply {
            setImageResource(com.gamebridge.km.R.drawable.ic_floatie_gamepad)
            setColorFilter(Color.WHITE)
            setBackgroundResource(com.gamebridge.km.R.drawable.bg_floatie)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        addView(icon, LayoutParams(sizePx, sizePx, Gravity.CENTER))
        layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
    }

    fun setLocked(value: Boolean) {
        locked = value
        icon.background?.mutate()?.setTint(
            if (value) Color.parseColor("#F59E0B") else Color.parseColor("#3F3F46")
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLocked(locked)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wmParams = layoutParams as? android.view.WindowManager.LayoutParams
            ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                paramX = wmParams.x
                paramY = wmParams.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (moved || dx * dx + dy * dy > slop * slop) {
                    moved = true
                    wmParams.x = paramX + dx.toInt()
                    wmParams.y = paramY + dy.toInt()
                    updatePosition(wmParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    performClick()
                    onToggle()
                } else {
                    // report fractions using the display size
                    val w = resources.displayMetrics.widthPixels.toFloat()
                    val h = resources.displayMetrics.heightPixels.toFloat()
                    onPositionChanged(
                        (wmParams.x / w).coerceIn(0f, 1f),
                        (wmParams.y / h).coerceIn(0f, 1f)
                    )
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePosition(params: android.view.WindowManager.LayoutParams) {
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .updateViewLayout(this, params)
        } catch (_: Exception) {
        }
    }
}
