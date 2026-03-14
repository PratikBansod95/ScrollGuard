package com.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.scrollguard.ScrollGuardApp
import com.scrollguard.logic.ScrollDetector

class ScrollAccessibilityService : AccessibilityService() {
    private val scrollDetector = ScrollDetector()
    private var lastPackageName: String? = null
    private var lastAnalysisAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val packageName = event.packageName?.toString() ?: return

        if (packageName != lastPackageName) {
            scrollDetector.reset()
            lastPackageName = packageName
        }

        val analysis = scrollDetector.recordScroll(event) ?: return
        if (analysis.isDoomScroll) {
            val now = System.currentTimeMillis()
            if (now - lastAnalysisAt < MIN_DETECTION_GAP_MS) {
                return
            }
            lastAnalysisAt = now
            val sessionManager = (application as ScrollGuardApp).container.sessionManager
            sessionManager.handleScrollThreshold(
                packageName = packageName,
                scrollCount = analysis.scrollCount,
                averageIntervalMillis = analysis.averageIntervalMillis,
                sustainedWindowMillis = analysis.windowMillis,
            )
            scrollDetector.reset()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Toast.makeText(
            this,
            "ScrollGuard accessibility monitoring was turned off.",
            Toast.LENGTH_LONG,
        ).show()
        return super.onUnbind(intent)
    }

    companion object {
        private const val MIN_DETECTION_GAP_MS = 12_000L
    }
}
