package com.proxipad.bluetooth

object MouseDescriptor {
    val MOUSE_REPORT_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
        // Buttons (Byte 0)
        0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
        0x29.toByte(), 0x03.toByte(), //     Usage Maximum (3)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(), //     Report Count (3)
        0x75.toByte(), 0x01.toByte(), //     Report Size (1)
        0x81.toByte(), 0x02.toByte(), //     Input (Data,Var,Abs)
        // Padding for buttons (5 bits)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x75.toByte(), 0x05.toByte(), //     Report Size (5)
        0x81.toByte(), 0x03.toByte(), //     Input (Cnst,Var,Abs)
        // X, Y (Bytes 1 and 2)
        0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(), //     Usage (X)
        0x09.toByte(), 0x31.toByte(), //     Usage (Y)
        0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8)
        0x95.toByte(), 0x02.toByte(), //     Report Count (2)
        0x81.toByte(), 0x06.toByte(), //     Input (Data,Var,Rel)
        // Wheel (Byte 3)
        0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x81.toByte(), 0x06.toByte(), //     Input (Data,Var,Rel)
        0xC0.toByte(),                //   End Collection
        0xC0.toByte()                 // End Collection
    )

    // Constants for the 4-byte report format
    const val REPORT_SIZE = 4
    
    // Button state bits
    const val BUTTON_LEFT = 0x01
    const val BUTTON_RIGHT = 0x02
    const val BUTTON_MIDDLE = 0x04
}
