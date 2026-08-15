package com.onevelox.app.data

import com.onevelox.app.model.DangerPoint
import kotlinx.coroutines.flow.Flow

interface DangerRepository {
    fun observePoiCount(): Flow<Int>
    suspend fun localPoiCount(): Int
    suspend fun getNearby(latitudeDeg: Double, longitudeDeg: Double, radiusMeters: Double): List<DangerPoint>
    suspend fun checkPoiUpdates(lastKnownRemoteTimestamp: String?): PoiUpdateCheckResult
    suspend fun installBundledSnapshot(onProgress: (DbRefreshProgress) -> Unit = {}): DbRefreshResult
    suspend fun refreshFromOsmItaly(
        lastKnownRemoteTimestamp: String?,
        force: Boolean = false,
        onProgress: (DbRefreshProgress) -> Unit = {}
    ): DbRefreshResult
}

data class DbRefreshProgress(
    val step: Int,
    val totalSteps: Int,
    val message: String
)

data class PoiUpdateCheckResult(
    val updateAvailable: Boolean,
    val remoteTimestamp: String?,
    val message: String
)

data class DbRefreshResult(
    val success: Boolean,
    val loadedPoiCount: Int,
    val source: String,
    val message: String,
    val errorType: String? = null,
    val remoteTimestamp: String? = null,
    val updatedRows: Int = 0,
    val removedRows: Int = 0,
    val updateAvailable: Boolean = false
)
