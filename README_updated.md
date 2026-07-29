> **⚠️ 本项目尚未开发完全，仍在积极开发中。欢迎共同参与开发，提交 Issue 和 Pull Request！**
>
> **⚠️ This project is under active development and not yet feature-complete. Contributions, issues, and pull requests are welcome!**
>
---

# Pod Control - AirPods Control for Android

A native Android app that brings AirPods control features to Android devices. Monitor battery levels, manage listening modes (ANC, Transparency, Adaptive Audio), configure gestures, and more - all from your Android phone.

## Features

- **Battery Monitoring** - View battery levels for left, right, and case (via BLE advertisements)
- **Listening Mode Control** - Switch between ANC, Transparency, Adaptive Audio, and Off
- **Spatial Audio** - Toggle Spatial Audio with head tracking
- **In-Ear Detection** - Monitor and configure in-ear detection
- **Find My** - Play sounds on your AirPods to locate them
- **Ear Tip Fit Test** - Run the ear tip fit test for optimal seal
- **Device Info** - View model, firmware version, serial number, and MAC address
- **Shizuku Integration** - Bypass ROM-level Bluetooth restrictions on Huawei/Xiaomi/OPPO

## Screenshots

*(Add screenshots here - see [docs/screenshots](docs/screenshots/))*

## System Requirements

- Android 9.0 (API 28) or later
- Bluetooth 4.0+ (BLE support required)
- Location services enabled (required for BLE scanning on Android < 12)

## Supported Devices

| Model | Battery | ANC | Transparency | Spatial Audio | Adaptive |
|-------|---------|-----|--------------|---------------|----------|
| AirPods Pro (1st gen) | Yes | Limited* | Limited* | Limited* | - |
| AirPods Pro (2nd gen) | Yes | Limited* | Limited* | Limited* | Limited* |
| AirPods (3rd gen) | Yes | - | - | Limited* | - |
| AirPods 4 / 4 (ANC) | Yes | Limited* | Limited* | Limited* | Limited* |
| AirPods Max | Yes | Limited* | Limited* | Limited* | - |
| AirPods (1st/2nd gen) | Yes | - | - | - | - |

> \* **Advanced controls (ANC, Transparency, Spatial Audio, Adaptive Audio) require AACP protocol access via BLE GATT.** On some devices (especially Huawei/Xiaomi/OPPO), the system blocks GATT connections to non-system apps. Use Shizuku to bypass this limitation.

## ROM Compatibility

### Standard Android (Pixel, Motorola, Nothing, etc.)
Full functionality with standard Bluetooth permissions. Just grant Bluetooth and Location permissions.

### Huawei MagicUI / EMUI
Bluetooth GATT operations are blocked by the system even with `BLUETOOTH_CONNECT` permission granted. **Install [Shizuku](https://shizuku.rikka.app/) to enable advanced controls.** Basic features (battery, device info) work without Shizuku via AudioManager fallback.

### Xiaomi MIUI / HyperOS
Similar restrictions to Huawei. May require enabling "Background autostart" in addition to Shizuku.

### OPPO ColorOS / vivo OriginOS
GATT restrictions may apply. Use Shizuku if advanced controls are unavailable.

### Samsung One UI
Generally works well. Some models may require Location to be enabled for BLE scanning.

## Setup Instructions

### 1. Install the App
Download the APK from [Releases](https://github.com/yourusername/pod-control/releases) and install it on your device.

### 2. Grant Permissions
When you first open the app, grant:
- **Bluetooth** - Required for device communication
- **Location** - Required for BLE scanning (Android < 12)
- **Notifications** - For foreground service status (Android 13+)
- **Display over other apps** - For the connection popup (optional)

### 3. Connect Your AirPods
1. Pair your AirPods with your Android phone via system Bluetooth settings
2. Open Pod Control - it should automatically detect your AirPods
3. If detection fails, tap "Retry Connection"

### 4. Enable Advanced Controls (Optional)
If you see "Compatibility Mode" and want ANC/Transparency/Spatial Audio:

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) from Google Play
2. Start Shizuku (via wireless debugging or ADB)
3. Open Pod Control and tap "Authorize Shizuku"
4. Grant permission in the Shizuku prompt
5. The app will attempt to unlock advanced controls via Shizuku

**Note:** On some ROMs (especially Huawei MagicUI), even Shizuku may not be able to bypass all Bluetooth restrictions due to SELinux policies. This is a system-level limitation, not an app bug.

## Building from Source

### Prerequisites
- Android Studio Hedgehog (2023.1) or later
- JDK 17+
- Android SDK 33

### Build
```bash
# Clone the repository
git clone https://github.com/yourusername/pod-control.git
cd pod-control

# Set JAVA_HOME (Windows example)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

# Build debug APK
gradlew :app:assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Project Structure
```
airpods-control/
  app/                  # Main application module
  core-aacp/            # AACP protocol implementation
  core-bluetooth/       # Bluetooth GATT manager, scanner, Shizuku integration
  core-data/            # Data models, preferences
  core-model3d/          # 3D model rendering
  core-service/         # Foreground service for persistent connection
  core-ui/              # Theme and design system
  feature-home/         # Main control screen (Jetpack Compose)
  feature-popup/        # Connection popup overlay
  feature-settings/     # Settings screen
```

### Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Bluetooth:** Android BLE GATT + Shizuku
- **Build:** Gradle 8.5 + AGP 8.2.2

## Known Issues

### Huawei MagicUI / EMUI
- **GATT blocked:** `connectGatt()` throws `SecurityException` even with all permissions granted. This is a system-level restriction.
- **Workaround:** Shizuku may bypass some restrictions on EMUI 12+, but SELinux policies on older versions may still block GATT.
- **Fallback:** The app falls back to AudioManager-based detection and BLE advertisement parsing for battery data.

### Battery Data Delayed
Battery information comes from BLE advertisements, which may take 10-30 seconds to appear after connection. Keep the app open to receive the first battery update.

### Permission Issues on Android 12+
- `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are runtime permissions on Android 12+. The app requests them on first launch.
- Location must be enabled (not just granted) for BLE scanning to work on Android < 12.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Disclaimer

This is an unofficial, community-developed app. It is not affiliated with or endorsed by Apple Inc. AirPods is a trademark of Apple Inc.

---

**Made with ❤️ for the Android community**