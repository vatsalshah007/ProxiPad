package com.proxipad.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.proxipad.bluetooth.HidProfileManager
import com.proxipad.bluetooth.MouseDescriptor
import com.proxipad.gesture.GestureEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.proxipad.service.HidForegroundService

class MainActivity : ComponentActivity() {
    private var hidProfileManager by mutableStateOf<HidProfileManager?>(null)
    private var isBound = false
    private var permissionsGrantedState by mutableStateOf(false)
    private var hasRequestedPermissions = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HidForegroundService.LocalBinder
            hidProfileManager = binder.getService().hidProfileManager
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            hidProfileManager = null
        }
    }
    
    private var leftButtonDown = false
    private var rightButtonDown = false

    private val releaseRunnable = Runnable {
        leftButtonDown = false
        rightButtonDown = false
        sendCurrentState(0, 0, 0)
    }

    companion object {
        // 5 minute timeout for mid-session dropouts
        private const val SESSION_TIMEOUT_MS = 5L * 60L * 1000L
    }

    private var timeoutJob: Job? = null
    private var isConnectedState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // App always launches in portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        permissionsGrantedState = checkPermissionsGranted()

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                val granted = checkPermissionsGranted()
                permissionsGrantedState = granted
                if (granted && !isBound) {
                    startAndBindService()
                }
            }

            LaunchedEffect(Unit) {
                if (!permissionsGrantedState) {
                    requestPermissions(permissionLauncher)
                }
            }

            if (!permissionsGrantedState) {
                PermissionScreen { requestPermissions(permissionLauncher) }
                return@setContent
            }

            val manager = hidProfileManager
            var showPicker by remember { mutableStateOf(false) }
            var showDisconnectConfirm by remember { mutableStateOf(false) }
            var isConnected by remember { mutableStateOf(false) }
            var deviceName by remember { mutableStateOf<String?>(null) }

            if (manager != null) {
                DisposableEffect(manager) {
                    manager.onConnectionStateChanged = { connected, name ->
                        isConnected = connected
                        isConnectedState = connected
                        deviceName = name
                        handleConnectionStateChange(connected)
                    }
                    onDispose {
                        manager.onConnectionStateChanged = null
                    }
                }
            }

            MainScreen(
                isConnected = isConnected,
                deviceName = deviceName,
                onGesture = { event ->
                    // Only process gestures if actually connected
                    if (isConnected) {
                        handleGesture(event)
                    }
                },
                onStatusBarClick = {
                    if (isConnected) {
                        showDisconnectConfirm = true
                    } else {
                        showPicker = true
                    }
                }
            )

            if (showPicker) {
                DevicePickerDialog(
                    onDeviceSelected = { device ->
                        Log.d("MainActivity", "Connecting to device: ${device.name ?: device.address}")
                        hidProfileManager?.connect(device)
                    },
                    onDismiss = { showPicker = false }
                )
            }

            if (showDisconnectConfirm) {
                DisconnectConfirmDialog(
                    deviceName = deviceName ?: "Unknown Device",
                    onConfirm = {
                        hidProfileManager?.disconnect()
                        showDisconnectConfirm = false
                    },
                    onDismiss = { showDisconnectConfirm = false }
                )
            }
        }
    }

    private fun handleConnectionStateChange(connected: Boolean) {
        if (connected) {
            // Cancel any pending dropout timers
            timeoutJob?.cancel()
            // Force rotate to landscape ONLY after BT connection is confirmed
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            // Mid-session dropout logic
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                Toast.makeText(this, "Connection lost. Reconnecting...", Toast.LENGTH_SHORT).show()
                timeoutJob?.cancel()
                timeoutJob = lifecycleScope.launch {
                    delay(SESSION_TIMEOUT_MS)
                    // If still disconnected after 5 mins: snap back to portrait
                    if (!isConnectedState) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Toast.makeText(this@MainActivity, "Session ended. Tap to connect.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if permissions were granted while user was in Android Settings
        val granted = checkPermissionsGranted()
        permissionsGrantedState = granted
        if (granted && !isBound) {
            startAndBindService()
        }

        // If user backgrounds app during active session and returns:
        if (isConnectedState) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            // Only snap to portrait if disconnected and we aren't in a dropout timeout
            if (timeoutJob?.isActive != true) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (checkPermissionsGranted() && !isBound) {
            startAndBindService()
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, HidForegroundService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service when the UI goes to the background.
        // We DO NOT stop the service or release the Bluetooth proxy!
        // The service stays perfectly alive.
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            hidProfileManager = null
        }
    }

    private fun handleGesture(event: GestureEvent) {
        var x: Int = 0
        var y: Int = 0
        var scroll: Int = 0
        var scheduleRelease = false

        when (event) {
            is GestureEvent.Move -> {
                x = event.dx
                y = event.dy
            }
            is GestureEvent.Scroll -> {
                scroll = event.amount
            }
            is GestureEvent.Tap -> {
                leftButtonDown = true
                scheduleRelease = true
            }
            is GestureEvent.RightTap -> {
                rightButtonDown = true
                scheduleRelease = true
            }
        }

        sendCurrentState(x, y, scroll)

        if (scheduleRelease) {
            window.decorView.removeCallbacks(releaseRunnable)
            window.decorView.postDelayed(releaseRunnable, 15)
        }
    }

    private fun sendCurrentState(x: Int, y: Int, scroll: Int) {
        var buttons = 0
        if (leftButtonDown) buttons = buttons or MouseDescriptor.BUTTON_LEFT
        if (rightButtonDown) buttons = buttons or MouseDescriptor.BUTTON_RIGHT
        
        hidProfileManager?.sendReport(buttons, x, y, scroll)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutJob?.cancel()
    }

    private fun checkPermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        val permissionsToRequest = mutableListOf<String>()
        var permanentlyDenied = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                // If we've asked before and Android says we shouldn't show rationale, it means "Don't ask again" was triggered.
                if (hasRequestedPermissions && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                    permanentlyDenied = true
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                if (hasRequestedPermissions && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    permanentlyDenied = true
                }
            }
        }

        hasRequestedPermissions = true

        if (permanentlyDenied) {
            // User permanently denied permissions; route them directly to Android Settings
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } else if (permissionsToRequest.isNotEmpty()) {
            launcher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun PermissionScreen(onRequestClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17171A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                text = "Permissions Required",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "ProxiPad needs Bluetooth Connect and Notification permissions to function as a wireless trackpad.",
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = onRequestClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C30))
            ) {
                Text("Grant Permissions", color = Color.White)
            }
        }
    }
}
