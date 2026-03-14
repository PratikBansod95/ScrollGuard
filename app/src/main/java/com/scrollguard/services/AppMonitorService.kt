package com.scrollguard.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.scrollguard.R
import com.scrollguard.ScrollGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var usageStatsManager: UsageStatsManager
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch {
                val sessionManager = (application as ScrollGuardApp).container.sessionManager
                while (isActive) {
                    sessionManager.onForegroundAppChanged(currentForegroundPackage())
                    delay(1_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun currentForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 5_000L
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var packageName: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                packageName = event.packageName
            }
        }
        return packageName
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ScrollGuard monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ScrollGuard is monitoring apps")
            .setContentText("Watching monitored apps and session timers.")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
