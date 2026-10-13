package com.gamebridge.km.data

import android.view.KeyEvent
import kotlinx.serialization.Serializable

/**
 * A full keymap profile. All coordinates are fractions of the screen (0f..1f)
 * so the same profile works on every resolution.
 */
@Serializable
data class KeymapProfile(
    val id: String,
    val name: String,
    val packageName: String? = null,
    val joystick: JoystickConfig = JoystickConfig(),
    val camera: CameraConfig = CameraConfig(),
    val bindings: List<KeyBinding> = emptyList(),
    /** Per-profile sensitivity multiplier applied on top of global settings. */
    val sensitivity: Float = 1.0f,
    /** Multiplier applied while an "aim" binding is held. */
    val adsSensitivityScale: Float = 0.65f
)

@Serializable
data class JoystickConfig(
    val centerX: Float = 0.185f,
    val centerY: Float = 0.640f,
    /** Radius as a fraction of min(screenWidth, screenHeight). */
    val radiusFrac: Float = 0.105f,
    /** Deflection used while the walk-modifier key is held (slow walk). */
    val walkDeflection: Float = 0.42f,
    val keyUp: Int = KeyEvent.KEYCODE_W,
    val keyDown: Int = KeyEvent.KEYCODE_S,
    val keyLeft: Int = KeyEvent.KEYCODE_A,
    val keyRight: Int = KeyEvent.KEYCODE_D,
    val walkKey: Int = KeyEvent.KEYCODE_SHIFT_LEFT
)

@Serializable
data class CameraConfig(
    val anchorX: Float = 0.72f,
    val anchorY: Float = 0.42f
)

@Serializable
enum class ActionType { TAP, HOLD, TOGGLE }

@Serializable
enum class TriggerKind { KEY, MOUSE_PRIMARY, MOUSE_SECONDARY, MOUSE_TERTIARY, MOUSE_BACK, MOUSE_FORWARD, WHEEL_UP, WHEEL_DOWN }

@Serializable
data class Trigger(
    val kind: TriggerKind = TriggerKind.KEY,
    val keyCode: Int = 0
)

@Serializable
data class KeyBinding(
    val id: String,
    val label: String,
    val trigger: Trigger = Trigger(),
    val type: ActionType = ActionType.TAP,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** When true, holding this binding switches camera to ADS sensitivity. */
    val isAim: Boolean = false
)

fun triggerDisplay(trigger: Trigger): String = when (trigger.kind) {
    TriggerKind.KEY -> keyLabel(trigger.keyCode)
    TriggerKind.MOUSE_PRIMARY -> "🖱L"
    TriggerKind.MOUSE_SECONDARY -> "🖱R"
    TriggerKind.MOUSE_TERTIARY -> "🖱M"
    TriggerKind.MOUSE_BACK -> "🖱4"
    TriggerKind.MOUSE_FORWARD -> "🖱5"
    TriggerKind.WHEEL_UP -> "wheel ▲"
    TriggerKind.WHEEL_DOWN -> "wheel ▼"
}

fun keyLabel(keyCode: Int): String {
    val raw = KeyEvent.keyCodeToString(keyCode) // e.g. KEYCODE_W
    return raw.removePrefix("KEYCODE_").replace('_', ' ')
}
