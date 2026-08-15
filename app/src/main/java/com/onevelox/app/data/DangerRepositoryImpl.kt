package com.onevelox.app.data

import com.onevelox.app.data.local.DangerDao
import com.onevelox.app.data.local.OneVeloxDatabase
import com.onevelox.app.data.local.toDomain
import com.onevelox.app.data.local.toEntity
import com.onevelox.app.model.DangerPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.cos

class DangerRepositoryImpl(
    private val roomDb: OneVeloxDatabase,
    private val osmSource: OsmOverpassDataSource = OsmOverpassDataSource(),
    private val bundledDb: BundledItaliaDb? = null
) : DangerRepository {

    private val dao: DangerDao = roomDb.dangerDao()

    override fun observePoiCount(): Flow<Int> = dao.observeCount()

    override suspend fun localPoiCount(): Int = dao.count()

    override suspend fun getNearby(
        latitudeDeg: Double,
        longitudeDeg: Double,
        radiusMeters: Double
    ): List<DangerPoint> = withContext(Dispatchers.IO) {
        val box = geoBounds(latitudeDeg, longitudeDeg, radiusMeters)
        dao.getInBounds(box.minLat, box.maxLat, box.minLon, box.maxLon).map { it.toDomain() }
    }

    override suspend fun installBundledSnapshot(onProgress: (DbRefreshProgress) -> Unit): DbRefreshResult {
        return try {
            onProgress(DbRefreshProgress(1, 3, "Copia database POI preinstallato"))
            val installer = bundledDb
                ?: return DbRefreshResult(
                    success = false,
                    loadedPoiCount = dao.count(),
                    source = "APK SQLite",
                    message = "italia.db non configurato",
                    errorType = "MISSING_SNAPSHOT"
                )
            if (!installer.exists()) {
                return DbRefreshResult(
                    success = false,
                    loadedPoiCount = dao.count(),
                    source = "APK SQLite",
                    message = "italia.db non presente nell'APK",
                    errorType = "MISSING_SNAPSHOT"
                )
            }
            onProgress(DbRefreshProgress(2, 3, "Importazione POI da SQLite"))
            val meta = withContext(Dispatchers.IO) {
                val imported = installer.installInto(roomDb.openHelper.writableDatabase)
                roomDb.invalidationTracker.refreshVersionsAsync()
                imported
            }
            val finalCount = dao.count()
            onProgress(DbRefreshProgress(3, 3, "Database POI pronto"))
            DbRefreshResult(
                success = true,
                loadedPoiCount = finalCount,
                source = "APK SQLite ${meta.generatedAt}",
                message = "POI precaricati: $finalCount (aggiornamento ${meta.generatedAt})",
                remoteTimestamp = meta.remoteTimestamp,
                updatedRows = finalCount,
                updateAvailable = meta.incomplete
            )
        } catch (t: Throwable) {
            DbRefreshResult(
                success = false,
                loadedPoiCount = dao.count(),
                source = "APK SQLite",
                message = t.message ?: "Import SQLite fallito",
                errorType = t::class.java.simpleName
            )
        }
    }

    override suspend fun checkPoiUpdates(lastKnownRemoteTimestamp: String?): PoiUpdateCheckResult {
        val remoteTimestamp = osmSource.fetchDatasetTimestamp()
        if (remoteTimestamp.isNullOrBlank()) {
            return PoiUpdateCheckResult(
                updateAvailable = false,
                remoteTimestamp = null,
                message = "Impossibile verificare aggiornamenti OSM ora"
            )
        }
        val updateAvailable = lastKnownRemoteTimestamp.isNullOrBlank() || remoteTimestamp != lastKnownRemoteTimestamp
        return PoiUpdateCheckResult(
            updateAvailable = updateAvailable,
            remoteTimestamp = remoteTimestamp,
            message = if (updateAvailable) "Aggiornamento POI disponibile" else "DB POI gia aggiornato"
        )
    }

    override suspend fun refreshFromOsmItaly(
        lastKnownRemoteTimestamp: String?,
        force: Boolean,
        onProgress: (DbRefreshProgress) -> Unit
    ): DbRefreshResult {
        return try {
            val localCount = dao.count()
            val updateCheck = checkPoiUpdates(lastKnownRemoteTimestamp)
            if (!force && !updateCheck.updateAvailable && localCount >= 20) {
                return DbRefreshResult(
                    success = true,
                    loadedPoiCount = localCount,
                    source = "OpenStreetMap Overpass",
                    message = "Nessuna variazione remota: uso cache locale",
                    remoteTimestamp = updateCheck.remoteTimestamp,
                    updateAvailable = false
                )
            }

            onProgress(DbRefreshProgress(1, 7, "Verifica variazioni completata"))
            onProgress(DbRefreshProgress(2, 7, "Download POI in corso (dataset nazionale > 10K)"))

            val fetched = osmSource.fetchItalyPoi { progress ->
                val mappedStep = (2 + (progress.step * 4 / progress.totalSteps.coerceAtLeast(1))).coerceIn(2, 6)
                onProgress(
                    DbRefreshProgress(
                        step = mappedStep,
                        totalSteps = 7,
                        message = progress.message
                    )
                )
            }
            val unique = fetched.points.distinctBy { it.id }
            if (unique.isEmpty()) {
                DbRefreshResult(
                    success = false,
                    loadedPoiCount = dao.count(),
                    source = "OpenStreetMap Overpass",
                    message = "Nessun POI ricevuto da OSM Overpass",
                    errorType = "EMPTY_DATASET",
                    remoteTimestamp = fetched.remoteTimestamp,
                    updateAvailable = true
                )
            } else {
                onProgress(DbRefreshProgress(6, 7, "Calcolo differenze locali/remoto"))
                val localMap = dao.getAll().associateBy { it.id }
                val remoteEntities = unique.map { it.toEntity() }

                val changedOrNew = remoteEntities.filter { remote ->
                    val local = localMap[remote.id]
                    local == null || local != remote
                }
                val remoteIds = remoteEntities.asSequence().map { it.id }.toSet()
                val removedIds = if (fetched.incomplete) {
                    emptyList()
                } else {
                    localMap.keys.filter { it !in remoteIds }
                }

                if (changedOrNew.isNotEmpty()) {
                    changedOrNew.chunked(400).forEach { dao.upsertAll(it) }
                }
                if (removedIds.isNotEmpty()) {
                    removedIds.chunked(400).forEach { dao.deleteByIds(it) }
                }

                onProgress(DbRefreshProgress(7, 7, "DB locale aggiornato"))

                val finalCount = dao.count()
                val incompleteNote = if (fetched.incomplete) " (sync parziale, nessun POI rimosso)" else ""
                DbRefreshResult(
                    success = true,
                    loadedPoiCount = finalCount,
                    source = "OpenStreetMap Overpass",
                    message = "POI sincronizzati: ${finalCount} totali (+${changedOrNew.size} aggiornati, -${removedIds.size} rimossi)$incompleteNote",
                    remoteTimestamp = fetched.remoteTimestamp ?: updateCheck.remoteTimestamp,
                    updatedRows = changedOrNew.size,
                    removedRows = removedIds.size,
                    updateAvailable = fetched.incomplete
                )
            }
        } catch (t: Throwable) {
            val currentCount = dao.count()
            val normalized = when (t) {
                is IllegalStateException -> "Servizio OpenStreetMap momentaneamente non disponibile o risposta non valida"
                else -> t.message ?: "Errore sconosciuto durante refresh OSM"
            }
            val errorCode = when (t) {
                is IllegalStateException -> "OVERPASS_RESPONSE"
                else -> t::class.java.simpleName
            }
            DbRefreshResult(
                success = false,
                loadedPoiCount = currentCount,
                source = "OpenStreetMap Overpass",
                message = if (currentCount > 0) "$normalized (dati locali mantenuti)" else normalized,
                errorType = errorCode,
                updateAvailable = currentCount < 20
            )
        }
    }

    private data class GeoBounds(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private fun geoBounds(latitudeDeg: Double, longitudeDeg: Double, radiusMeters: Double): GeoBounds {
        val dLat = radiusMeters / METERS_PER_DEG_LAT
        val cosLat = cos(Math.toRadians(latitudeDeg)).coerceAtLeast(0.01)
        val dLon = radiusMeters / (METERS_PER_DEG_LAT * cosLat)
        return GeoBounds(
            minLat = latitudeDeg - dLat,
            maxLat = latitudeDeg + dLat,
            minLon = longitudeDeg - dLon,
            maxLon = longitudeDeg + dLon
        )
    }

    companion object {
        private const val METERS_PER_DEG_LAT = 111_320.0
    }
}
