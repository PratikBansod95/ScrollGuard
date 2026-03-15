package com.scrollguard

import android.app.Application
import com.scrollguard.data.AppDatabase
import com.scrollguard.data.AppRepository
import com.scrollguard.data.TrackedAppsRepository
import com.scrollguard.logic.DoomScrollDetector
import com.scrollguard.logic.InterventionManager
import com.scrollguard.logic.ScrollEventBuffer
import com.scrollguard.logic.SessionManager

class ScrollGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = AppDatabase.getInstance(application)

    val repository = AppRepository(database.appLimitDao())
    val sessionManager = SessionManager(application, repository)
    val trackedAppsRepository = TrackedAppsRepository()
    val scrollEventBuffer = ScrollEventBuffer()
    val doomScrollDetector = DoomScrollDetector()
    val interventionManager = InterventionManager(application, sessionManager)
}
