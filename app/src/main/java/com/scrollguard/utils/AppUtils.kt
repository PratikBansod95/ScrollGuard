package com.scrollguard.utils

import android.content.Context
import android.graphics.drawable.Drawable


data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
)

object AppUtils {
    fun getLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        return packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { info ->
                info.packageName != context.packageName &&
                    packageManager.getLaunchIntentForPackage(info.packageName) != null &&
                    packageManager.getApplicationLabel(info).isNotBlank()
            }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                )
            }
            .toList()
    }
}
