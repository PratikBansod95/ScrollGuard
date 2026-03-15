package com.scrollguard.logic

import java.util.ArrayDeque
import kotlin.math.max

class ScrollEventBuffer(
    private val windowMillis: Long = 20_000L,
) {
    private val events = ArrayDeque<Long>()

    fun addScrollEvent(timestamp: Long = System.currentTimeMillis()) {
        events.addLast(timestamp)
        trim(timestamp)
    }

    fun getScrollCount(): Int = events.size

    fun getScrollRate(): Double {
        if (events.isEmpty()) return 0.0
        val windowSeconds = max(1.0, windowMillis / 1000.0)
        return events.size / windowSeconds
    }

    fun reset() {
        events.clear()
    }

    private fun trim(now: Long) {
        while (events.isNotEmpty() && now - events.first() > windowMillis) {
            events.removeFirst()
        }
    }
}
