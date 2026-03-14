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
import java.time.LocalDate
import java.time.ZoneId

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
    val topBlockedApp: String = "None",
)

class SessionManager(
    private val context: Context,
    private val repository: AppRepository,
) {
    private val prefs = context.getSharedPreferences("scrollguard_stats", Context.MODE_PRIVATE)
    private val zoneId = ZoneId.systemDefault()

    private val _activeSession = MutableStateFlow<SessionState?>(null)
    val activeSession: StateFlow<SessionState?> = _activeSession.asStateFlow()

    private val _dashboardStats = MutableStateFlow(loadStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    private var activePackageName: String? = null
    private var sessionStartMillis = 0L
    private var lastWarningAt = 0L
    private var warningsThisSession = 0

    init {
        activePackageName = prefs.getString(KEY_ACTIVE_PACKAGE, null)
        sessionStartMillis = prefs.getLong(KEY_SESSION_START_MILLIS, 0L)
        ensureStatsForToday()
        _dashboardStats.value = loadStats()
    }

    suspend fun onForegroundAppChanged(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            if (activePackageName != null) {
                clearSession()
            }
            return
        }
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

        val cooldownRemaining = cooldownRemaining(limit)
        if (cooldownRemaining > 0) {
            showBlock(limit, cooldownRemaining)
            activePackageName = packageName
            return
        }

        if (activePackageName != packageName) {
            activePackageName = packageName
            sessionStartMillis = System.currentTimeMillis()
            lastWarningAt = 0L
            warningsThisSession = 0
            persistSessionState()
        }

        val elapsedSeconds = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).toInt()
        val remainingSeconds = (limit.timeLimitSeconds - elapsedSeconds).coerceAtLeast(0)

        if (remainingSeconds == 0) {
            startCooldown(limit)
            showBlock(limit, limit.cooldownSeconds)
            incrementBlockedSessions(limit.packageName, limit.appName)
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

    fun handleScrollThreshold(
        packageName: String?,
        scrollCount: Int,
        averageIntervalMillis: Long,
        sustainedWindowMillis: Long,
    ) {
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

        warningsThisSession += 1
        lastWarningAt = now
        incrementDoomScrollDetections()

        val title = if (warningsThisSession == 1) {
            "You slipped into a scroll loop"
        } else {
            "The feed still has your attention"
        }
        val sustainedSeconds = (sustainedWindowMillis / 1_000L).coerceAtLeast(1L)
        val message = if (warningsThisSession == 1) {
            "You kept moving through the feed for about ${sustainedSeconds}s with $scrollCount strong scrolls. Want 10 more seconds or a clean exit?"
        } else {
            "Another sustained scrolling burst showed up in this session. You are moving at about ${averageIntervalMillis}ms per scroll, so this is a good stop point."
        }

        dispatchOverlay(
            OverlayCommand.ShowWarning(
                title = title,
                message = message,
            ),
        )
    }

    fun extendCurrentSession(extraSeconds: Int) {
        val session = _activeSession.value ?: return
        sessionStartMillis += extraSeconds * 1_000L
        _activeSession.value = session.copy(
            remainingSeconds = session.remainingSeconds + extraSeconds,
        )
        persistSessionState()
    }

    fun clearSession() {
        activePackageName = null
        sessionStartMillis = 0L
        lastWarningAt = 0L
        warningsThisSession = 0
        _activeSession.value = null
        clearPersistedSessionState()
        dispatchOverlay(OverlayCommand.HideAll)
    }

    private suspend fun startCooldown(limit: AppLimitEntity) {
        val cooldownEndMillis = System.currentTimeMillis() + limit.cooldownSeconds * 1_000L
        repository.updateCooldownEnd(limit.packageName, cooldownEndMillis)
        sessionStartMillis = 0L
        lastWarningAt = 0L
        warningsThisSession = 0
        clearPersistedSessionState()
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

    private suspend fun cooldownRemaining(limit: AppLimitEntity): Int {
        val end = limit.cooldownEndMillis
        if (end <= 0L) {
            return 0
        }

        val remaining = ((end - System.currentTimeMillis()) / 1000L).toInt()
        if (remaining <= 0) {
            repository.updateCooldownEnd(limit.packageName, 0L)
        }
        return remaining.coerceAtLeast(0)
    }

    private fun dispatchOverlay(command: OverlayCommand) {
        ContextCompat.startForegroundService(context, OverlayService.createIntent(context, command))
    }

    private fun loadStats(): DashboardStats {
        ensureStatsForToday()
        return DashboardStats(
            blockedSessionsToday = prefs.getInt(KEY_BLOCKED_SESSIONS, 0),
            doomScrollDetections = prefs.getInt(KEY_DOOM_DETECTIONS, 0),
            topBlockedApp = resolveTopBlockedApp(),
        )
    }

    private fun incrementBlockedSessions(packageName: String, appName: String) {
        ensureStatsForToday()
        val perAppKey = "$KEY_BLOCKED_COUNT_PREFIX$packageName"
        prefs.edit()
            .putInt(KEY_BLOCKED_SESSIONS, prefs.getInt(KEY_BLOCKED_SESSIONS, 0) + 1)
            .putInt(perAppKey, prefs.getInt(perAppKey, 0) + 1)
            .putString("$KEY_BLOCKED_APP_NAME_PREFIX$packageName", appName)
            .apply()
        _dashboardStats.value = loadStats()
    }

    private fun incrementDoomScrollDetections() {
        ensureStatsForToday()
        prefs.edit()
            .putInt(KEY_DOOM_DETECTIONS, prefs.getInt(KEY_DOOM_DETECTIONS, 0) + 1)
            .apply()
        _dashboardStats.value = loadStats()
    }

    private fun persistSessionState() {
        prefs.edit()
            .putString(KEY_ACTIVE_PACKAGE, activePackageName)
            .putLong(KEY_SESSION_START_MILLIS, sessionStartMillis)
            .apply()
    }

    private fun clearPersistedSessionState() {
        prefs.edit()
            .remove(KEY_ACTIVE_PACKAGE)
            .remove(KEY_SESSION_START_MILLIS)
            .apply()
    }

    private fun ensureStatsForToday() {
        val today = LocalDate.now(zoneId).toString()
        val recordedDay = prefs.getString(KEY_STATS_DATE, null)
        if (recordedDay == today) {
            return
        }

        val editor = prefs.edit()
            .putString(KEY_STATS_DATE, today)
            .putInt(KEY_BLOCKED_SESSIONS, 0)
            .putInt(KEY_DOOM_DETECTIONS, 0)

        prefs.all.keys
            .filter { key ->
                key.startsWith(KEY_BLOCKED_COUNT_PREFIX) || key.startsWith(KEY_BLOCKED_APP_NAME_PREFIX)
            }
            .forEach(editor::remove)

        editor.apply()
    }

    private fun resolveTopBlockedApp(): String {
        val topEntry = prefs.all.entries
            .filter { it.key.startsWith(KEY_BLOCKED_COUNT_PREFIX) }
            .maxByOrNull { (it.value as? Int) ?: 0 }
            ?: return "None"

        val packageName = topEntry.key.removePrefix(KEY_BLOCKED_COUNT_PREFIX)
        val count = (topEntry.value as? Int) ?: 0
        val appName = prefs.getString("$KEY_BLOCKED_APP_NAME_PREFIX$packageName", packageName) ?: packageName
        return if (count > 0) "$appName ($count)" else "None"
    }

    companion object {
        private const val KEY_STATS_DATE = "stats_date"
        private const val KEY_BLOCKED_SESSIONS = "blocked_sessions_today"
        private const val KEY_DOOM_DETECTIONS = "doom_scroll_detections"
        private const val KEY_BLOCKED_COUNT_PREFIX = "blocked_count_"
        private const val KEY_BLOCKED_APP_NAME_PREFIX = "blocked_app_name_"
        private const val KEY_ACTIVE_PACKAGE = "active_package"
        private const val KEY_SESSION_START_MILLIS = "session_start_millis"
        private const val MIN_SESSION_AGE_FOR_WARNING_MS = 10_000L
        private const val WARNING_COOLDOWN_MS = 25_000L
    }
}
