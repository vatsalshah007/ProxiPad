# ProxiPad 🖱️

> Turn your Android phone into a wireless Bluetooth trackpad for your tablet — no tablet app required.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/min%20SDK-28%20(Android%209)-brightgreen.svg)](https://developer.android.com/about/versions/pie/android-9.0)
[![Status](https://img.shields.io/badge/status-v1.0.0-brightgreen.svg)](https://github.com/vatsalshah007/ProxiPad/releases)

---

## What is ProxiPad?

ProxiPad is a lightweight, open-source Android app that registers your phone as a **Bluetooth HID (Human Interface Device) mouse**. Once paired, your phone's screen becomes a fully functional trackpad — your tablet treats it exactly like a physical Bluetooth mouse, with no app installation required on the tablet side.

---

## How It Works

```
┌─────────────────────┐         Bluetooth HID            ┌──────────────────────┐
│   Phone (ProxiPad)  │  ─────────────────────────────▶ │  Tablet (no app)     │
│                     │                                  │                      │
│  Touch → Gesture    │       Mouse reports (4 bytes)    │  OS moves cursor     │
│  Engine → HID layer │                                  │  natively            │
└─────────────────────┘                                  └──────────────────────┘
```

The phone uses Android's `BluetoothHidDevice` API (available since Android 9) to emulate a standard Bluetooth mouse. The tablet receives mouse input at the OS level — no special software, drivers, or configuration needed.

---

## Features (v1)

| Gesture | Action |
|---|---|
| 1-finger drag | Move cursor |
| 1-finger tap | Left click |
| 2-finger tap | Right click |
| 2-finger vertical drag | Scroll |

---

## Requirements

- **Phone:** Android 9 (API 28) or higher
- **Tablet:** Any Android tablet (no app required) — or any device that accepts Bluetooth HID mice (Windows, macOS, Linux, iPadOS)
- **Bluetooth:** Both devices must have Bluetooth enabled

---

## Installation

### From Release (Recommended)
1. Download the latest `.apk` from the [Releases](https://github.com/vatsalshah007/ProxiPad/releases) page
2. Enable "Install from unknown sources" on your phone if prompted
3. Install and open ProxiPad

### Build from Source
```bash
# Clone the repo
git clone https://github.com/vatsalshah007/ProxiPad.git
cd ProxiPad

# Build debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Usage

1. **Pair your phone and tablet** via Bluetooth settings (standard pairing — do this once)
2. **Open ProxiPad** on your phone
3. **Tap your tablet** from the device list
4. **Use the touchscreen** as a trackpad — the cursor appears on your tablet immediately

To disconnect, tap the disconnect button or simply close the app.

---

## Permissions

ProxiPad requests only the permissions it needs:

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` | To connect to your paired tablet |
| `BLUETOOTH_ADVERTISE` | To register the phone as a HID device |
| `FOREGROUND_SERVICE` | To keep the connection alive when the app is backgrounded |

No internet access. No location. No camera. No data leaves your device.

---

## Architecture

```
app/
├── bluetooth/
│   ├── HidProfileManager.kt      # BT HID registration, connect/disconnect
│   ├── HidReportSender.kt        # Sends HID reports on a dedicated HandlerThread
│   └── MouseDescriptor.kt        # Standard HID mouse byte descriptor
├── gesture/
│   ├── GestureEngine.kt          # MotionEvent → GestureEvent (O(1), no allocations)
│   └── GestureEvent.kt           # Sealed class: Move, Tap, RightTap, Scroll
├── ui/
│   ├── TouchSurface.kt           # Fullscreen touch capture view
│   ├── MainActivity.kt
│   └── DevicePickerDialog.kt     # Lists paired Bluetooth devices
└── service/
    └── HidForegroundService.kt   # Keeps BT connection alive in background
```

**Design principles:**
- Zero unnecessary dependencies (no Retrofit, Hilt, Room, or image libraries)
- Pre-allocated HID report buffer — no GC pressure on the 60–120Hz touch path
- Bluetooth Classic HID for ~15ms latency (not BLE which spikes to 100ms+)
- APK target: under 2MB

---

## Roadmap

### v1 (Current)
- ✅ Project setup & architecture
- ✅ Bluetooth HID registration
- ✅ Basic cursor movement
- ✅ Tap (left click)
- ✅ Two-finger tap (right click)
- ✅ Two-finger scroll
- ✅ Device picker UI
- ✅ Background foreground service

### v2 (Planned)
- [ ] Adjustable pointer sensitivity
- [ ] Palm rejection
- [ ] Three-finger gestures
- [ ] Auto-reconnect on Bluetooth drop
- [ ] Screen dim while in trackpad mode

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

For bugs, use the [Issues](https://github.com/vatsalshah007/ProxiPad/issues) tab. Please include your Android version and device model.

---

## License

```
Copyright 2026 Vatsal

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

<p align="center">Built with ❤️ for the Android open source community</p>
