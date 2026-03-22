package com.scrollguard.utils

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AppSettings {
    const val PREFS_NAME = "scrollguard_ui"
    const val KEY_DOOM_SCROLL_ENABLED = "doom_scroll_enabled"
    const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
    const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
    const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    const val KEY_SCHEDULE_START_MINUTES = "schedule_start_minutes"
    const val KEY_SCHEDULE_END_MINUTES = "schedule_end_minutes"
    const val KEY_SCHEDULE_DAY_MASK = "schedule_day_mask"
    const val KEY_QUIET_UNTIL_MILLIS = "quiet_until_millis"
    const val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"

    fun isDoomScrollEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOOM_SCROLL_ENABLED, true)
    }

    fun setDoomScrollEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DOOM_SCROLL_ENABLED, enabled)
            .apply()
    }

    fun isDarkModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE_ENABLED, false)
    }

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE_ENABLED, enabled)
            .apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START_ENABLED, true)
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START_ENABLED, enabled)
            .apply()
    }

    fun getBlockSchedule(context: Context): BlockSchedule {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        val startMinutes = prefs.getInt(KEY_SCHEDULE_START_MINUTES, DEFAULT_SCHEDULE_START_MINUTES)
        val endMinutes = prefs.getInt(KEY_SCHEDULE_END_MINUTES, DEFAULT_SCHEDULE_END_MINUTES)
        val dayMask = prefs.getInt(KEY_SCHEDULE_DAY_MASK, DEFAULT_SCHEDULE_DAY_MASK)
        return BlockSchedule(
            enabled = enabled,
            startMinutes = startMinutes.coerceIn(0, MINUTES_PER_DAY - 1),
            endMinutes = endMinutes.coerceIn(0, MINUTES_PER_DAY - 1),
            activeDays = decodeDayMask(dayMask),
        )
    }

    fun setBlockSchedule(context: Context, schedule: BlockSchedule) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCHEDULE_ENABLED, schedule.enabled)
            .putInt(KEY_SCHEDULE_START_MINUTES, schedule.startMinutes.coerceIn(0, MINUTES_PER_DAY - 1))
            .putInt(KEY_SCHEDULE_END_MINUTES, schedule.endMinutes.coerceIn(0, MINUTES_PER_DAY - 1))
            .putInt(KEY_SCHEDULE_DAY_MASK, encodeDayMask(schedule.activeDays))
            .apply()
    }

    fun shouldBlockNow(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isQuietModeActive(context, nowMillis)) {
            return false
        }
        val schedule = getBlockSchedule(context)
        if (!schedule.enabled) {
            return true
        }
        return isWithinSchedule(schedule, nowMillis)
    }

    fun setQuietMode(context: Context, untilMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_QUIET_UNTIL_MILLIS, untilMillis)
            .apply()
    }

    fun clearQuietMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_QUIET_UNTIL_MILLIS, 0L)
            .apply()
    }

    fun getQuietUntilMillis(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_QUIET_UNTIL_MILLIS, 0L)
    }

    fun isQuietModeActive(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = getQuietUntilMillis(context)
        return until > nowMillis
    }

    fun dismissOnboarding(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_DISMISSED, true)
            .apply()
    }

    fun isOnboardingDismissed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DISMISSED, false)
    }

    fun nextScheduleWindow(
        schedule: BlockSchedule,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduleWindow? {
        if (!schedule.enabled || schedule.activeDays.isEmpty()) {
            return null
        }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDateTime()
        val maxDays = 8
        for (dayOffset in 0 until maxDays) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val day = date.dayOfWeek
            if (!schedule.activeDays.contains(day)) {
                continue
            }
            val start = LocalDateTime.of(date, minutesToTime(schedule.startMinutes))
            val end = LocalDateTime.of(date, minutesToTime(schedule.endMinutes))
            if (schedule.startMinutes == schedule.endMinutes) {
                val fullDayStart = start
                val fullDayEnd = start.plusDays(1)
                if (now.isBefore(fullDayEnd)) {
                    return ScheduleWindow(fullDayStart, fullDayEnd)
                }
                continue
            }
            if (schedule.startMinutes < schedule.endMinutes) {
                val windowStart = start
                val windowEnd = end
                if (now.isBefore(windowEnd)) {
                    return ScheduleWindow(windowStart, windowEnd)
                }
            } else {
                val windowStart = start
                val windowEnd = end.plusDays(1)
                if (now.isBefore(windowEnd)) {
                    return ScheduleWindow(windowStart, windowEnd)
                }
            }
        }
        return null
    }

    private fun isWithinSchedule(schedule: BlockSchedule, nowMillis: Long): Boolean {
        if (schedule.activeDays.isEmpty()) {
            return false
        }
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val nowDay = zoned.dayOfWeek
        val nowMinutes = zoned.hour * 60 + zoned.minute
        val startMinutes = schedule.startMinutes
        val endMinutes = schedule.endMinutes

        if (startMinutes == endMinutes) {
            return schedule.activeDays.contains(nowDay)
        }

        if (startMinutes < endMinutes) {
            return schedule.activeDays.contains(nowDay) && nowMinutes in startMinutes until endMinutes
        }

        val previousDay = nowDay.minus(1)
        return (schedule.activeDays.contains(nowDay) && nowMinutes >= startMinutes) ||
            (schedule.activeDays.contains(previousDay) && nowMinutes < endMinutes)
    }

    private fun encodeDayMask(days: Set<DayOfWeek>): Int {
        var mask = 0
        WEEK_DAYS.forEachIndexed { index, day ->
            if (days.contains(day)) {
                mask = mask or (1 shl index)
            }
        }
        return mask
    }

    private fun decodeDayMask(mask: Int): Set<DayOfWeek> {
        return WEEK_DAYS.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 }.toSet()
    }

    private fun minutesToTime(minutes: Int): LocalTime {
        val safeMinutes = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
        return LocalTime.of(safeMinutes / 60, safeMinutes % 60)
    }

    private val WEEK_DAYS = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

    const val DEFAULT_SCHEDULE_START_MINUTES = 11 * 60
    const val DEFAULT_SCHEDULE_END_MINUTES = 17 * 60
    val DEFAULT_SCHEDULE_DAYS = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
    const val DEFAULT_SCHEDULE_DAY_MASK = 0b00011111
    private const val MINUTES_PER_DAY = 24 * 60
}

data class BlockSchedule(
    val enabled: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val activeDays: Set<DayOfWeek>,
)

data class ScheduleWindow(
    val start: LocalDateTime,
    val end: LocalDateTime,
)
