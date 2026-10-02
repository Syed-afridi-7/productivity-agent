package com.syedafridi.productivity_agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.syedafridi.productivity_agent.R
import com.syedafridi.productivity_agent.bus.NativeAgentBus

class AgentForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "productivity_guardian_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.syedafridi.productivity_agent.action.START"
        const val ACTION_STOP = "com.syedafridi.productivity_agent.action.STOP"
        const val ACTION_UPDATE_STATE = "com.syedafridi.productivity_agent.action.UPDATE_STATE"
        const val EXTRA_STATE = "extra_state"
    }

    private var isRunning = false
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        return when (action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isRunning = false
                stopSelf()
                START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                val stateText = intent.getStringExtra(EXTRA_STATE) ?: "Monitoring focus session (Deep Focus)"
                val notification = buildNotification(stateText)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
                START_STICKY
            }
            else -> {
                isRunning = true
                val notification = buildNotification("Monitoring focus session (Deep Focus)")
                startForeground(NOTIFICATION_ID, notification)
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        screenReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Safely handle already unregistered or failed unregister
            }
            screenReceiver = null
        }
        isRunning = false
        super.onDestroy()
    }

    fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Productivity Guardian Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Productivity Guardian Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isScreenOn = when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> true
                    Intent.ACTION_SCREEN_OFF -> false
                    else -> return
                }
                NativeAgentBus.emit(
                    mapOf(
                        "type" to "SCREEN_STATE_CHANGED",
                        "screenOn" to isScreenOn,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            }
        }
        registerReceiver(screenReceiver, filter)
    }
}
