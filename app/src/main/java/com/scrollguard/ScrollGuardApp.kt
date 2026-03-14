package com.scrollguard

import android.app.Application
import com.scrollguard.data.AppDatabase
import com.scrollguard.data.AppRepository
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
}
