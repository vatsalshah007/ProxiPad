---
trigger: manual
---

Write automated unit tests for the following modules in ProxiPad.
Use JUnit4 and place all tests in the androidTest or test source set
as appropriate.

--- GestureEngine Tests ---

1. Single finger move produces correct delta X and Y values
2. Tap is detected when ACTION_UP fires within 150ms of ACTION_DOWN
   and total movement is under 10px
3. Tap is NOT detected when total movement exceeds 10px
4. Two-finger tap produces a RightTap GestureEvent
5. Two-finger vertical drag produces a Scroll GestureEvent
6. Verify the same reportBuffer instance is reused across multiple
   ACTION_MOVE events — no new ByteArray allocated on each call

--- MouseDescriptor Tests ---

1. MOUSE_REPORT_DESCRIPTOR byte array length is exactly 34 bytes
2. BUTTON_LEFT == 0x01, BUTTON_RIGHT == 0x02, BUTTON_MIDDLE == 0x04
3. REPORT_SIZE == 4

--- HidReportSender Tests ---

1. Delta values above 127 are clamped to 127
2. Delta values below -127 are clamped to -127
3. The same buffer instance is returned across multiple buildReport()
   calls (no new allocation)

--- Rules ---

- Use JUnit4
- Mock BluetoothHidDevice where needed using Mockito
- No UI or BT hardware required — pure logic tests only
- One test class per module
- Name test classes: GestureEngineTest, MouseDescriptorTest,
  HidReportSenderTest
