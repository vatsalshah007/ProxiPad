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
- Any feature not in the v1 gesture spec above, OR in the PHASE 8 spec below

---

# PHASE 8 (v2) — Two-Finger Drag-and-Drop

IMPORTANT: Everything above this line is existing, working v1 code and is
LOCKED. Do not refactor, rename, or restructure anything above this line
unless a specific step below explicitly requires touching that file. Do
not touch GestureEngine's existing move/tap/right-tap/scroll logic — this
phase is additive only.

Only build what is described in this Phase 8 section. Ignore every other
phase above — they are done. Follow the same "one unit at a time, wait for
confirmation" workflow described in "How We Work" above.

## Feature Description

A new gesture: two-finger drag-and-drop. Finger 1 (first pointer down)
acts as a "hold" — like pressing and holding the left mouse button.
Finger 2 (second pointer down) drags — its movement moves the cursor
while the button stays held. Lifting either finger releases the button
and ends the drag.

This reuses the existing HID report format exactly as-is: byte 0 bit0
(Left click) held at 1 for the duration of the drag, bytes 1–2 carrying
finger 2's X/Y deltas each report. No new HID bytes, no new descriptor,
no new report type. This is purely a new sequence of existing report
values.

## Disambiguation from 2-finger tap (right click)

The existing v1 spec already defines 2-finger tap as: pointer UP within
150ms of DOWN + total movement < 10px, mapped to a right click. The new
drag gesture starts identically (two fingers down) but is distinguished
by MOVEMENT, not time:

- If finger 2 moves past a slop threshold (10px, matching the existing
  tap movement threshold already used in GestureEngine — reuse that same
  constant, do not introduce a second magic number for this) before
  lifting, treat it as a drag, not a tap.
- If both fingers lift before the slop threshold is exceeded, this is
  unchanged v1 behavior — the existing right-click tap logic handles it.
  Do not fire any new drag event in that case.

Do not implement this as a timer/long-press gesture. Time is only used
for the tap-vs-hold path that already exists in GestureEngine; the new
drag path is entered purely via a movement threshold on finger 2.

## Unit 8a — Extend GestureEvent.kt

Add to the existing sealed class, matching its current style exactly
(check whether existing cases are objects, data classes, or both, and
follow the same pattern):

- DragHoldStart — fired once when finger 2's movement confirms this is a
  drag, not a tap. No payload needed unless the existing GestureEvent
  cases for Move already carry a coordinate the HID layer expects — if
  so, match that shape.
- DragMove(dx: Int, dy: Int) — fired repeatedly while dragging, matching
  whatever delta type/range the existing Move case already uses (should
  align with the -127..127 byte range used elsewhere in the report).
- DragRelease — fired once when either finger lifts, ending the drag.

Test: file compiles, no logic yet.

## Unit 8b — Extend GestureEngine.kt state tracking

GestureEngine currently must remain O(1) per touch callback with no
allocation in the hot path (ACTION_MOVE). Extend its existing pointer
tracking (do not create a second parallel tracking mechanism) to
recognize this second concurrent state:

- Track pointer id of "finger 1" (first ACTION_DOWN / first pointer in
  the active set) and "finger 2" (the pointer added on
  ACTION_POINTER_DOWN while finger 1 is still active).
- On finger 2's ACTION_POINTER_DOWN: record its starting x/y into
  pre-allocated fields (do not allocate a new object — add fields to
  whatever pre-allocated state object GestureEngine already holds for
  the existing tap-tracking logic).
- On subsequent ACTION_MOVE while both fingers are active: compute
  finger 2's displacement from its last known position (not from its
  start position — this must be a per-frame delta, matching how
  single-finger move deltas are already computed elsewhere in this
  file). If displacement exceeds the existing tap movement threshold
  and DragHoldStart has not yet fired this gesture, emit DragHoldStart
  first, then emit a DragMove carrying the accumulated delta since
  finger 2 touched down (so the very first report after DragHoldStart
  is not a zero-delta — a held button with zero movement can register
  as a stuck click on some hosts). After that, emit DragMove with the
  per-frame delta each subsequent ACTION_MOVE.
- On ACTION_POINTER_UP or ACTION_UP for either finger 1 or finger 2,
  while a drag is active (DragHoldStart already fired this gesture):
  emit DragRelease and reset drag-tracking state.
- On ACTION_POINTER_UP or ACTION_UP for either finger before the slop
  threshold was exceeded: do not emit any drag event. Let this fall
  through to the existing 2-finger-tap right-click logic unchanged.

No object allocation anywhere in this unit's ACTION_MOVE path. Reuse
pre-allocated fields only, consistent with constraint #4 above.

Test: log output showing DragHoldStart / DragMove / DragRelease firing
correctly for: (1) two-finger tap with no movement — no drag events,
right-click still fires as before, (2) two-finger touch where finger 2
moves — DragHoldStart then repeated DragMove, (3) lifting finger 1
mid-drag — DragRelease fires, (4) lifting finger 2 mid-drag — DragRelease
fires.

## Unit 8c — Wire drag events into HID report sending

Find wherever GestureEvent cases are currently translated into HID
report bytes and sent via HidReportSender (this is likely in
MainActivity, TouchSurface, or a small dispatcher between GestureEngine
and HidReportSender — locate it, do not assume its name). Add handling
for the three new events using the existing pre-allocated report buffer,
matching the existing pattern used for Move/Tap/RightTap/Scroll exactly:

- DragHoldStart -> set byte 0 bit0 (Left click) to 1, leave X/Y/scroll
  at 0, send.
- DragMove(dx, dy) -> keep byte 0 bit0 at 1, set byte 1 = dx, byte 2 =
  dy, byte 3 = 0, send.
- DragRelease -> clear byte 0 bit0 to 0, byte 1/2/3 = 0, send.

Do not create a second report-sending path. Do not allocate a new buffer
— reuse the existing pre-allocated 4-byte buffer.

Test: with the app connected to a real tablet, hold finger 1, drag with
finger 2, confirm the tablet cursor moves while a "button held" state is
visually indicated (e.g. an icon can be dragged and dropped). Release
either finger and confirm the drop occurs at that position, not before.

## What NOT to build in Phase 8

- No visual/haptic feedback for drag-start (that's a separate future
  polish item, not part of this phase, unless explicitly asked)
- No settings/sensitivity control for the slop threshold — reuse the
  existing tap threshold constant as specified above
- No changes to 1-finger move, 1-finger tap, or 2-finger scroll behavior
- No new HID report format or descriptor changes
