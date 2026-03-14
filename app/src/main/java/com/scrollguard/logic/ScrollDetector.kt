package com.scrollguard.logic

import java.util.ArrayDeque

class ScrollDetector(
    private val threshold: Int = 15,
    private val windowMillis: Long = 20_000L,
) {
    private val timestamps = ArrayDeque<Long>()

    fun recordScroll(timestamp: Long = System.currentTimeMillis()): Boolean {
        timestamps.addLast(timestamp)
        trim(timestamp)
        return timestamps.size > threshold
    }

    fun reset() {
        timestamps.clear()
    }

    private fun trim(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
    }
}
