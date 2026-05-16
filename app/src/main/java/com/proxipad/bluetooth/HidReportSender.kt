package com.proxipad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log

class HidReportSender : Handler.Callback {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var hidDeviceProxy: BluetoothHidDevice? = null
    var activeTarget: BluetoothDevice? = null

    // Single pre-allocated buffer used right before sending
    private val sendBuffer = ByteArray(MouseDescriptor.REPORT_SIZE)

    fun start() {
        if (handlerThread == null) {
            Log.d(TAG, "Starting HID HandlerThread")
            handlerThread = HandlerThread("HidReportThread").apply {
                start()
                // Use this class as the Handler.Callback
                handler = Handler(looper, this@HidReportSender)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping HID HandlerThread")
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        val proxy = hidDeviceProxy
        val target = activeTarget
        if (proxy == null || target == null) return true

        val packed = msg.arg1
        val btn = packed and 0xFF
        val x = (packed shr 8) and 0xFF
        val y = (packed shr 16) and 0xFF
        val scroll = (packed shr 24) and 0xFF

        // Convert back to signed integers
        val xSigned = x.toByte().toInt()
        val ySigned = y.toByte().toInt()
        val scrollSigned = scroll.toByte().toInt()

        val success = proxy.sendReport(target, 0, buildReport(btn, xSigned, ySigned, scrollSigned))
        if (!success) {
            Log.w(TAG, "Failed to send HID report")
        }
        return true
    }

    // Zero-allocation send method. Uses Android's Message pool to prevent GC.
    fun sendReport(btn: Int, x: Int, y: Int, scroll: Int) {
        val h = handler ?: return
        val msg = h.obtainMessage(1)
        
        val cx = x.coerceIn(-127, 127)
        val cy = y.coerceIn(-127, 127)
        val cScroll = scroll.coerceIn(-127, 127)

        // Pack all 4 bytes into a single integer thread-safely
        var packed = btn and 0xFF
        packed = packed or ((cx and 0xFF) shl 8)
        packed = packed or ((cy and 0xFF) shl 16)
        packed = packed or ((cScroll and 0xFF) shl 24)
        
        msg.arg1 = packed
        h.sendMessage(msg)
    }

    /**
     * Visible for testing. Applies clamping and populates the pre-allocated buffer.
     */
    internal fun buildReport(btn: Int, x: Int, y: Int, scroll: Int): ByteArray {
        sendBuffer[0] = btn.toByte()
        sendBuffer[1] = x.coerceIn(-127, 127).toByte()
        sendBuffer[2] = y.coerceIn(-127, 127).toByte()
        sendBuffer[3] = scroll.coerceIn(-127, 127).toByte()
        return sendBuffer
    }

    companion object {
        private const val TAG = "HidReportSender"
    }
}
