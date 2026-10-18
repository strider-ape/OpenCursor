# One-handed cursor app — clean-room behaviour spec

Functional specification for an open-source, one-handed "mouse cursor" app for Android,
written from a static behavioural study of Quick Cursor 2.2.1. It describes **what the
app does**, not how its code is written. Implement from this document only: do not copy
decompiled code, assets, icons, layouts, strings, or the "Quick Cursor" name.

Confidence tags: **[observed]** = seen in the shipped app; **[inferred]** = implied by names
or partial evidence; **[open]** = unknown, pick your own design.

---

## 1. Concept

The user drags a thumb from a screen edge ("trigger zone"). A small touch pad ("tracker")
appears under the thumb and a cursor appears elsewhere on screen. Moving the thumb on the
tracker moves the cursor across the whole screen; releasing / tapping performs a tap at the
cursor. Extra gestures on the tracker and edge-overshoot run configurable actions.

No root. Everything runs inside an `AccessibilityService`.

## 2. Platform building blocks

| Need | Android API |
|---|---|
| Engine host | `AccessibilityService` (declared with `BIND_ACCESSIBILITY_SERVICE`) |
| Overlays | `WindowManager.addView` with `TYPE_ACCESSIBILITY_OVERLAY` (2032), `PixelFormat.TRANSLUCENT`, gravity top-left |
| Taps / swipes | `dispatchGesture(GestureDescription)` |
| System buttons | `performGlobalAction` (back, home, recents, notifications, quick settings, power dialog, lock screen, screenshot, split screen) |
| Fallback click | `AccessibilityNodeInfo.performAction(ACTION_CLICK)` on the node under the cursor |
| Quick toggle | `TileService` (Quick Settings tile) |
| Flashlight | `CameraManager.setTorchMode` (from a transparent, no-history activity) |

Permissions used by the original [observed]: VIBRATE, CAMERA (torch), WRITE_SETTINGS
(brightness / rotation), ACCESS_/CHANGE_WIFI_STATE, ACCESS_NOTIFICATION_POLICY (DND).
It has **no INTERNET permission**. Recommended: keep it that way.

## 3. Windows [observed]

| Window | Size | Touch | Flags |
|---|---|---|---|
| Canvas (draws cursor, tracker, trail, edge bars, pie menu) | full screen, never moves | not touchable | NOT_FOCUSABLE, NOT_TOUCHABLE, LAYOUT_IN_SCREEN, LAYOUT_NO_LIMITS |
| Trigger zone (one per configured zone) | the zone rectangle | touchable | NOT_FOCUSABLE, NOT_TOUCH_MODAL, LAYOUT_IN_SCREEN, LAYOUT_NO_LIMITS |
| Tracker pad | square, configurable size | touchable | as trigger + WATCH_OUTSIDE_TOUCH |

Design notes:
- Cursor movement = update a position and `invalidate()` the canvas. Don't move windows per frame.
- When a trigger zone is grabbed, it shrinks to 1×1 px so it doesn't block taps. [observed]
- The tracker window follows the finger (x/y = raw touch − grab offset). [observed]
- The original disables the window move animation by reflection on a private flag. Optional;
  avoid hidden APIs if you can.

## 4. Service lifecycle and states [observed]

On connect: create the canvas, read settings, register receivers (screen on/off/unlock,
config changes, optional S Pen), then evaluate the state below. Show onboarding on first run.

The engine is in exactly one state:

| State | Condition |
|---|---|
| Disabled | user switched it off (or temporarily disabled) |
| Screen off / locked | screen not interactive, or keyguard locked and "enabled on lock screen" is off |
| Blacklisted app | foreground app is in the blacklist (or not in the whitelist) |
| Stylus | S Pen detached and "disable with S Pen" is on |
| Keyboard | keyboard visible and mode is "hide when keyboard open" |
| Active | otherwise: show trigger zones |

- Foreground app: from `TYPE_WINDOW_STATE_CHANGED` events (package + class name). Only
  treat it as an app switch when that component resolves to a real activity. [observed]
- Keyboard: on window changes, measure the IME height. Then either hide the triggers, or
  move them above the keyboard. [observed]
