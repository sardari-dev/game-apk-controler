package com.gamebridge.km.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.gamebridge.km.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/**
 * The in-game quick settings panel opened from the floating button or F7:
 * lock toggle, sensitivity slider, keymap editor, hide, exit.
 */
@SuppressLint("ViewConstructor")
class QuickPanelView(
    context: Context,
    profileName: String,
    locked: Boolean,
    sensitivity: Float,
    private val onToggleLock: () -> Unit,
    private val onSensitivityChanged: (Float) -> Unit,
    private val onEditKeymap: () -> Unit,
    private val onHide: () -> Unit,
    private val onExit: () -> Unit
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var lockState: Boolean = locked
    private val lockSwitch: MaterialSwitch
    private val sensValue: TextView

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        setPadding(dp(20), dp(18), dp(20), dp(18))

        val title = TextView(context).apply {
            text = profileName
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val lockRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(6))
        }
        val lockLabel = TextView(context).apply {
            text = context.getString(R.string.panel_lock_mouse)
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        lockSwitch = MaterialSwitch(context).apply {
            isChecked = locked // set BEFORE the listener to avoid a spurious callback
        }
        lockSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != lockState) {
                lockState = checked
                onToggleLock()
            }
        }
        lockRow.addView(lockLabel, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        lockRow.addView(lockSwitch)
        addView(lockRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val sensLabel = TextView(context).apply {
            text = context.getString(R.string.panel_sensitivity)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
        }
        addView(sensLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val sensRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val slider = Slider(context).apply {
            valueFrom = 0.1f
            valueTo = 5f
            stepSize = 0.05f
            value = sensitivity.coerceIn(0.1f, 5f)
            addOnChangeListener { _, v, _ -> sensValue.text = String.format("%.2f", v) }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    onSensitivityChanged(slider.value)
                }
            })
        }
        sensValue = TextView(context).apply {
            text = String.format("%.2f", sensitivity)
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 14f
        }
        sensRow.addView(slider, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        sensRow.addView(sensValue)
        addView(sensRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val btnRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        fun btn(label: String, onClick: () -> Unit): Button = Button(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background.setTint(Color.parseColor("#27272F"))
            setOnClickListener { onClick() }
        }
        btnRow.addView(
            btn(context.getString(R.string.panel_edit_keymap)) { onEditKeymap() },
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        )
        btnRow.addView(
            btn(context.getString(R.string.panel_hide)) { onHide() },
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        )
        btnRow.addView(
            btn(context.getString(R.string.panel_exit)) { onExit() },
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(btnRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun updateLockState(locked: Boolean) {
        lockState = locked
        lockSwitch.isChecked = locked
    }
}
