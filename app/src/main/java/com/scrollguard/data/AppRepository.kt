package com.scrollguard.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val dao: AppLimitDao,
) {
    fun observeLimits(): Flow<List<AppLimitEntity>> = dao.observeAll()

    suspend fun getLimits(): List<AppLimitEntity> = dao.getAll()

    suspend fun getLimit(packageName: String): AppLimitEntity? = dao.getByPackageName(packageName)

    suspend fun saveLimit(limit: AppLimitEntity) {
        val existing = dao.getByPackageName(limit.packageName)
        dao.upsert(limit.copy(cooldownEndMillis = existing?.cooldownEndMillis ?: 0L))
    }

    suspend fun updateCooldownEnd(packageName: String, cooldownEndMillis: Long) {
        dao.updateCooldownEnd(packageName, cooldownEndMillis)
    }

    suspend fun removeLimit(packageName: String) = dao.delete(packageName)
}
