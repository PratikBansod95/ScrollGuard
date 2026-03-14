package com.scrollguard.utils

import android.content.Context
import android.graphics.drawable.Drawable


data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSuggested: Boolean,
)

object AppUtils {
    fun getLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        return packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { info ->
                info.packageName != context.packageName &&
                    packageManager.getLaunchIntentForPackage(info.packageName) != null &&
                    packageManager.getApplicationLabel(info).isNotBlank() &&
                    !packageManager.isLauncherApp(info.packageName)
            }
            .map { info ->
                val packageName = info.packageName
                val appName = packageManager.getApplicationLabel(info).toString()
                InstalledApp(
                    packageName = packageName,
                    appName = appName,
                    icon = packageManager.getApplicationIcon(info),
                    isSuggested = isLikelyDoomScrollApp(packageName, appName),
                )
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.isSuggested }
                    .thenBy { it.appName.lowercase() },
            )
            .toList()
    }

    private fun isLikelyDoomScrollApp(packageName: String, appName: String): Boolean {
        val value = "${packageName.lowercase()} ${appName.lowercase()}"
        return SUGGESTED_PATTERNS.any(value::contains)
    }

    private fun android.content.pm.PackageManager.isLauncherApp(packageName: String): Boolean {
        val launchIntent = getLaunchIntentForPackage(packageName) ?: return false
        val categories = launchIntent.categories ?: return false
        return categories.contains(android.content.Intent.CATEGORY_HOME)
    }

    private val SUGGESTED_PATTERNS = listOf(
        "instagram",
        "youtube",
        "reddit",
        "facebook",
        "twitter",
        "x corp",
        "tiktok",
        "snapchat",
        "pinterest",
        "linkedin",
        "threads",
        "shorts",
    )
}
