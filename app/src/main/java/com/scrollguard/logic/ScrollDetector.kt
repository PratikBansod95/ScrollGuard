package com.scrollguard.logic

import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque
import kotlin.math.abs

data class ScrollAnalysis(
    val scrollCount: Int,
    val windowMillis: Long,
    val averageIntervalMillis: Long,
    val movementScore: Int,
    val dominantDirectionRatio: Float,
    val isDoomScroll: Boolean,
)

class ScrollDetector(
    private val threshold: Int = 8,
    private val windowMillis: Long = 18_000L,
    private val minIntervalMillis: Long = 900L,
    private val maxAverageIntervalMillis: Long = 2_000L,
    private val resetAfterIdleMillis: Long = 4_500L,
    private val minSustainedWindowMillis: Long = 6_000L,
    private val minimumMovementScore: Int = 18,
    private val minimumDominantDirectionRatio: Float = 0.72f,
) {
    private val samples = ArrayDeque<ScrollSample>()
    private var lastRecordedAt = 0L
    private var lastFromIndex = -1
    private var lastToIndex = -1
    private var lastScrollY = Int.MIN_VALUE
    private var lastItemCount = -1

    fun recordScroll(event: AccessibilityEvent, timestamp: Long = System.currentTimeMillis()): ScrollAnalysis? {
        val sample = createSample(event, timestamp) ?: return null

        if (lastRecordedAt != 0L && timestamp - lastRecordedAt > resetAfterIdleMillis) {
            samples.clear()
        }

        if (!isMeaningfulScroll(event, timestamp, sample)) {
            return null
        }

        samples.addLast(sample)
        trim(timestamp)
        lastRecordedAt = timestamp
        lastFromIndex = event.fromIndex
        lastToIndex = event.toIndex
        lastScrollY = event.scrollY
        lastItemCount = event.itemCount

        val span = if (samples.size > 1) samples.last().timestamp - samples.first().timestamp else 0L
        val averageInterval = if (samples.size > 1) span / (samples.size - 1) else 0L
        val movementScore = samples.sumOf { it.movementScore }
        val downwardMoves = samples.count { it.direction == ScrollDirection.Down }
        val upwardMoves = samples.count { it.direction == ScrollDirection.Up }
        val directionalMoves = downwardMoves + upwardMoves
        val dominantDirectionRatio = if (directionalMoves == 0) {
            0f
        } else {
            maxOf(downwardMoves, upwardMoves).toFloat() / directionalMoves.toFloat()
        }
        val isDoomScroll = samples.size >= threshold &&
            span >= minSustainedWindowMillis &&
            averageInterval in 1..maxAverageIntervalMillis &&
            movementScore >= minimumMovementScore &&
            dominantDirectionRatio >= minimumDominantDirectionRatio

        return ScrollAnalysis(
            scrollCount = samples.size,
            windowMillis = span,
            averageIntervalMillis = averageInterval,
            movementScore = movementScore,
            dominantDirectionRatio = dominantDirectionRatio,
            isDoomScroll = isDoomScroll,
        )
    }

    fun reset() {
        samples.clear()
        lastRecordedAt = 0L
        lastFromIndex = -1
        lastToIndex = -1
        lastScrollY = Int.MIN_VALUE
        lastItemCount = -1
    }

    private fun createSample(event: AccessibilityEvent, timestamp: Long): ScrollSample? {
        val indexDelta = when {
            lastFromIndex == -1 || event.fromIndex == -1 -> 0
            else -> event.fromIndex - lastFromIndex
        }
        val toIndexDelta = when {
            lastToIndex == -1 || event.toIndex == -1 -> 0
            else -> event.toIndex - lastToIndex
        }
        val direction = when {
            indexDelta > 0 || toIndexDelta > 0 -> ScrollDirection.Down
            indexDelta < 0 || toIndexDelta < 0 -> ScrollDirection.Up
            event.scrollY >= 0 && lastScrollY != Int.MIN_VALUE && event.scrollY > lastScrollY -> ScrollDirection.Down
            event.scrollY >= 0 && lastScrollY != Int.MIN_VALUE && event.scrollY < lastScrollY -> ScrollDirection.Up
            else -> ScrollDirection.Unknown
        }
        val indexJump = maxOf(abs(indexDelta), abs(toIndexDelta))
        val scrollJump = if (event.scrollY >= 0 && lastScrollY != Int.MIN_VALUE) {
            abs(event.scrollY - lastScrollY)
        } else {
            0
        }
        val movementScore = maxOf(indexJump / 2, scrollJump / 220)
        return ScrollSample(
            timestamp = timestamp,
            direction = direction,
            movementScore = movementScore,
        )
    }

    private fun isMeaningfulScroll(
        event: AccessibilityEvent,
        timestamp: Long,
        sample: ScrollSample,
    ): Boolean {
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

        val isFirstSample = lastRecordedAt == 0L
        return sample.movementScore >= 1 ||
            indexJump >= 3 ||
            scrollJump >= 220 ||
            (isFirstSample && itemCountChanged)
    }

    private fun trim(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().timestamp > windowMillis) {
            samples.removeFirst()
        }
    }

    private data class ScrollSample(
        val timestamp: Long,
        val direction: ScrollDirection,
        val movementScore: Int,
    )

    private enum class ScrollDirection {
        Up,
        Down,
        Unknown,
    }
}
