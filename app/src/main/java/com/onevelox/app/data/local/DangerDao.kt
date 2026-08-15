package com.onevelox.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DangerDao {
    @Query("SELECT * FROM danger_points")
    suspend fun getAll(): List<DangerEntity>

    @Query(
        """
        SELECT * FROM danger_points
        WHERE latitudeDeg BETWEEN :minLat AND :maxLat
          AND longitudeDeg BETWEEN :minLon AND :maxLon
        """
    )
    suspend fun getInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<DangerEntity>

    @Query("SELECT COUNT(*) FROM danger_points")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DangerEntity>)

    @Query("DELETE FROM danger_points WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM danger_points")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM danger_points")
    suspend fun count(): Int
}
