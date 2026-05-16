---
trigger: always_on
---

# Project: BT Trackpad — Android App

## What This App Does

A standalone Android app installed only on a phone that turns the phone
into a Bluetooth HID trackpad for a tablet. The tablet requires no app —
it treats the phone as a standard Bluetooth mouse via the OS.

## Tech Stack

- Language: Kotlin
- Min SDK: 28 (Android 9) — required for BluetoothHidDevice API
- Target SDK: 35
- Build system: Gradle with Kotlin DSL
- Dependencies: ONLY Kotlin stdlib + AndroidX Core. No Retrofit,
  no Hilt, no Room, no image libraries, no analytics. Zero unnecessary
  dependencies.

## Architecture

Flat and simple. No DI framework. Direct references between components.

app/
├── bluetooth/
│ ├── HidProfileManager.kt # registerApp, connect, disconnect
│ ├── HidReportSender.kt # sendReport on HandlerThread
│ └── MouseDescriptor.kt # HID byte descriptor + constants
├── gesture/
│ ├── GestureEngine.kt # MotionEvent → GestureEvent
│ └── GestureEvent.kt # sealed class: Move, Tap, RightTap, Scroll
├── ui/
│ ├── TouchSurface.kt # fullscreen View, passes events to GestureEngine
│ ├── MainActivity.kt
│ └── DevicePickerDialog.kt # lists paired BT devices, user selects tablet
└── service/
└── HidForegroundService.kt # keeps BT connection alive when backgrounded

## Core Design Constraints (never violate these)

1. Pre-allocate the 4-byte HID report buffer ONCE at init. Never allocate
   inside ACTION_MOVE handler — it fires at 60–120Hz and causes GC pressure.
2. Gesture engine must be O(1) on the touch callback hot path. No loops,
   no collection iteration on every touch event.
3. HID sendReport() must always be called on a dedicated HandlerThread,
   never the main thread.
4. No object allocation inside ACTION_MOVE. Use pre-allocated state objects.
5. R8 minification and resource shrinking must be enabled in release builds.
6. No wake locks in the foreground service. BT stack manages its own power.

## HID Report Format (4 bytes)

Byte 0: Button state [bit0=Left click, bit1=Right click, bit2=Middle]
Byte 1: X delta [-127 to 127]
Byte 2: Y delta [-127 to 127]
Byte 3: Scroll [-127 to 127]

## Gesture Spec (v1 only)

- 1 finger drag → cursor move
- 1 finger tap → left click (report 0x01, then 0x00, ~10ms gap)
- 2 finger tap → right click (report 0x02, then 0x00, ~10ms gap)
- 2 finger vertical → scroll (byte 3)
  Tap detection: pointer UP within 150ms of DOWN + total movement < 10px.

## Threading Model

Main Thread
└── TouchSurface (captures MotionEvent)
└── GestureEngine.process(event) [sync, O(1)]
└── posts to →
HidHandlerThread
└── HidReportSender.send(report)
└── BluetoothHidDevice.sendReport()

## Required Android Permissions

- BLUETOOTH_CONNECT (API 31+)
- BLUETOOTH_ADVERTISE (API 31+)
- BLUETOOTH (API <31 fallback)
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_CONNECTED_DEVICE

## How We Work — IMPORTANT

Build one small unit at a time. Do not proceed to the next unit until
the current one is verified working. The build order is:

PHASE 1 — Project Setup
1a. Create the Android project with correct SDK versions and empty
MainActivity
1b. Configure build.gradle: dependencies, R8, permissions in manifest

PHASE 2 — Bluetooth HID Foundation
2a. MouseDescriptor.kt — just the HID byte array and constants,
nothing else. No logic.
2b. HidProfileManager.kt — registerApp and profile callback only.
No connection logic yet.
2c. Add connect/disconnect to HidProfileManager. Test: can the phone
appear as a BT HID device to the tablet.

PHASE 3 — HID Report Sending
3a. HidReportSender.kt — HandlerThread setup + sendReport wrapper.
Test with a hardcoded dummy report.
3b. Wire HidReportSender into HidProfileManager. Test: send a
static report when connected.

