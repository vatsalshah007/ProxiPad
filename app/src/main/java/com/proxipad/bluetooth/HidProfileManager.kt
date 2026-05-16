package com.proxipad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class HidProfileManager(private val context: Context) {

    private var hidDevice: BluetoothHidDevice? = null
    private var activeDevice: BluetoothDevice? = null
    private val reportSender = HidReportSender()
    
    private var isExplicitDisconnect = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID profile connected")
                hidDevice = proxy as BluetoothHidDevice
                reportSender.hidDeviceProxy = hidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID profile disconnected")
                hidDevice = null
                reportSender.hidDeviceProxy = null
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
            
            if (state == BluetoothProfile.STATE_CONNECTED) {
                isExplicitDisconnect = false
                stopReconnectLoop()
                activeDevice = device
                reportSender.activeTarget = device
                onConnectionStateChanged?.invoke(true, device.name)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                val droppedDevice = activeDevice ?: device
                if (activeDevice == device) {
                    activeDevice = null
                    reportSender.activeTarget = null
                    onConnectionStateChanged?.invoke(false, null)
                }
                
                // If it wasn't an explicit disconnect by the user, try to recover
                if (!isExplicitDisconnect && droppedDevice != null) {
                    startReconnectLoop(droppedDevice)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startReconnectLoop(device: BluetoothDevice) {
        if (reconnectRunnable != null) return // Already running
        Log.d(TAG, "Starting reconnect loop for ${device.address}")
        
        reconnectRunnable = object : Runnable {
            override fun run() {
                if (hidDevice != null && !isExplicitDisconnect) {
                    val currentState = hidDevice?.getConnectionState(device)
                    if (currentState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Attempting auto-reconnect...")
                        hidDevice?.connect(device)
                    }
                    // Poll every 5 seconds
                    mainHandler.postDelayed(this, 5000)
                }
            }
        }
        // Initial reconnect attempt after a short delay
        mainHandler.postDelayed(reconnectRunnable!!, 2000)
    }

    private fun stopReconnectLoop() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    fun sendReport(btn: Byte, x: Byte, y: Byte, scroll: Byte) {
        reportSender.sendReport(btn, x, y, scroll)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isExplicitDisconnect = true
        stopReconnectLoop()
        val proxy = hidDevice
        val target = activeDevice
        if (proxy != null && target != null) {
            Log.d(TAG, "Disconnecting from ${target.name ?: target.address}")
            proxy.disconnect(target)
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
        stopReconnectLoop()
        reportSender.stop()
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        isExplicitDisconnect = false
        stopReconnectLoop()
        Log.d(TAG, "Connecting to ${device.address}")
        return hidDevice?.connect(device) ?: false
    }

    @SuppressLint("MissingPermission")
    fun disconnect(device: BluetoothDevice): Boolean {
        isExplicitDisconnect = true
        stopReconnectLoop()
        Log.d(TAG, "Disconnecting from ${device.address}")
        return hidDevice?.disconnect(device) ?: false
    }

    companion object {
        private const val TAG = "HidProfileManager"
    }
}
