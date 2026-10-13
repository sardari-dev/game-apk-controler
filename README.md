# GameBridge — کیبورد و موس برای کالاف دیوتی موبایل 🎮⌨️🖱️

**GameBridge** turns a USB (OTG) or Bluetooth **keyboard & mouse** into a real touch controller, so you can play **Call of Duty Mobile** (or any touch game) with **WASD movement, mouse aim, and fully custom keymaps** — **without root**.

**گیم‌بریج** کیبورد و موس USB (از طریق OTG و هاب) یا بلوتوث را به کنترلر لمسی واقعی تبدیل می‌کند تا کالاف دیوتی موبایل (یا هر بازی لمسی) را با حرکت WASD، نشانه‌گیری با موس و کی‌مپ دلخواه بازی کنید — بدون روت.

> 📱 Tested target: Samsung Galaxy A33 5G · Android 14 (One UI 6) · minSdk 26 (Android 8.0+)

---

## ⚠️ Honest disclaimer / سلب مسئولیت صادقانه

This app is an **input mapper** (same category as Octopus / gamepad mappers on Play Store). It does **not** modify the game, read game memory, or provide aimbot/ESP. However, game publishers may consider third-party input tools a violation of their Terms of Service and **account bans have been reported for similar apps**. Use at your own risk.

این برنامه فقط یک **مپر ورودی** است (همان دسته‌ی Octopus و مپرهای گیم‌پد). بازی را تغییر نمی‌دهد و هیچ قابلیت تقلبی ندارد؛ اما ناشر بازی ممکن است ابزار ورودی شخص ثالث را خلاف قوانین بداند و **گزارش‌هایی از بن برای اپ‌های مشابه وجود دارد**. با آگاهی کامل استفاده کنید.

---

## ✨ Features / امکانات

| | |
|---|---|
| 🖱️ **Real mouse lock** | Android 14+ system-level mouse capture (`AccessibilityService.onMotionEvent` + `setMotionEventSources`) — infinite camera movement, no edge clipping, no root |
| 🕹️ **Analog WASD joystick** | Keys are translated into a *press & drag* stroke on the game's virtual stick (8 directions, diagonal support, Shift = slow walk) — exactly how a human thumb plays |
| 🎯 **Camera drag engine** | Continuous stroke with automatic re-anchoring (no spin), X/Y sensitivity, ADS sensitivity scale, invert Y |
| 🗺️ **In-game keymap editor** | Drag buttons / joystick / camera anchor **over the live game** and re-assign keys instantly |
| 🟠 **Floating menu** | Quick panel (F7): lock toggle, sensitivity slider, keymap editor, hide, exit |
| 🔒 **Custom lock key** | Default F8 toggles mouse lock/free (fully configurable) |
| 🧩 **Profiles** | Fraction-based coordinates (resolution independent), COD Mobile preset included, per-game active profile |
| ⌨️ **Global key interception** | `FLAG_REQUEST_FILTER_KEY_EVENTS` — mapped keys never leak into game chat |
| 🔍 **Live diagnostics** | In-app event log (keys, mouse deltas, gesture stats) for easy troubleshooting |
| 🌍 **English + فارسی** | Full localization, RTL friendly |

## 🔌 Hardware setup / اتصال سخت‌افزار

```
Phone ← OTG adapter ← USB hub ← mouse + keyboard
```

1. Connect the OTG adapter to the phone
2. Connect a powered/unpowered USB hub to the OTG
3. Plug the mouse & keyboard into the hub

## 🚀 Quick start / راه‌اندازی

1. Install the APK (from [Releases](../../releases/latest))
2. Open GameBridge → **enable the accessibility service** (the button in the app opens the right settings page)
3. On Samsung: **disable battery optimization** for GameBridge (Settings → Apps → GameBridge → Battery → Unrestricted)
4. Connect your OTG mouse & keyboard
5. In the app: pick **Call of Duty Mobile**, choose the profile → **LAUNCH**
6. In game: the floating button appears → mouse is auto-locked. **F8** = lock/free, **F7** = floating menu
7. Use the in-game editor ("ویرایش کی‌مپ" in the panel) to drag every button onto its matching HUD control

### Default keymap / کی‌مپ پیش‌فرض

| Key | Action |
|---|---|
| W A S D | Move (analog joystick) |
| Left click | Fire (hold) |
| Right click | ADS (hold, lower sens) |
| Space / Ctrl / Z | Jump / Crouch / Prone |
| R / G / V / F | Reload / Grenade / Melee / Skill |
| 1 / 2 | Weapon slots |
| Shift | Slow walk |
| Mouse wheel | — (assignable) |
| **F8 / F7** | **Mouse lock / Floating menu** |

All triggers, positions, and action types (tap / hold / toggle) are editable in the keymap editor.

## 🛠️ How it works / معماری فنی

- **Keyboard** → `AccessibilityService.onKeyEvent` with `FLAG_REQUEST_FILTER_KEY_EVENTS` (global, before the game)
- **Mouse (Android 14+)** → `AccessibilityServiceInfo.setMotionEventSources(SOURCE_MOUSE | SOURCE_MOUSE_RELATIVE)`; events are delivered to the service and **not sent to the rest of the system** = true mouse lock. Deltas read from `AXIS_RELATIVE_X/Y` with position-diff fallback
- **Mouse (Android 8–13)** → compatibility hover-tracking overlay (documented edge limitation) + `requestPointerCapture()` attempt
- **Touch injection** → `dispatchGesture` with **continuing strokes** (`StrokeDescription.continueStroke`) — one engine slot per finger (joystick / camera / each held action), a single serialized "master gesture" per tick contains updates for all active pointers, with keep-alives, cancellation recovery and a dispatch watchdog
- **Overlays** → `TYPE_ACCESSIBILITY_OVERLAY` windows (floating button, quick panel, editor) — no `SYSTEM_ALERT_WINDOW` permission
- **UI** → Kotlin 2.0, Jetpack Compose (Material 3), DataStore + kotlinx-serialization, classic Views for in-game overlays

## ⚙️ Build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

CI builds & publishes the APK automatically on every push to `main` (GitHub Actions).

> The repo intentionally contains a personal release keystore (`app/release.keystore`) so CI can produce update-compatible signed builds. For anything beyond personal use, replace it with your own and keep it private.

## 🧯 Troubleshooting

- **Service dies mid-game (Samsung)** → disable battery optimization for GameBridge
- **Mouse doesn't lock on Android < 14** → expected: use "Compatibility (hover)" mode; full capture needs Android 14+
- **Aim feels different per scope** → tune *ADS sensitivity scale*, and per-binding slow-scale is coming
- **Diagnostics** → Settings → *Live diagnostics* shows every key/mouse/gesture event

## 📄 License

MIT — see [LICENSE](LICENSE)
