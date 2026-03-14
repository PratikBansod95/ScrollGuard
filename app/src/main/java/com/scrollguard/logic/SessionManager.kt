package com.scrollguard.logic

import android.content.Context
import androidx.core.content.ContextCompat
import com.scrollguard.data.AppLimitEntity
import com.scrollguard.data.AppRepository
import com.scrollguard.services.OverlayCommand
import com.scrollguard.services.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class SessionState(
    val packageName: String,
    val appName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isBlocked: Boolean,
    val cooldownRemainingSeconds: Int,
)

data class DashboardStats(
    val blockedSessionsToday: Int = 0,
    val doomScrollDetections: Int = 0,
    val mostUsedApp: String = "None",
)

class SessionManager(
    private val context: Context,
    private val repository: AppRepository,
) {
    private val prefs = context.getSharedPreferences("scrollguard_stats", Context.MODE_PRIVATE)

    private val _activeSession = MutableStateFlow<SessionState?>(null)
    val activeSession: StateFlow<SessionState?> = _activeSession.asStateFlow()

    private val _dashboardStats = MutableStateFlow(loadStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    private val cooldowns = ConcurrentHashMap<String, Long>()
    private var activePackageName: String? = null
    private var sessionStartMillis = 0L
    private var lastWarningAt = 0L

    suspend fun onForegroundAppChanged(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        if (packageName == context.packageName) {
            if (activePackageName != null) {
                clearSession()
            }
            return
        }

        val limit = repository.getLimit(packageName)
        if (limit == null) {
            if (activePackageName != null && activePackageName != packageName) {
                clearSession()
            }
            return
        }

        val cooldownRemaining = cooldownRemaining(packageName)
        if (cooldownRemaining > 0) {
            showBlock(limit, cooldownRemaining)
            activePackageName = packageName
            return
        }

        if (activePackageName != packageName) {
            activePackageName = packageName
            sessionStartMillis = System.currentTimeMillis()
            lastWarningAt = 0L
        }

        val elapsedSeconds = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).toInt()
        val remainingSeconds = (limit.timeLimitSeconds - elapsedSeconds).coerceAtLeast(0)

        if (remainingSeconds == 0) {
            startCooldown(limit)
            showBlock(limit, limit.cooldownSeconds)
            incrementBlockedSessions(limit.appName)
            return
        }

        _activeSession.value = SessionState(
            packageName = limit.packageName,
            appName = limit.appName,
            totalSeconds = limit.timeLimitSeconds,
            remainingSeconds = remainingSeconds,
            isBlocked = false,
            cooldownRemainingSeconds = 0,
        )
        dispatchOverlay(
            OverlayCommand.ShowTimer(
                appName = limit.appName,
                totalSeconds = limit.timeLimitSeconds,
                remainingSeconds = remainingSeconds,
            ),
        )
    }

    fun handleScrollThreshold(packageName: String?) {
        val session = _activeSession.value ?: return
        if (packageName == null || packageName != activePackageName || session.isBlocked) return

        val now = System.currentTimeMillis()
        val sessionAge = now - sessionStartMillis
        if (sessionAge < MIN_SESSION_AGE_FOR_WARNING_MS) {
            return
        }
        if (now - lastWarningAt < WARNING_COOLDOWN_MS) {
            return
        }

        lastWarningAt = now
        incrementDoomScrollDetections()
        dispatchOverlay(
            OverlayCommand.ShowWarning(
                title = "Slow down a bit",
                message = "You have been scrolling quickly for a while. Want 10 more seconds or a clean exit?",
            ),
        )
    }

    fun extendCurrentSession(extraSeconds: Int) {
        val session = _activeSession.value ?: return
        sessionStartMillis += extraSeconds * 1_000L
        _activeSession.value = session.copy(
            remainingSeconds = session.remainingSeconds + extraSeconds,
        )
    }

    fun clearSession() {
        activePackageName = null
        sessionStartMillis = 0L
        lastWarningAt = 0L
        _activeSession.value = null
        dispatchOverlay(OverlayCommand.HideAll)
    }

    private fun startCooldown(limit: AppLimitEntity) {
        cooldowns[limit.packageName] = System.currentTimeMillis() + limit.cooldownSeconds * 1_000L
        _activeSession.value = SessionState(
            packageName = limit.packageName,
            appName = limit.appName,
            totalSeconds = limit.timeLimitSeconds,
            remainingSeconds = 0,
            isBlocked = true,
            cooldownRemainingSeconds = limit.cooldownSeconds,
        )
    }

    private fun showBlock(limit: AppLimitEntity, cooldownRemainingSeconds: Int) {
        _activeSession.value = SessionState(
            packageName = limit.packageName,
            appName = limit.appName,
            totalSeconds = limit.timeLimitSeconds,
            remainingSeconds = 0,
            isBlocked = true,
            cooldownRemainingSeconds = cooldownRemainingSeconds,
        )
        dispatchOverlay(
            OverlayCommand.ShowBlock(
                appName = limit.appName,
                cooldownSeconds = cooldownRemainingSeconds,
            ),
        )
    }

    private fun cooldownRemaining(packageName: String): Int {
        val end = cooldowns[packageName] ?: return 0
        val remaining = ((end - System.currentTimeMillis()) / 1000L).toInt()
        if (remaining <= 0) {
            cooldowns.remove(packageName)
        }
        return remaining.coerceAtLeast(0)
    }

    private fun dispatchOverlay(command: OverlayCommand) {
        ContextCompat.startForegroundService(context, OverlayService.createIntent(context, command))
    }

    private fun loadStats(): DashboardStats {
        return DashboardStats(
            blockedSessionsToday = prefs.getInt(KEY_BLOCKED_SESSIONS, 0),
            doomScrollDetections = prefs.getInt(KEY_DOOM_DETECTIONS, 0),
            mostUsedApp = prefs.getString(KEY_MOST_USED_APP, "None") ?: "None",
        )
    }

    private fun incrementBlockedSessions(appName: String) {
        prefs.edit()
            .putInt(KEY_BLOCKED_SESSIONS, prefs.getInt(KEY_BLOCKED_SESSIONS, 0) + 1)
            .putString(KEY_MOST_USED_APP, appName)
            .apply()
        _dashboardStats.value = loadStats()
    }

    private fun incrementDoomScrollDetections() {
        prefs.edit()
            .putInt(KEY_DOOM_DETECTIONS, prefs.getInt(KEY_DOOM_DETECTIONS, 0) + 1)
            .apply()
        _dashboardStats.value = loadStats()
    }

    companion object {
        private const val KEY_BLOCKED_SESSIONS = "blocked_sessions_today"
        private const val KEY_DOOM_DETECTIONS = "doom_scroll_detections"
        private const val KEY_MOST_USED_APP = "most_used_app"
        private const val MIN_SESSION_AGE_FOR_WARNING_MS = 8_000L
        private const val WARNING_COOLDOWN_MS = 30_000L
    }
}
