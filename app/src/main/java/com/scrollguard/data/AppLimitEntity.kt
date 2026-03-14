package com.scrollguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val timeLimitSeconds: Int,
    val cooldownSeconds: Int,
)
