package com.proxipad.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class MouseDescriptorTest {

    @Test
    fun `MOUSE_REPORT_DESCRIPTOR byte array length is exactly 62 bytes`() {
        assertEquals("HID descriptor length must be exactly 62 bytes", 62, MouseDescriptor.MOUSE_REPORT_DESCRIPTOR.size)
    }

    @Test
    fun `BUTTON_LEFT is 0x01, BUTTON_RIGHT is 0x02, BUTTON_MIDDLE is 0x04`() {
        assertEquals("Left button should be bit 0", 0x01, MouseDescriptor.BUTTON_LEFT)
        assertEquals("Right button should be bit 1", 0x02, MouseDescriptor.BUTTON_RIGHT)
        assertEquals("Middle button should be bit 2", 0x04, MouseDescriptor.BUTTON_MIDDLE)
    }

    @Test
    fun `REPORT_SIZE is 4`() {
        assertEquals("Mouse report size must be 4 bytes", 4, MouseDescriptor.REPORT_SIZE)
    }
}
