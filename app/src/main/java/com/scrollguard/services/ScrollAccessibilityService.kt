package com.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.scrollguard.ScrollGuardApp
import com.scrollguard.logic.ScrollDetector

class ScrollAccessibilityService : AccessibilityService() {
    private val scrollDetector = ScrollDetector()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val packageName = event.packageName?.toString() ?: return
        if (scrollDetector.recordScroll(event)) {
            val sessionManager = (application as ScrollGuardApp).container.sessionManager
            sessionManager.handleScrollThreshold(packageName)
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
}
