# Contributing to ProxiPad

Thanks for your interest in contributing! Please read this before submitting a PR.

---

## How It Works — Architecture

```
┌─────────────────────┐         Bluetooth HID            ┌──────────────────────┐
│   Phone (ProxiPad)  │  ─────────────────────────────▶ │  Tablet (no app)     │
│                     │                                  │                      │
│  Touch → Gesture    │       Mouse reports (4 bytes)    │  OS moves cursor     │
│  Engine → HID layer │                                  │  natively            │
└─────────────────────┘                                  └──────────────────────┘
```

### Module Breakdown

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

### Design Principles
- Zero unnecessary dependencies (no Retrofit, Hilt, Room, or image libraries)
- Pre-allocated HID report buffer — no GC pressure on the 60–120Hz touch path
- Bluetooth Classic HID for ~15ms latency (not BLE which can spike to 100ms+)
- APK target: under 2MB
- O(1) gesture processing on the touch callback hot path — no loops or allocations

### Threading Model
```
Main Thread
  └── TouchSurface (captures MotionEvent)
        └── GestureEngine.process(event)   [sync, O(1)]
              └── posts to → HidHandlerThread
HidHandlerThread
  └── HidReportSender.send(report)
        └── BluetoothHidDevice.sendReport()
```

---

## Dev Environment Setup

### Requirements
- Android Studio (latest stable)
- Android SDK Platform 28 and 35
- Android SDK Command-line Tools + Platform-tools
- JDK 17 (bundled with Android Studio)

### Environment Variables (Windows)
```
ANDROID_HOME = C:\Users\<YourName>\AppData\Local\Android\Sdk
JAVA_HOME    = C:\Program Files\Android\Android Studio\jbr
```

Add to User PATH:
```
%ANDROID_HOME%\platform-tools
%ANDROID_HOME%\tools
%JAVA_HOME%\bin
```

### Build & Install
```bash
# Debug build
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew test
```

> **Note:** Bluetooth HID cannot be tested on an emulator.
> You need two physical devices — a phone and a tablet — for any BT testing.

---

## Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Production — stable releases only |
| `dev` | Active development — all PRs target this |
| `feature/*` | Individual features branched off `dev` |

Never commit directly to `main`. All changes go through a PR from `dev`.

---

## Submitting a PR

1. Fork the repo
2. Branch off `dev`: `git checkout -b feature/your-feature`
3. Make your changes
4. Run tests: `./gradlew test`
5. Commit: `git commit -m 'feat: describe your change'`
6. Push: `git push origin feature/your-feature`
7. Open a PR targeting `dev`, not `main`

### PR Checklist
- [ ] All unit tests pass (`./gradlew test`)
- [ ] No new dependencies added without discussion
- [ ] No object allocations inside `ACTION_MOVE` handler
- [ ] Code follows existing module structure
- [ ] Tested on a physical device

---

## Reporting Bugs

Use the [Issues](https://github.com/vatsalshah007/ProxiPad/issues) tab. Please include:
- Android version and device model
- Steps to reproduce
- Logcat output (filter by tag `HidProfileManager` or `GestureEngine`)

---

## What's in Scope

### v2 Planned Features (good first contributions)
- Adjustable pointer sensitivity
- Palm rejection
- Three-finger gestures
- Auto-reconnect on Bluetooth drop
- Screen dim while in trackpad mode

If you want to work on something not listed, open an issue first to discuss it.
