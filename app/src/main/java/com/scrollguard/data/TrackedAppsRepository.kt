package com.scrollguard.data

class TrackedAppsRepository(
    defaultApps: Set<String> = DEFAULT_TRACKED_APPS,
) {
    private var trackedApps: Set<String> = defaultApps

    fun isTrackedApp(packageName: String): Boolean {
        return trackedApps.contains(packageName)
    }

    fun setTrackedApps(apps: Set<String>) {
        trackedApps = apps
    }

    companion object {
        val DEFAULT_TRACKED_APPS = setOf(
            "com.instagram.android",
            "com.reddit.frontpage",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
        )
    }
}
