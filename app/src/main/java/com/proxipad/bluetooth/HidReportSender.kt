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

        // Unpack the 4 bytes from arg1 (packed to avoid allocating objects during ACTION_MOVE)
        val packed = msg.arg1
        sendBuffer[0] = (packed and 0xFF).toByte()
        sendBuffer[1] = ((packed shr 8) and 0xFF).toByte()
        sendBuffer[2] = ((packed shr 16) and 0xFF).toByte()
        sendBuffer[3] = ((packed shr 24) and 0xFF).toByte()

        val success = proxy.sendReport(target, 0, sendBuffer)
        if (!success) {
            Log.w(TAG, "Failed to send HID report")
        }
        return true
    }

    // Zero-allocation send method. Uses Android's Message pool to prevent GC.
    fun sendReport(btn: Byte, x: Byte, y: Byte, scroll: Byte) {
        val h = handler ?: return
        val msg = h.obtainMessage(1)
        
        // Pack all 4 bytes into a single integer
        var packed = btn.toInt() and 0xFF
        packed = packed or ((x.toInt() and 0xFF) shl 8)
        packed = packed or ((y.toInt() and 0xFF) shl 16)
        packed = packed or ((scroll.toInt() and 0xFF) shl 24)
        
        msg.arg1 = packed
        h.sendMessage(msg)
    }

    companion object {
        private const val TAG = "HidReportSender"
    }
}
