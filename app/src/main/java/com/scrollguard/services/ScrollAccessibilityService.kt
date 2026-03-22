package com.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.scrollguard.ScrollGuardApp
import com.scrollguard.utils.AppSettings

class ScrollAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val trackedAppsRepository by lazy { (application as ScrollGuardApp).container.trackedAppsRepository }
    private val scrollEventBuffer by lazy { (application as ScrollGuardApp).container.scrollEventBuffer }
    private val doomScrollDetector by lazy { (application as ScrollGuardApp).container.doomScrollDetector }
    private val interventionManager by lazy { (application as ScrollGuardApp).container.interventionManager }
    private val sessionManager by lazy { (application as ScrollGuardApp).container.sessionManager }

    private lateinit var prefs: SharedPreferences
    private var doomScrollEnabled = true
    private var activePackageName: String? = null
    private var lastScrollEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
        doomScrollEnabled = prefs.getBoolean(AppSettings.KEY_DOOM_SCROLL_ENABLED, true)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScrollEvent(event)
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        if (!AppSettings.shouldBlockNow(this)) {
            sessionManager.endSession()
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (packageName == activePackageName) return

        activePackageName = packageName
        if (trackedAppsRepository.isTrackedApp(packageName)) {
            sessionManager.startSession(packageName)
        } else {
            sessionManager.endSession()
        }
        scrollEventBuffer.reset()
    }

    private fun handleScrollEvent(event: AccessibilityEvent) {
        if (!doomScrollEnabled) return
        if (!AppSettings.shouldBlockNow(this)) return

        val packageName = event.packageName?.toString() ?: return
        if (!trackedAppsRepository.isTrackedApp(packageName)) return

        if (sessionManager.getActiveSessionPackage() != packageName) {
            sessionManager.startSession(packageName)
        }

        val now = System.currentTimeMillis()
        if (now - lastScrollEventAt < MIN_SCROLL_EVENT_INTERVAL_MS) {
            return
        }
        lastScrollEventAt = now

        scrollEventBuffer.addScrollEvent(now)
        val scrollCount = scrollEventBuffer.getScrollCount()
        val scrollRate = scrollEventBuffer.getScrollRate()
        val sessionDurationSeconds = sessionManager.getSessionDurationSeconds()

        if (doomScrollDetector.checkForDoomScroll(scrollCount, scrollRate, sessionDurationSeconds, packageName)) {
            interventionManager.triggerIntervention(
                packageName = packageName,
                sessionDurationSeconds = sessionDurationSeconds,
                scrollRate = scrollRate,
                scrollCount = scrollCount,
            )
            scrollEventBuffer.reset()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Toast.makeText(
            this,
            "SnapOut accessibility monitoring was turned off.",
            Toast.LENGTH_LONG,
        ).show()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (this::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == AppSettings.KEY_DOOM_SCROLL_ENABLED) {
            doomScrollEnabled = sharedPreferences.getBoolean(AppSettings.KEY_DOOM_SCROLL_ENABLED, true)
        }
    }

    companion object {
        private const val MIN_SCROLL_EVENT_INTERVAL_MS = 100L
    }
}

