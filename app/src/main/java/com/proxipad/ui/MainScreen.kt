package com.proxipad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.proxipad.gesture.GestureEvent

@Composable
fun MainScreen(
    isConnected: Boolean,
    deviceName: String?,
    onGesture: (GestureEvent) -> Unit,
    onStatusBarClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
            .padding(top = 24.dp)
    ) {
        // 1. Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .clickable { onStatusBarClick() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) deviceName ?: "Connected" else "Not Connected",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 2. Touchpad surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
        ) {
            AndroidView(
                factory = { context ->
                    TouchSurface(context, onGesture)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dim overlay and block touches when disconnected
            if (!isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = true) {} // Consumes touches to prevent them from reaching AndroidView
                )
            }
        }
    }
}

@Composable
fun DisconnectConfirmDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA),
        title = { Text("Disconnect?") },
        text = { Text("Are you sure you want to disconnect from $deviceName?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Disconnect", color = Color(0xFFF44336))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFAAAAAA))
            }
        }
    )
}
