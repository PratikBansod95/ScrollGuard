package com.scrollguard.logic

import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque
import kotlin.math.abs

class ScrollDetector(
    private val threshold: Int = 8,
    private val windowMillis: Long = 18_000L,
    private val minIntervalMillis: Long = 900L,
) {
    private val timestamps = ArrayDeque<Long>()
    private var lastRecordedAt = 0L
    private var lastFromIndex = -1
    private var lastToIndex = -1
    private var lastScrollY = Int.MIN_VALUE
    private var lastItemCount = -1

    fun recordScroll(event: AccessibilityEvent, timestamp: Long = System.currentTimeMillis()): Boolean {
        if (!isMeaningfulScroll(event, timestamp)) {
            return false
        }

        timestamps.addLast(timestamp)
        trim(timestamp)
        lastRecordedAt = timestamp
        lastFromIndex = event.fromIndex
        lastToIndex = event.toIndex
        lastScrollY = event.scrollY
        lastItemCount = event.itemCount
        return timestamps.size >= threshold
    }

    fun reset() {
        timestamps.clear()
        lastRecordedAt = 0L
        lastFromIndex = -1
        lastToIndex = -1
        lastScrollY = Int.MIN_VALUE
        lastItemCount = -1
    }

    private fun isMeaningfulScroll(event: AccessibilityEvent, timestamp: Long): Boolean {
        if (timestamp - lastRecordedAt < minIntervalMillis) {
            return false
        }

        val indexJump = maxOf(
            abs(event.fromIndex - lastFromIndex),
            abs(event.toIndex - lastToIndex),
        )
        val scrollJump = if (event.scrollY >= 0 && lastScrollY != Int.MIN_VALUE) {
            abs(event.scrollY - lastScrollY)
        } else {
            0
        }
        val itemCountChanged = event.itemCount > 0 && event.itemCount != lastItemCount

        return indexJump >= 2 || scrollJump >= 180 || (lastRecordedAt == 0L && itemCountChanged)
    }

    private fun trim(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
    }
}
