# OpenCursor

A free, open-source one-handed cursor for Android. Swipe in from the edge of the screen and a
small round pad appears under your thumb, with a cursor that reaches the top of the screen.
Move the pad to aim, tap it to tap where the cursor points. No root needed.

OpenCursor is an independent, clean-room implementation inspired by the behaviour of
the Quick Cursor app. It shares no code or assets with it and is not affiliated with it.

## Features

- **Edge triggers** on the left and right edges (or along the bottom edge in landscape),
  with adjustable position, length and thickness
- **Tracker pad**: tap to tap at the cursor, hold to long-press, flick or touch elsewhere to hide
- **Cursor movement** with 12 speed levels and an adjustable reach, laid out the same way as Quick Cursor
- **Action pie**: hold the pad, slide toward one of 8 slices, let go to run it at the cursor
- **Edge actions**: push the cursor past a screen edge to run an action, including a
  **volume bar** that turns the top edge into a media-volume slider
- **Actions**: back, home, recents, previous app, notifications, quick settings, power menu,
  lock screen, screenshot, split screen, app drawer, and gestures at the cursor (tap,
  long tap, double tap, swipes, pinches)
- **Import a Quick Cursor backup** (.json) to bring over your existing setup
- Works with gesture navigation, pauses while the phone is locked
- No internet permission, no analytics, reads no screen content

## Install

1. Download the latest `OpenCursor-vX.Y.Z.apk` from
   [Releases](https://github.com/strider-ape/OpenCursor/releases) and install it.
2. Open OpenCursor and tap **Open accessibility settings**, then turn on **OpenCursor**.
3. On Android 13 and newer, if the switch is greyed out or says "Restricted setting":
   open **Settings → Apps → OpenCursor → ⋮ → Allow restricted settings**, then try again.

## Use

1. Swipe in from a side edge, a little below the middle of the screen.
2. Keep your thumb on the round pad and move it to aim the cursor.
3. Lift, then tap the pad to tap where the cursor points. Hold the pad to long-press,
   or to open the action pie if you turned that on.
4. Touch anywhere else, flick the pad, or wait two seconds to hide it.

Every option is explained on its own settings page inside the app.

## Build from source

Requirements: JDK 17 or newer and the Android SDK (compile SDK 37).

```bash
./gradlew assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

Release builds are signed only if a `keystore.properties` file exists in the project root
(it is git-ignored and must never be committed):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleRelease      # signed APK: app/build/outputs/apk/release/
```

## How it's built

[SPEC.md](SPEC.md) is the behaviour specification the app is implemented from: windows,
trigger layout formulas, cursor mapping, gesture timings, actions and defaults.

The code lives in `app/src/main/java/com/openquickcursor/`:

| Package | What it does |
|---|---|
| `service` | The accessibility service that hosts everything |
| `engine` | Runs a cursor session: triggers, pad, cursor, edge actions, pie, volume bar |
| `overlay` | The overlay windows and their drawing (cursor canvas, triggers, pad, pie) |
| `input` | Trigger layout, finger-to-cursor mapping, edge detection |
| `inject` | Taps, swipes and pinches performed through gesture injection |
| `actions` | The bindable actions |
| `settings` | Saved settings and the Quick Cursor backup importer |
| `ui` | The settings screens (Jetpack Compose) |

## License

OpenCursor is free software, licensed under the [GNU General Public License v3.0](LICENSE).
You can use, study, share and modify it; any distributed modified version must also be
released under the GPL-3.0 with its source code.