- On unbind/destroy, unregister receivers and remove overlays.

## 5. Trigger zones [observed]

- Configurable rectangles along screen edges (multiple zones; per-zone settings).
- **Down:** remember start point and time; shrink the zone window.
- **Activation:** after the finger moves past an activation distance of **20 dp**, the
  cursor session starts and the tracker appears. [observed]
- **Default layout ("simple" mode):** one zone on each side edge, 16 dp wide (max 100 dp).
  Settings on 0–11 style sliders: trigger position (portrait default 8, landscape 6),
  trigger length (5), cursor speed (portrait 4, landscape 3), cursor area size (3),
  tracker align (portrait 3, landscape 6), tracker distance (3). An "advanced" mode
  lets the user draw custom zones and rectangles instead. [observed]
- **Floating mode:** a 75 dp edge tracker resting on the screen edge at 30 % opacity,
  snapping to the edge between 15 and 50 dp. [observed]
- **Up:** if activated, finish with a release velocity, but only if the hold lasted
  > 300 ms. If not activated, it was a tap or long press on the trigger, which can run the
  "trigger tap" / "trigger long tap" actions.
- **Second finger** during a session triggers a separate event (bindable).

## 6. Tracker → cursor mapping [observed]

Linear rectangle mapping, no acceleration curve:

```
cursorX = (fingerX − src.left) / src.width  * dst.width  + dst.left
cursorY = (fingerY − src.top)  / src.height * dst.height + dst.top
```

- `src` = the area the thumb travels in (per trigger), `dst` = the screen area the cursor covers.
- Clamp to the screen for the drawn cursor, but keep the **unclamped** value too.
- **Edge actions:** the overshoot past a screen edge selects an edge-action bar. Each edge is
  split into N equal segments: `index = ceil(position / (edgeLength / N))`. Fire after a
  configurable threshold and delay. Highlight strength scales with distance to the edge.
- Optional cursor trail (several trail styles).

**Cursor speed (simple mode)** [observed]: `src` is `dst` shrunk by a speed factor, so the
cursor moves *factor ×* as far as the thumb. Levels 0–11 map to factors
1.5, 1.75, 2, 2.33, 2.66, 3, 3.33, 3.66, 4, 4.33, 4.66, 5. The portrait default (level 4)
is **2.66×** and the landscape default (level 3) is **2.33×**.
**Cursor area size**: level *n* covers (n + 1)/8 of the screen's long side (default 3 = half).
[observed formula; which axis counts as the long side is inferred]

Feel free to add an acceleration curve as your own improvement. The original has none.

## 7. Tracker gestures [observed]

| Gesture | Detection |
|---|---|
| Tap | down→up with movement < click-distance threshold |
| Long tap | held past a long-tap threshold |
| Double tap | second tap within a double-tap threshold |
| Release after move | up after movement: default action is tap at cursor |
| Touch outside | `ACTION_OUTSIDE`: optionally hide the cursor |

Each gesture is bound to an action from §9. A fixed 25 ms timer is also used internally. [observed]

## 8. Gesture injection [observed]

| Gesture | Stroke |
|---|---|
| Tap | one stroke at (x, y), start 0, **duration 5 ms**. Ignore points off-screen. |
| Long tap | one stroke; duration = setting, default **1.5 × system long-press timeout** |
| Multi-finger tap | one 5 ms stroke per finger, fingers spaced 5 dp apart |
| Swipe | from the cursor, straight line in one direction (default left); length 35 dp × 1/2/3 for short/medium/long (default medium); **duration 80 ms**; N fingers spread 5 dp × N apart, perpendicular to the swipe |
| Pinch in / out | two diagonal strokes around the cursor; outer offset 50/100/150 dp (short/medium/long), inner offset = outer ÷ 5 (pinch out) or ÷ 8 (pinch in); duration 300/500/1000 ms (default medium) |
| Drag ("real-time gesture") | start a stroke at the cursor with `willContinue=true`; on every cursor move, append a **1 ms** `continueStroke` segment; only one segment in flight at a time (wait for the completion callback); on release, send a final segment with `willContinue=false` (retry after 1 ms if a segment is still in flight). Scrolling is done the same way: drag with the cursor. |
| System-edge swipes | notifications: from the top edge at ¼ width, swipe down "long", 75 ms. Quick settings: same at ¾ width. App menu: from the side edge at ¼ height, inwards. Bottom two-finger: from 10 px above the bottom, up "medium", 2 fingers |
| Recorded gesture | replay recorded points, chaining `continueStroke(willContinue=true)` |
| Limited mode | instead of a gesture, find the accessibility node at (x, y) and `ACTION_CLICK` it |

