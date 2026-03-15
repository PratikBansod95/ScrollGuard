package com.scrollguard.logic

class DoomScrollDetector(
    private val minSessionSeconds: Long = 25,
    private val minScrollCount: Int = 12,
    private val minScrollRatePerSecond: Double = 0.6,
) {
    fun checkForDoomScroll(
        scrollCount: Int,
        scrollRate: Double,
        sessionDurationSeconds: Long,
        packageName: String,
    ): Boolean {
        return sessionDurationSeconds > minSessionSeconds &&
            scrollCount > minScrollCount &&
            scrollRate > minScrollRatePerSecond
    }
}
