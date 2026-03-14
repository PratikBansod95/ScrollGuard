package com.scrollguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY appName ASC")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun getAll(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
