# Di2 Media Control

Android app that translates Shimano Di2 D-Fly button presses into media controls (next/prev/play/pause) for any media app.

## Architecture

```
Di2 EW-WU111 ──BLE──▶ Di2BleService ──▶ MediaSessionManager ──▶ Spotify/YT Music/etc.
                        │
                        ├── Scans & connects to Di2 wireless unit
                        ├── Subscribes to GATT notifications
                        ├── Decodes D-Fly button events
                        └── Dispatches to active MediaController
```

## Setup

### Prerequisites
- Shimano Di2 with EW-WU111 wireless unit (or 12-speed with built-in BLE)
- E-Tube Project: assign buttons to **D-Fly Ch.1** and **D-Fly Ch.2**
- Android 8.0+

### Permissions needed
1. **Bluetooth/Location** — for BLE scanning
2. **Notification Access** — required for `MediaSessionManager.getActiveSessions()`
   - App will prompt you to enable this in Android Settings

### Default button mapping
| Button          | Action         |
|-----------------|----------------|
| CH1 short press | Next track     |
| CH1 long press  | Play/Pause     |
| CH2 short press | Previous track |
| CH2 long press  | (unmapped)     |

## ⚠️ TODO: BLE Protocol

The UUIDs in `Di2BleService.kt` are **placeholders**. You need to discover the real ones:

### How to find your Di2's BLE UUIDs

1. **nRF Connect app** (free, Nordic Semiconductor) — scan, connect to your
   EW-WU111, browse services/characteristics. Note all UUIDs.

2. Put your Di2 in connection mode (press junction box button until LEDs flash).

3. Connect with nRF Connect, enable notifications on characteristics that
   have the NOTIFY property, then press Di2 buttons and observe the raw bytes.

4. Update these constants in `Di2BleService.kt`:
   - `DI2_SERVICE_UUID`
   - `DI2_BUTTON_CHAR_UUID`

5. Update `handleButtonPress()` byte decoding based on observed patterns.

### Known community resources
- https://bettershifting.com — Di2 protocol info
- Search GitHub for "shimano di2 ble" for protocol reverse-engineering
- The Cadence app (getcadence.app) successfully reads Di2 buttons — their
  approach confirms this is doable

## Build

Standard Android Studio project. Requires:
- Kotlin 1.9+
- Jetpack Compose (Material 3)
- Min SDK 26 (Android 8.0)
- Target SDK 34