**Dropped-tap workaround ("input dispatcher bug"):** on some devices injected taps get
lost. If detected (or after tracker release / long tap, configurable), dispatch an N-finger
tap of 5 ms strokes spaced 1 px apart vertically, retrying with more fingers up to 6.

Foldables / Surface Duo: offset gesture coordinates by the hinge/display-mask offset. [observed]

A double tap is two taps 50 ms apart. [observed]

## 9. Actions catalogue [observed: all 79 actions mapped]

Group into categories in the picker; some need extra permissions, so show requirements.

| Action | How it works |
|---|---|
| back / home / recents | `performGlobalAction` BACK (1) / HOME (2) / RECENTS (3) |
| previous app | RECENTS (3), then RECENTS again after 300 ms |
| expand notifications | NOTIFICATIONS (4); optional "toggle" variant uses DISMISS_NOTIFICATION_SHADE (15) first |
| expand quick settings | QUICK_SETTINGS (5), same toggle option |
| power menu | POWER_DIALOG (6) |
| split screen | TOGGLE_SPLIT_SCREEN (7) |
| lock screen | LOCK_SCREEN (8) |
| screenshot | TAKE_SCREENSHOT (9), API 28+ |
| app drawer | ACCESSIBILITY_ALL_APPS (14) |
| screenshot → clipboard | takes a screenshot, then copies, shares (`ACTION_SEND` + `EXTRA_STREAM`) or saves it through a FileProvider; optional crop step |
| copy | copies the selected text to the clipboard, or shares it (`ACTION_SEND` + `EXTRA_TEXT`) |
| cut | node `ACTION_CUT` (65536), selecting the text first if needed (`ACTION_SET_SELECTION` 131072) |
| paste | node `ACTION_PASTE` (32768) |
| Wi-Fi / internet / NFC / volume panel | `startActivity` with `android.settings.panel.action.WIFI` / `INTERNET_CONNECTIVITY` / `NFC` / `VOLUME` |
| toggle Wi-Fi | `WifiManager.setWifiEnabled` (only works on old Android; newer versions need the panel) |
| toggle flashlight | starts a transparent, no-history activity that calls `CameraManager.setTorchMode` |
| brightness bar / swipe | on-screen slider writing `Settings.System.SCREEN_BRIGHTNESS` (needs WRITE_SETTINGS) |
| toggle brightness | flips `SCREEN_BRIGHTNESS_MODE` (auto / manual) |
| toggle auto-rotate | `ACCELEROMETER_ROTATION` |
| rotate screen | `ACCELEROMETER_ROTATION` = 0 plus `USER_ROTATION` |
| volume bar / swipe | on-screen slider calling `AudioManager.setStreamVolume` (media stream by default) |
| sound profile | `AudioManager.setRingerMode` |
| Do Not Disturb | `NotificationManager.setInterruptionFilter` (needs notification policy access) |
| media play-pause / next / prev / play / pause | `AudioManager.dispatchMediaKeyEvent` down+up for keycodes 85 / 87 / 88 / 126 / 127 |
| media volume up / down / mute | `AudioManager.adjustStreamVolume` |
| launch app | `getLaunchIntentForPackage` (MAIN + LAUNCHER); optional windowed mode |
| launch assistant / camera / search / shortcut | `VOICE_COMMAND` / `STILL_IMAGE_CAMERA` intents / search intent / a stored shortcut intent |
| Tasker / MacroDroid / Automate | `startActivity` on the shortcut the user picked |
| tap, long tap, double tap, multi tap, swipe, pinch, drag, system-edge swipes | gesture injection (§8) |
| trigger tap / long tap, grab / hide cursor, temporarily disable, auto-tap toggle / cancel, tracker-actions open / once / always visible, gesture recorder start / stop, input-dispatcher fix, open quick settings (in-app panel) | internal engine actions, no extra Android API |
| shortcut: trigger / hide / toggle cursor, start / stop / toggle app | entries for the launcher-shortcut activity |
| nothing / no permissions needed | no-op |

