package com.proxipad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

class HidProfileManager(private val context: Context) {

    private var hidDevice: BluetoothHidDevice? = null
    private val reportSender = HidReportSender()
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID profile connected")
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID profile disconnected")
                hidDevice = null
            }
        }
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            val stateStr = when (state) {
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "UNKNOWN($state)"
            }
            Log.d(TAG, "onConnectionStateChanged: state=$stateStr")
            
            if (state == BluetoothProfile.STATE_CONNECTED && hidDevice != null) {
                // Phase 3b: Test sending a static report when connected
                reportSender.sendDummyReport(hidDevice!!, device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun init() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return
        }

        reportSender.start()
        
        Log.d(TAG, "Getting profile proxy for HID_DEVICE")
        bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "ProxiPad",
            "ProxiPad Bluetooth HID",
            "ProxiPad",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
            MouseDescriptor.MOUSE_REPORT_DESCRIPTOR
        )

        Log.d(TAG, "Registering HID app...")
        val registered = hidDevice?.registerApp(
            sdp,
            null,
            null,
            context.mainExecutor,
            hidDeviceCallback
        )
        
        Log.d(TAG, "registerApp returned: $registered")
    }

    @SuppressLint("MissingPermission")
    fun release() {
        Log.d(TAG, "Releasing HID profile proxy")
        reportSender.stop()
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        Log.d(TAG, "Connecting to ${device.address}")
        return hidDevice?.connect(device) ?: false
    }

    @SuppressLint("MissingPermission")
    fun disconnect(device: BluetoothDevice): Boolean {
        Log.d(TAG, "Disconnecting from ${device.address}")
        return hidDevice?.disconnect(device) ?: false
    }

    companion object {
        private const val TAG = "HidProfileManager"
    }
}