PHASE 4 — Gesture Engine
4a. GestureEvent.kt — sealed class only, no logic
4b. GestureEngine.kt — single finger move only. Test with log output.
4c. Add tap detection to GestureEngine. Test.
4d. Add two-finger tap (right click). Test.
4e. Add two-finger scroll. Test.

PHASE 5 — UI Layer
Use Jetpack Compose for the UI, not XML layouts.

5a. UI Layout (MainActivity + TouchSurface)

ORIENTATION LOGIC:

- App always launches in portrait
- Force rotate to landscape ONLY after BT connection is confirmed
- On mid-session dropout: stay landscape, show disconnection toast,
  start a 5 min timeout timer (SESSION_TIMEOUT_MS = 5 _ 60 _ 1000)
- If still disconnected after timeout: snap back to portrait, show
  "Session ended. Tap to connect." prompt
- If user backgrounds app during active session and returns:
  - BT still connected → resume landscape
  - BT disconnected → portrait with connect prompt
- Orientation is controlled programmatically via
  requestedOrientation, not the manifest

PORTRAIT LAYOUT:

- Status bar at top: red dot + "Not Connected" (tappable → opens picker)
- Touchpad surface below (inactive/dimmed state, not interactable)

LANDSCAPE LAYOUT:

- Status bar on right edge (appears as top bar in landscape):
  green/red dot + device name — tappable always
  - Connected: tap → "Disconnect?" confirmation
  - Disconnected: tap → opens device picker bottom sheet
- Touchpad surface fills remaining screen space, fully active

VISUAL THEME:

- Dark background throughout
- Touchpad: dark rounded rectangle, subtle border/inner shadow
- Status text: light muted color, small and unobtrusive
- No app title anywhere
- No buttons (v2)

TECHNICAL:

- Single Activity, no fragments
- TouchSurface is a custom View overriding onTouchEvent
- Status bar updates via callback from HidProfileManager
- Flat view hierarchy, ConstraintLayout or Compose
- Use Jetpack Compose, not XML
- SESSION_TIMEOUT_MS defined as a top-level constant
  5b. TouchSurface.kt — fullscreen View wired to GestureEngine
  5c. DevicePickerDialog

BEHAVIOR:

- Triggered by tapping the status bar in both portrait
  and landscape modes
- In landscape: sheet appears from the right edge
- In portrait: sheet appears from bottom

VISUAL THEME (must match main screen exactly):

- Same dark background tone as touchpad surface
- Rounded top corners (bottom sheet) or left corners (landscape)
- Drag handle at the opening edge
- Title: "Select Device" — light muted text, not bold
- Device list items: device name in white/light text,
  MAC address below in smaller muted text
- Selection highlight: subtle lighter dark shade
- Empty state: "No paired devices found. Pair your tablet
  in Bluetooth settings." in muted text
- No default Material dialog styling

TECHNICAL:

- ModalBottomSheet (Compose Material3)
- Lists only already-paired BT devices
- Returns selected BluetoothDevice to MainActivity
- On selection: initiates connection, closes sheet,
  starts connection flow
- Jetpack Compose, not XML
  5d. MainActivity.kt — ties everything together

PHASE 6 — Background Service
6a. HidForegroundService.kt — keeps connection alive when backgrounded
6b. Wire service into MainActivity lifecycle

PHASE 7 — Polish
7a. Handle BT permission request flow (Android 12+)
7b. Handle disconnection and reconnection gracefully
7c. Verify R8 release build produces correct output

## When Asking for Each Unit

I will say: "Build [unit ID] — [unit name]"
Example: "Build 2a — MouseDescriptor.kt"

When you build a unit:

1. Write the code for that unit only
2. Tell me exactly how to test it
3. Wait for my confirmation before suggesting we move to the next unit
4. If the test fails, debug within the same unit before moving on

## What NOT to build unless explicitly asked

- Settings screen (v2)
- Sensitivity slider (v2)
- Palm rejection (v2)
- Multi-device switching (v2)
- Any feature not in the v1 gesture spec above