**Pie menu ("tracker actions"):** a radial menu around the tracker with configurable slices,
icons and colours.

## 10. Settings [observed]

Stored in default SharedPreferences, about 99 keys. Group by screen:

- **Triggers:** zones, sizes, positions, activation distance, advanced per-trigger mapping.
- **Tap behaviour:** gesture→action bindings, thresholds (click distance, long tap,
  double tap), long-tap duration, limited mode, auto-tap mode.
- **Cursor:** size, style (built-in or custom image with crop), colour, trail type.
- **Tracker:** size, look, hide on outside touch.
- **Edge actions / tracker actions:** bars per edge, segment count, threshold, delay, bindings.
- **Vibrations & visuals:** haptics per event, animations.
- **Blacklist:** whitelist/blacklist mode and app list; enabled on lock screen.
- **Workarounds:** dropped-tap fix toggles; keyboard behaviour; S Pen behaviour.
- **Backup & restore:** export all prefs as JSON via the system document picker. On import,
  check the version and ask the user to confirm. Exclude debug logs. [observed]
- **Debug:** optional in-app log buffer. No remote logging. [observed]

**Original defaults** [observed], useful as starting values:

| Area | Defaults |
|---|---|
| Cursor | size 40 dp, tip 6 dp, white 4 dp outline, translucent fill, not mirrored |
| Tracker | 100 dp, inside `#80FFFFFF`, outside `#80C0C0C0` |
| Taps | click-distance threshold 6 dp; long tap 400 ms; double tap 200 ms; second-tap zone 30 dp; long-tap gesture length = 1.5 × system long-press timeout |
| Tracker bindings | long tap → long tap; double tap → nothing; second tap → open quick settings |
| Hiding | hide on outside touch, hide on fling, and hide after 2000 ms idle: all on |
| Feedback | vibration 10 ms (off for clicks, on for cursor actions); ripple 80 dp, 500 ms; trail "high", 150 ms lifespan |
| Edge actions | threshold 30 dp, delay 500 ms, "instant" trigger mode, visual + vibration feedback on, 75 % opacity |
| Tracker actions (pie) | inner 70 dp, outer 160 dp, icons 50, sweep offset 67°, trigger on release, delay 500 ms |
| Action sliders | brightness/volume bar: absolute mode, horizontal, 1000 ms smoothing; volume steps 1 |
| Keyboard | triggers: do nothing; tracker: do nothing; "thinner" 50 % |
| Modes | auto-tap off; limited mode off; app filter off; enabled on lock screen off; disabled while S Pen detached on |
| Workarounds | all three dropped-tap fixes off |

## 11. Out of scope / don't replicate

- Pro / billing (Play Billing, "lifetime" / "subscription" states). An open-source build
  doesn't need it.
- Everything the TRUMods mod added: signature spoofer, anti-tamper, the Telegram
  dialog, patched strings.

## 12. Suggested architecture for the open-source rebuild

Kotlin, minSdk 26 (gestures need 24+; 26+ simplifies overlays), Jetpack Compose for settings.

```
service/   CursorService (AccessibilityService), StateMachine (§4)
overlay/   OverlayManager, CanvasView, TriggerZoneView, TrackerView
input/     TriggerTouchHandler, TrackerGestureDetector, CursorMapper (§6), EdgeActionResolver
inject/    GestureInjector (§8), DroppedTapWorkaround
actions/   Action (sealed interface + registry), one file per category (§9)
settings/  DataStore-backed settings, JSON backup/restore
ui/        Compose settings screens, onboarding, Quick Settings tile
```

Build order: (1) service + canvas + one trigger + linear mapping + 5 ms tap → usable MVP;
(2) tracker gestures + global actions; (3) edge actions + pie menu; (4) blacklist, keyboard,
lock-screen states; (5) remaining actions, backup, workarounds.
