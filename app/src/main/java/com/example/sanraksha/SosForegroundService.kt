package com.example.sanraksha

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class SosForegroundService : Service() {

    private val CHANNEL_ID = "SosListeningChannel"
    private val NOTIFICATION_ID = 1
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LISTENING" -> startListening()
            "STOP_LISTENING" -> stopListening()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "SOS Listening Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sanraksha is listening for SOS keywords"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sanraksha: Safety Mode Active")
            .setContentText("Listening for SOS keywords...")
            .setSmallIcon(R.drawable.ic_mic_on)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 🔔 Notify MainActivity to start voice recognition
        val broadcastIntent = Intent("START_VOICE_LISTENING")
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        // 🔁 Restart listening every 2 seconds (loop)
        Handler(Looper.getMainLooper()).postDelayed(this::startListening, 2000)
    }

    private fun stopListening() {
        isListening = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        // 🔔 Notify MainActivity to stop listening
        val broadcastIntent = Intent("STOP_VOICE_LISTENING")
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
