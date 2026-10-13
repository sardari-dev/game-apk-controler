package com.gamebridge.km.data

import android.view.KeyEvent

object DefaultProfiles {

    /** Known game package names, used to pin them in the game list. */
    val knownGamePackages = listOf(
        "com.activision.calvin.gcp",   // Call of Duty Mobile (Global)
        "com.garena.game.codm",        // CODM (Garena)
        "com.vng.games.codm",          // CODM (VNG)
        "com.tencent.calvin",          // CODM (CN)
        "com.tencent.ig",              // PUBG Mobile
        "com.dts.freefireth",          // Free Fire
        "com.mobile.legends",          // Mobile Legends
        "com.RoamingStar.Sausage"      // etc.
    )

    fun codMobile(): KeymapProfile = KeymapProfile(
        id = "preset-codm",
        name = "COD Mobile — پیش‌فرض",
        packageName = null,
        joystick = JoystickConfig(
            centerX = 0.185f,
            centerY = 0.640f,
            radiusFrac = 0.105f,
            walkDeflection = 0.42f,
            keyUp = KeyEvent.KEYCODE_W,
            keyDown = KeyEvent.KEYCODE_S,
            keyLeft = KeyEvent.KEYCODE_A,
            keyRight = KeyEvent.KEYCODE_D,
            walkKey = KeyEvent.KEYCODE_SHIFT_LEFT
        ),
        camera = CameraConfig(anchorX = 0.72f, anchorY = 0.42f),
        sensitivity = 1.0f,
        adsSensitivityScale = 0.65f,
        bindings = listOf(
            KeyBinding("b-fire", "Fire / شلیک", Trigger(TriggerKind.MOUSE_PRIMARY), ActionType.HOLD, 0.895f, 0.815f),
            KeyBinding("b-ads", "Aim / نشانه‌گیری", Trigger(TriggerKind.MOUSE_SECONDARY), ActionType.HOLD, 0.860f, 0.720f, isAim = true),
            KeyBinding("b-jump", "Jump / پرش", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_SPACE), ActionType.TAP, 0.925f, 0.600f),
            KeyBinding("b-crouch", "Crouch / نشستن", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_CTRL_LEFT), ActionType.TAP, 0.885f, 0.935f),
            KeyBinding("b-prone", "Prone / خوابیدن", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_Z), ActionType.TAP, 0.800f, 0.955f),
            KeyBinding("b-reload", "Reload / خشاب", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_R), ActionType.TAP, 0.755f, 0.575f),
            KeyBinding("b-grenade", "Grenade / نارنجک", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_G), ActionType.TAP, 0.685f, 0.900f),
            KeyBinding("b-melee", "Melee / چاقو", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_V), ActionType.TAP, 0.940f, 0.700f),
            KeyBinding("b-skill", "Skill / مهارت", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_F), ActionType.TAP, 0.615f, 0.885f),
            KeyBinding("b-w1", "Weapon 1", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_1), ActionType.TAP, 0.895f, 0.475f),
            KeyBinding("b-w2", "Weapon 2", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_2), ActionType.TAP, 0.845f, 0.500f),
            KeyBinding("b-interact", "Interact / برداشتن", Trigger(TriggerKind.KEY, KeyEvent.KEYCODE_H), ActionType.TAP, 0.620f, 0.550f)
        )
    )

    fun defaults(): List<KeymapProfile> = listOf(codMobile())
}
