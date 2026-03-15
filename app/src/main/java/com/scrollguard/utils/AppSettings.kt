package com.scrollguard.utils

import android.content.Context

object AppSettings {
    const val PREFS_NAME = "scrollguard_ui"
    const val KEY_DOOM_SCROLL_ENABLED = "doom_scroll_enabled"

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
}
