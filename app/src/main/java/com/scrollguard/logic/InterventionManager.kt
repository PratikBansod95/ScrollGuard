package com.scrollguard.logic

import android.content.Context
import androidx.core.content.ContextCompat
import com.scrollguard.services.OverlayCommand
import com.scrollguard.services.OverlayService

class InterventionManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val minGapBetweenInterventionsMs: Long = 25_000L,
) {
    private var lastInterventionAt = 0L
    private var lastInterventionPackage: String? = null

    fun triggerIntervention(
        packageName: String,
        sessionDurationSeconds: Long,
        scrollRate: Double,
        scrollCount: Int,
    ) {
        val now = System.currentTimeMillis()
        if (packageName == lastInterventionPackage && now - lastInterventionAt < minGapBetweenInterventionsMs) {
            return
        }
        lastInterventionAt = now
        lastInterventionPackage = packageName

        sessionManager.recordDoomScrollDetection()

        when (determineInterventionType(sessionDurationSeconds, scrollRate)) {
            InterventionType.SOFT -> showWarning(sessionDurationSeconds, scrollCount)
            InterventionType.MEDIUM -> showTimerOrWarning(sessionDurationSeconds, scrollCount)
            InterventionType.HARD -> showBlockOrWarning(sessionDurationSeconds, scrollCount)
        }
    }

    private fun determineInterventionType(
        sessionDurationSeconds: Long,
        scrollRate: Double,
    ): InterventionType {
        return when {
            sessionDurationSeconds >= 120 && scrollRate >= 1.1 -> InterventionType.HARD
            sessionDurationSeconds >= 60 && scrollRate >= 0.9 -> InterventionType.MEDIUM
            else -> InterventionType.SOFT
        }
    }

    private fun showWarning(sessionDurationSeconds: Long, scrollCount: Int) {
        val message = "SnapOut — You have been scrolling for ${sessionDurationSeconds}s with $scrollCount scrolls. Take a break?"
        dispatchOverlay(
            OverlayCommand.ShowWarning(
                title = "Time for a reset",
                message = message,
            ),
        )
    }

    private fun showTimerOrWarning(sessionDurationSeconds: Long, scrollCount: Int) {
        val session = sessionManager.activeSession.value
        if (session != null && !session.isBlocked && session.totalSeconds > 0) {
            dispatchOverlay(
                OverlayCommand.ShowTimer(
                    appName = session.appName,
                    totalSeconds = session.totalSeconds,
                    remainingSeconds = session.remainingSeconds,
                ),
            )
        } else {
            showWarning(sessionDurationSeconds, scrollCount)
        }
    }

    private fun showBlockOrWarning(sessionDurationSeconds: Long, scrollCount: Int) {
        val session = sessionManager.activeSession.value
        if (session != null) {
            val cooldownSeconds = session.cooldownRemainingSeconds.coerceAtLeast(DEFAULT_COOLDOWN_SECONDS)
            dispatchOverlay(
                OverlayCommand.ShowBlock(
                    appName = session.appName,
                    cooldownSeconds = cooldownSeconds,
                ),
            )
        } else {
            showWarning(sessionDurationSeconds, scrollCount)
        }
    }

    private fun dispatchOverlay(command: OverlayCommand) {
        ContextCompat.startForegroundService(context, OverlayService.createIntent(context, command))
    }

    private enum class InterventionType {
        SOFT,
        MEDIUM,
        HARD,
    }

    companion object {
        private const val DEFAULT_COOLDOWN_SECONDS = 300
    }
}
