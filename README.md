# Di2 Media Controls

Android app that connects to a Shimano Di2 EW-WU111 (or 12-speed built-in BLE) via Bluetooth Low Energy and translates D-Fly button presses into media controls for any media app.

## Architecture

```
Di2 EW-WU111 ──BLE──▶ Di2BleService ──▶ AudioManager ──▶ Spotify/YT Music/etc.
                        │
                        ├── Scans & connects to Di2 wireless unit
                        ├── Subscribes to GATT indications
                        ├── Decodes D-Fly button events (CH1–CH4)
                        └── Dispatches media key events
```

Media controls use `AudioManager.dispatchMediaKeyEvent()` — no special permissions beyond Bluetooth/Location needed.

## Setup

### Prerequisites
- Shimano Di2 with EW-WU111 wireless unit (or 12-speed with built-in BLE)
- E-Tube Project app: assign buttons to D-Fly channels (CH1–CH4)
- Android 8.0+
- Device must be bonded (paired) before indications work

### Permissions needed
- **Bluetooth/Location** — for BLE scanning and connection

### Button mapping

Each channel supports short press, long press, and double press. Mappings are configurable per-channel in the app. Available actions include next/previous track, play/pause, and volume up/down (with hold-to-ramp for long press).

## Build

```sh
nix develop                                    # Enter dev shell with Gradle, Kotlin, Android SDK
./gradlew assembleDebug                        # Debug build
./gradlew assembleRelease                      # Release build (R8 minified, debug-key signed)
./gradlew installDebug                         # Install debug on connected device
adb install app/build/outputs/apk/release/app-release.apk  # Install release
```

## Di2 BLE Protocol

- Service UUID: `000018ef-5348-494d-414e-4f5f424c4500`
- Button characteristic: `00002ac2` — uses **indicate** (not notify)
- Byte format: `[counter, ch1, ch2, ch3, ch4]`
  - `0xF0` = unmapped channel
  - `0x10` bit = short press, `0x20` = long press, `0x40` = double press
  - All bits cleared = released
