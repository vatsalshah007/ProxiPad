package com.proxipad.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class HidReportSenderTest {

    private lateinit var sender: HidReportSender

    @Before
    fun setUp() {
        sender = HidReportSender()
    }

    @Test
    fun `Delta values above 127 are clamped to 127`() {
        val result = sender.buildReport(1, 200, 150, 300)
        
        // 200 -> 127, 150 -> 127, 300 -> 127
        assertArrayEquals(byteArrayOf(1.toByte(), 127.toByte(), 127.toByte(), 127.toByte()), result)
    }

    @Test
    fun `Delta values below -127 are clamped to -127`() {
        val result = sender.buildReport(0, -200, -150, -300)
        
        // -200 -> -127, -150 -> -127, -300 -> -127
        assertArrayEquals(byteArrayOf(0.toByte(), (-127).toByte(), (-127).toByte(), (-127).toByte()), result)
    }

    @Test
    fun `The same buffer instance is returned across multiple buildReport calls (no new allocation)`() {
        val firstCall = sender.buildReport(1, 10, 10, 0)
        val secondCall = sender.buildReport(0, -5, -5, 0)

        // Using assertSame checks for reference equality in Kotlin/Java
        assertSame("Buffer instance should be exactly the same object", firstCall, secondCall)
    }
}
