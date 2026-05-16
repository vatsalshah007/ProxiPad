package com.proxipad.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DevicePickerDialog(
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val containerColor = Color(0xFF121212)
    val mutedColor = Color(0xFFAAAAAA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF333333)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            Text(
                text = "Select Device",
                color = mutedColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (adapter == null || !adapter.isEnabled) {
                Text(
                    text = "Bluetooth is disabled. Please enable it.",
                    color = mutedColor,
                    fontSize = 16.sp
                )
                return@Column
            }

            val bondedDevices = remember { adapter.bondedDevices.toList() }

            if (bondedDevices.isEmpty()) {
                Text(
                    text = "No paired devices found. Pair your tablet in Bluetooth settings.",
                    color = mutedColor,
                    fontSize = 16.sp
                )
            } else {
                LazyColumn {
                    items(bondedDevices) { device ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = device.name ?: "Unknown Device",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = device.address,
                                color = mutedColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
