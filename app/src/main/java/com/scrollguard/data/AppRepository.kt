package com.scrollguard.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val dao: AppLimitDao,
) {
    fun observeLimits(): Flow<List<AppLimitEntity>> = dao.observeAll()

    suspend fun getLimits(): List<AppLimitEntity> = dao.getAll()

    suspend fun getLimit(packageName: String): AppLimitEntity? = dao.getByPackageName(packageName)

    suspend fun saveLimit(limit: AppLimitEntity) = dao.upsert(limit)

    suspend fun removeLimit(packageName: String) = dao.delete(packageName)
}
