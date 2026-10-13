package com.gamebridge.km.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.gamebridge.km.R
import com.gamebridge.km.bridge.InputRouter
import com.gamebridge.km.data.ActionType
import com.gamebridge.km.data.CameraConfig
import com.gamebridge.km.data.JoystickConfig
import com.gamebridge.km.data.KeyBinding
import com.gamebridge.km.data.KeymapProfile
import com.gamebridge.km.data.Trigger
import com.gamebridge.km.data.TriggerKind
import com.gamebridge.km.data.triggerDisplay
import com.google.android.material.slider.Slider

/**
 * Full-screen in-game keymap editor overlay. Drag widgets to reposition them,
 * tap a widget for options, press keys to re-assign triggers. Live over the
 * actual game so placement is pixel-perfect.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class KeymapEditorOverlay(
    context: Context,
    private val router: InputRouter,
    private val initialProfile: KeymapProfile
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var joyCfg: JoystickConfig = initialProfile.joystick
    private var camCfg: CameraConfig = initialProfile.camera
    private val bindings = initialProfile.bindings.map { it.copy() }.toMutableList()

    private val widgetLayer = FrameLayout(context)
    private val canvas: EditorCanvas
    private val chips = HashMap<String, TextView>()

    var listeningForAssignment = false
        private set

    init {
        setBackgroundColor(0x88000000.toInt())

        canvas = EditorCanvas(context) { joy, cam ->
            joyCfg = joy
            camCfg = cam
        }
        canvas.setTapListener(
            onJoystickTap = { showJoystickOptions() },
            onAnchorTap = { /* anchor: drag only */ }
        )
        widgetLayer.addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(widgetLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addToolbar()
        rebuildChips()

        router.keyCaptureCallback = null
        router.mouseCaptureCallback = null
    }

    // ------------------------------------------------------------- toolbar

    private fun addToolbar() {
        val hint = TextView(context).apply {
            text = context.getString(R.string.oe_drag_hint)
            setTextColor(Color.parseColor("#FBBF24"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(0x66000000)
        }
        addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE101014.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        fun btn(label: String, accent: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(if (accent) Color.parseColor("#1A1206") else Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            background.mutate().setTint(if (accent) Color.parseColor("#F59E0B") else Color.parseColor("#3F3F46"))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { onClick() }
        }
        bar.addView(
            btn("+ ${context.getString(R.string.oe_new_button)}", false) { addNewBinding() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
        )
        bar.addView(
            btn(context.getString(R.string.oe_save), true) {
                router.keyCaptureCallback = null
                router.mouseCaptureCallback = null
                requestSave()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
        )
        bar.addView(
            btn("✕", false) {
                router.keyCaptureCallback = null
                router.mouseCaptureCallback = null
                requestClose()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    private fun requestSave() {
        saveRequested = true
        // give the layout pass a chance so chip positions are up to date
        post { requestClose() }
    }

    private var saveRequested = false

    private fun requestClose() {
        router.keyCaptureCallback = null
        router.mouseCaptureCallback = null
        val svc = com.gamebridge.km.bridge.BridgeService.instance ?: return
        if (saveRequested) svc.overlayManager.saveAndCloseEditor()
        else svc.overlayManager.closeEditorWithoutSaving()
    }

    // --------------------------------------------------------------- chips

    private fun rebuildChips() {
        chips.values.forEach { widgetLayer.removeView(it) }
        chips.clear()
        bindings.forEach { b -> addChipFor(b) }
        canvas.setConfigs(joyCfg, camCfg)
    }

    private fun addChipFor(b: KeyBinding) {
        val chip = TextView(context).apply {
            text = "${b.label} · ${triggerDisplay(b.trigger)}"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            tag = b.id
        }
        chip.setOnTouchListener(ChipDragListener(b.id, chip))
        widgetLayer.addView(
            chip,
            FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        chip.post { positionChip(chip, b.x, b.y) }
        chips[b.id] = chip
    }

    private fun positionChip(chip: TextView, fx: Float, fy: Float) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        (chip.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = (fx * w - chip.width / 2f).toInt()
            topMargin = (fy * h - chip.height / 2f).toInt()
        }
        chip.requestLayout()
    }

    private fun chipFraction(chip: TextView): Pair<Float, Float> {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val cx = chip.left + chip.width / 2f
        val cy = chip.top + chip.height / 2f
        return (cx / w).coerceIn(0.02f, 0.98f) to (cy / h).coerceIn(0.02f, 0.98f)
    }

    private inner class ChipDragListener(private val bindingId: String, private val chip: TextView) :
        OnTouchListener {

        private var downX = 0f
        private var downY = 0f
        private var startLeft = 0
        private var startTop = 0
        private var moved = false
        private val slop = 6 * density

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = chip.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startLeft = lp.leftMargin
                    startTop = lp.topMargin
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (moved || dx * dx + dy * dy > slop * slop) {
                        moved = true
                        lp.leftMargin = startLeft + dx.toInt()
                        lp.topMargin = startTop + dy.toInt()
                        chip.requestLayout()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        performClick()
                        onChipTapped(bindingId, chip)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    // -------------------------------------------------------- chip options

    private fun onChipTapped(bindingId: String, anchor: View) {
        val idx = bindings.indexOfFirst { it.id == bindingId }
        if (idx < 0) return
        val b = bindings[idx]

        val popup = android.widget.PopupMenu(context, anchor)
        popup.menu.add(context.getString(R.string.oe_change_key))
        popup.menu.add("${context.getString(R.string.oe_action_type)}: ${actionName(b.type)}")
        popup.menu.add(context.getString(R.string.rename))
        popup.menu.add(context.getString(R.string.oe_delete))
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                context.getString(R.string.oe_change_key) -> startAssignment(bindingId)
                context.getString(R.string.oe_action_type) -> {
                    val next = when (b.type) {
                        ActionType.TAP -> ActionType.HOLD
                        ActionType.HOLD -> ActionType.TOGGLE
                        ActionType.TOGGLE -> ActionType.TAP
                    }
                    bindings[idx] = b.copy(type = next)
                    refreshChip(bindings[idx])
                }
                context.getString(R.string.rename) -> showRenameDialog(bindings[idx])
                context.getString(R.string.oe_delete) -> {
                    bindings.removeAt(idx)
                    chips[bindingId]?.let { widgetLayer.removeView(it) }
                    chips.remove(bindingId)
                }
            }
            true
        }
        popup.show()
    }

    private fun actionName(t: ActionType): String = when (t) {
        ActionType.TAP -> context.getString(R.string.action_tap)
        ActionType.HOLD -> context.getString(R.string.action_hold)
        ActionType.TOGGLE -> context.getString(R.string.action_toggle)
    }

    private fun refreshChip(b: KeyBinding) {
        chips[b.id]?.text = "${b.label} · ${triggerDisplay(b.trigger)}"
    }

    private fun startAssignment(bindingId: String) {
        val idx = bindings.indexOfFirst { it.id == bindingId }
        if (idx < 0) return
        listeningForAssignment = true
        chips[bindingId]?.text = context.getString(R.string.oe_listening)

        fun assign(trigger: Trigger) {
            bindings[idx] = bindings[idx].copy(trigger = trigger)
            refreshChip(bindings[idx])
            stopAssignment()
        }

        router.keyCaptureCallback = { keyCode -> assign(Trigger(TriggerKind.KEY, keyCode)) }
        router.mouseCaptureCallback = { kind -> assign(Trigger(kind)) }
    }

    private fun stopAssignment() {
        listeningForAssignment = false
        router.keyCaptureCallback = null
        router.mouseCaptureCallback = null
    }

    /** Mouse buttons & wheel arrive here while unlocked (assignment mode). */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (listeningForAssignment) {
            when (event.actionMasked) {
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    val kind = when (event.actionButton) {
                        MotionEvent.BUTTON_PRIMARY -> TriggerKind.MOUSE_PRIMARY
                        MotionEvent.BUTTON_SECONDARY -> TriggerKind.MOUSE_SECONDARY
                        MotionEvent.BUTTON_TERTIARY -> TriggerKind.MOUSE_TERTIARY
                        MotionEvent.BUTTON_BACK -> TriggerKind.MOUSE_BACK
                        MotionEvent.BUTTON_FORWARD -> TriggerKind.MOUSE_FORWARD
                        else -> null
                    }
                    if (kind != null) {
                        router.mouseCaptureCallback?.invoke(kind)
                        return true
                    }
                }
                MotionEvent.ACTION_SCROLL -> {
                    val up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f
                    router.mouseCaptureCallback?.invoke(if (up) TriggerKind.WHEEL_UP else TriggerKind.WHEEL_DOWN)
                    return true
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ---------------------------------------------------------- add / rename

    private var newCounter = 1

    private fun addNewBinding() {
        val id = "b-custom-${System.currentTimeMillis()}"
        val b = KeyBinding(
            id = id,
            label = "${context.getString(R.string.oe_new_button)} $newCounter",
            trigger = Trigger(TriggerKind.KEY, android.view.KeyEvent.KEYCODE_UNKNOWN),
            type = ActionType.TAP,
            x = 0.5f,
            y = 0.35f
        )
        newCounter++
        bindings.add(b)
        addChipFor(b)
    }

    private fun showRenameDialog(b: KeyBinding) {
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFF16161C.toInt())
        }
        val input = EditText(context).apply {
            setText(b.label)
            setTextColor(Color.WHITE)
            hint = context.getString(R.string.button_label)
        }
        wrap.addView(input)
        val ok = TextView(context).apply {
            text = context.getString(R.string.ok)
            setTextColor(Color.parseColor("#F59E0B"))
            setPadding(dp(12), dp(12), dp(12), dp(4))
            setOnClickListener {
                val idx = bindings.indexOfFirst { it.id == b.id }
                if (idx >= 0 && input.text.isNotBlank()) {
                    bindings[idx] = bindings[idx].copy(label = input.text.toString())
                    refreshChip(bindings[idx])
                }
                popupDismiss()
            }
        }
        wrap.addView(ok)
        popupWindow(wrap, chips[b.id])
    }

    private fun showJoystickOptions() {
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFF16161C.toInt())
        }
        fun rowLabel(text: String): TextView = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val radiusLabel = rowLabel("${context.getString(R.string.joystick_radius)}: ${(joyCfg.radiusFrac * 100).toInt()}%")
        val radius = Slider(context).apply {
            valueFrom = 0.05f
            valueTo = 0.20f
            stepSize = 0.005f
            value = joyCfg.radiusFrac
            addOnChangeListener { _, v, _ ->
                joyCfg = joyCfg.copy(radiusFrac = v)
                radiusLabel.text = "${context.getString(R.string.joystick_radius)}: ${(v * 100).toInt()}%"
                canvas.setConfigs(joyCfg, camCfg)
            }
        }
        val walkLabel = rowLabel("${context.getString(R.string.oe_walk_deflection)}: ${(joyCfg.walkDeflection * 100).toInt()}%")
        val walk = Slider(context).apply {
            valueFrom = 0.15f
            valueTo = 0.90f
            stepSize = 0.05f
            value = joyCfg.walkDeflection
            addOnChangeListener { _, v, _ ->
                joyCfg = joyCfg.copy(walkDeflection = v)
                walkLabel.text = "${context.getString(R.string.oe_walk_deflection)}: ${(v * 100).toInt()}%"
            }
        }
        wrap.addView(radiusLabel)
        wrap.addView(radius)
        wrap.addView(walkLabel)
        wrap.addView(walk)
        popupWindow(wrap, null)
    }

    private var activePopup: PopupWindow? = null

    private fun popupWindow(content: View, anchor: View?) {
        val pw = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setOnDismissListener { activePopup = null }
        activePopup = pw
        if (anchor != null) pw.showAsDropDown(anchor) else pw.showAtLocation(this, Gravity.CENTER, 0, 0)
    }

    private fun popupDismiss() {
        activePopup?.dismiss()
        activePopup = null
    }

    // ------------------------------------------------------------ save

    fun buildProfile(): KeymapProfile {
        val updated = bindings.map { b ->
            chips[b.id]?.let { chip ->
                val (fx, fy) = chipFraction(chip)
                b.copy(x = fx, y = fy)
            } ?: b
        }
        return initialProfile.copy(
            joystick = joyCfg,
            camera = camCfg,
            bindings = updated
        )
    }

    override fun onDetachedFromWindow() {
        stopAssignment()
        super.onDetachedFromWindow()
    }

    // =============================================================== canvas

    /**
     * Draws the joystick zone + camera anchor and handles their drag gestures.
     */
    private inner class EditorCanvas(
        context: Context,
        private val onConfig: (JoystickConfig, CameraConfig) -> Unit
    ) : View(context) {

        private val joyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33F59E0B
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(0xFFF59E0B.toInt())
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(0xFFF59E0B.toInt())
            style = Paint.Style.FILL
        }
        private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setColor(0xFF34D399.toInt())
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
        }

        private var joy = joyCfg
        private var cam = camCfg

        fun setConfigs(j: JoystickConfig, c: CameraConfig) {
            joy = j; cam = c
            invalidate()
        }

        fun setTapListener(onJoystickTap: () -> Unit, onAnchorTap: () -> Unit) {
            joyTap = onJoystickTap; anchorTap = onAnchorTap
        }

        private var joyTap: (() -> Unit)? = null
        private var anchorTap: (() -> Unit)? = null

        private var mode = 0 // 0 none, 1 joy center, 2 joy radius, 3 anchor
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private val slop = 6 * density

        private fun joyCX() = joy.centerX * width
        private fun joyCY() = joy.centerY * height
        private fun joyR() = joy.radiusFrac * minOf(width, height)
        private fun anchorX() = cam.anchorX * width
        private fun anchorY() = cam.anchorY * height

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // joystick
            canvas.drawCircle(joyCX(), joyCY(), joyR(), joyPaint)
            canvas.drawCircle(joyCX(), joyCY(), joyR(), strokePaint)
            canvas.drawCircle(joyCX(), joyCY(), 8f * density, handlePaint)
            // radius handle at 45°
            val hx = joyCX() + joyR() * 0.7071f
            val hy = joyCY() + joyR() * 0.7071f
            canvas.drawCircle(hx, hy, 12f * density, handlePaint)
            // anchor crosshair
            val ax = anchorX(); val ay = anchorY()
            canvas.drawCircle(ax, ay, 10f * density, anchorPaint)
            canvas.drawLine(ax - 20f * density, ay, ax + 20f * density, ay, anchorPaint)
            canvas.drawLine(ax, ay - 20f * density, ax, ay + 20f * density, anchorPaint)

            textPaint.textSize = 12f * density
            canvas.drawText("WASD", joyCX(), joyCY() + 30f * density, textPaint)
            canvas.drawText("CAM", ax, ay + 34f * density, textPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; moved = false
                    mode = hitTest(event.x, event.y)
                    return mode != 0
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode != 0) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (moved || dx * dx + dy * dy > slop * slop) {
                            moved = true
                            when (mode) {
                                1 -> {
                                    joy = joy.copy(
                                        centerX = (event.x / width).coerceIn(0.05f, 0.95f),
                                        centerY = (event.y / height).coerceIn(0.05f, 0.95f)
                                    )
                                }
                                2 -> {
                                    val r = kotlin.math.hypot(event.x - joyCX(), event.y - joyCY())
                                    joy = joy.copy(radiusFrac = (r / minOf(width, height)).coerceIn(0.05f, 0.20f))
                                }
                                3 -> {
                                    cam = cam.copy(
                                        anchorX = (event.x / width).coerceIn(0.05f, 0.95f),
                                        anchorY = (event.y / height).coerceIn(0.05f, 0.95f)
                                    )
                                }
                            }
                            joyCfg = joy; camCfg = cam
                            onConfig(joy, cam)
                            invalidate()
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode != 0 && !moved) {
                        when (mode) {
                            1, 2 -> joyTap?.invoke()
                            3 -> anchorTap?.invoke()
                        }
                    }
                    mode = 0
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { mode = 0; return true }
            }
            return super.onTouchEvent(event)
        }

        private fun hitTest(x: Float, y: Float): Int {
            val hx = joyCX() + joyR() * 0.7071f
            val hy = joyCY() + joyR() * 0.7071f
            val handleR = 26f * density
            if (kotlin.math.hypot(x - hx, y - hy) < handleR) return 2
            if (kotlin.math.hypot(x - anchorX(), y - anchorY()) < 34f * density) return 3
            if (kotlin.math.hypot(x - joyCX(), y - joyCY()) < joyR() * 1.15f) return 1
            return 0
        }
    }
}
