package com.proxipad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class HidReportSender {
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    // Pre-allocated buffer for testing phase 3a
    private val dummyReport = ByteArray(MouseDescriptor.REPORT_SIZE)

    fun start() {
        if (handlerThread == null) {
            Log.d(TAG, "Starting HID HandlerThread")
            handlerThread = HandlerThread("HidReportThread").apply {
                start()
                handler = Handler(looper)
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
    fun sendReport(hidDevice: BluetoothHidDevice, device: BluetoothDevice, report: ByteArray) {
        handler?.post {
            // id = 0 because we didn't define a Report ID in our MouseDescriptor
            val success = hidDevice.sendReport(device, 0, report)
            if (!success) {
                Log.w(TAG, "Failed to send HID report")
            }
        }
    }

    // Temporary method to test phase 3a
    fun sendDummyReport(hidDevice: BluetoothHidDevice, device: BluetoothDevice) {
        Log.d(TAG, "Sending dummy report (Left Click)")
        // Dummy report: Left click down, no movement
        dummyReport[0] = MouseDescriptor.BUTTON_LEFT.toByte()
        dummyReport[1] = 0 // X
        dummyReport[2] = 0 // Y
        dummyReport[3] = 0 // Wheel
        
        sendReport(hidDevice, device, dummyReport)
        
        // Post a release after 100ms
        handler?.postDelayed({
            Log.d(TAG, "Releasing dummy report (Left Click UP)")
            dummyReport[0] = 0 // release click
            sendReport(hidDevice, device, dummyReport)
        }, 100)
    }

    companion object {
        private const val TAG = "HidReportSender"
    }
}
