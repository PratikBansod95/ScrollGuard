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
    private val monitoringPrefs by lazy {
        getSharedPreferences(PREFS_MONITORING, Context.MODE_PRIVATE)
    }
    private var monitorJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var lastForegroundDetectedAt = 0L

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        updateMonitoringHeartbeat(isRunning = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob?.isActive != true) {
            monitorJob = serviceScope.launch {
                val sessionManager = (application as ScrollGuardApp).container.sessionManager
                while (isActive) {
                    updateMonitoringHeartbeat(isRunning = true)
                    sessionManager.onForegroundAppChanged(currentForegroundPackage())
                    delay(1_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        updateMonitoringHeartbeat(isRunning = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun currentForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 5_000L
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var resumedPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumedPackage = event.packageName
            }
        }
        if (!resumedPackage.isNullOrBlank() && !IGNORED_FOREGROUND_PACKAGES.contains(resumedPackage)) {
            lastForegroundPackage = resumedPackage
            lastForegroundDetectedAt = end
        }
        val timeSinceLast = end - lastForegroundDetectedAt
        return if (!lastForegroundPackage.isNullOrBlank() && timeSinceLast <= ABSOLUTE_FOREGROUND_TIMEOUT_MS) {
            lastForegroundPackage
        } else {
            null
        }
    }

    private fun updateMonitoringHeartbeat(isRunning: Boolean) {
        monitoringPrefs.edit()
            .putBoolean(KEY_MONITORING_ENABLED, isRunning)
            .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
            .apply()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SnapOut monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SnapOut is monitoring apps")
            .setContentText("Watching monitored apps and session timers.")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PREFS_MONITORING = "scrollguard_monitoring"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"

        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ABSOLUTE_FOREGROUND_TIMEOUT_MS = 600_000L

        private val IGNORED_FOREGROUND_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.oneui.home",
            "com.miui.home",
        )
    }
}







