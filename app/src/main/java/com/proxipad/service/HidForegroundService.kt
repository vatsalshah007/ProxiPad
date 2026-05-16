package com.proxipad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.proxipad.bluetooth.HidProfileManager
import com.proxipad.ui.MainActivity

class HidForegroundService : Service() {

    private val binder = LocalBinder()
    
    // Lazy init so we pass the Service context, not null
    val hidProfileManager by lazy { HidProfileManager(this) }

    inner class LocalBinder : Binder() {
        fun getService(): HidForegroundService = this@HidForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            // The service now strictly owns the HID proxy lifecycle
            hidProfileManager.init()
        } catch (e: SecurityException) {
            // Android 14+ crashes if the system resurrects this service while 
            // the required Bluetooth permissions are missing/revoked.
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hidProfileManager.release()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Do not resurrect this service if the system kills the app.
        // It relies on explicit permissions and UI lifecycle bindings.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ProxiPad Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Bluetooth HID connection alive in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // System fallback icon
            .setContentTitle("ProxiPad is Active")
            .setContentText("Maintaining Bluetooth trackpad connection")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "proxipad_hid_channel"
        private const val NOTIFICATION_ID = 1
    }
}
