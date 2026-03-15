package com.scrollguard.utils

import android.content.Context

object AppSettings {
    const val PREFS_NAME = "scrollguard_ui"
    const val KEY_DOOM_SCROLL_ENABLED = "doom_scroll_enabled"
    const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
    const val KEY_AUTO_START_ENABLED = "auto_start_enabled"

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
}
