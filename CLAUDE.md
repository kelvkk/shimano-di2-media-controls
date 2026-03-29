# Di2 Media Controls

Android app (Kotlin, Jetpack Compose) that connects to a Shimano Di2 EW-WU111 via BLE and translates D-Fly button presses into media controls.

## Build & Run

```sh
nix develop                                    # Enter dev shell with Gradle, Kotlin, Android SDK
./gradlew assembleDebug                        # Debug build
./gradlew assembleRelease                      # Release build (R8 minified, debug-key signed)
./gradlew installDebug                         # Install debug on connected device
adb install app/build/outputs/apk/release/app-release.apk  # Install release
```

## Project Structure

```
app/src/main/kotlin/com/di2media/
  MainActivity.kt                 # Single activity, permission handling, screen navigation
  service/
    Di2BleService.kt              # Foreground service: BLE scan, connect, decode, dispatch
    MediaNotificationListener.kt  # Stub for future MediaSession access
  mapping/
    ButtonAction.kt               # InstantAction/HoldAction enums
    ButtonMappingConfig.kt        # Channel+press → action config, SharedPreferences persistence
    ActionDispatcher.kt           # Executes actions via AudioManager.dispatchMediaKeyEvent()
  ui/
    DeviceSetupScreen.kt          # BLE scan, device list, connect
    ButtonMonitorScreen.kt        # CH1-4 press state indicators with mapping labels
    ChannelConfigScreen.kt        # Per-channel action configuration (radio buttons)
```

## Di2 BLE Protocol

- Service UUID: `000018ef-5348-494d-414e-4f5f424c4500`
- Button characteristic: `00002ac2` — uses **indicate** (not notify)
- Byte format: `[counter, ch1, ch2, ch3, ch4]`
  - `0xF0` = unmapped channel
  - `0x10` bit = short press, `0x20` = long press, `0x40` = double press
  - All bits cleared = released
- Buttons must be assigned to D-Fly channels via Shimano E-Tube Project app
- Device must be bonded before indications work

## Key Design Decisions

- Media controls use `AudioManager.dispatchMediaKeyEvent()` — no special permissions needed (no NotificationListener)
- Long press is the only press type with a release event, enabling hold actions (volume ramp)
- Mappings stored in SharedPreferences as JSON via `ButtonMappingConfig`
- Release APK is signed with the debug key (personal sideload use)

## Conventions

- Conventional Commits for commit messages
- Concise oneliner commit messages without description body
- Nix flake for dev environment (Gradle + Android SDK)
