package com.scrollguard.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.scrollguard.utils.AppSettings
import com.scrollguard.utils.PermissionUtils

class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (!AUTO_START_ACTIONS.contains(action)) {
            return
        }
        if (!AppSettings.isAutoStartEnabled(context)) {
            return
        }
        if (!PermissionUtils.hasUsageAccess(context) ||
            !PermissionUtils.canDrawOverlays(context) ||
            !PermissionUtils.isAccessibilityEnabled(context)
        ) {
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, AppMonitorService::class.java))
    }

    companion object {
        private val AUTO_START_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
