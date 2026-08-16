package com.onevelox.app.data

import com.onevelox.app.data.local.DangerDao
import com.onevelox.app.data.local.OneVeloxDatabase
import com.onevelox.app.data.local.toDomain
import com.onevelox.app.model.DangerPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.cos

class DangerRepositoryImpl(
    private val roomDb: OneVeloxDatabase,
    private val catalog: ItaliaCatalogRemote = ItaliaCatalogRemote(),
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
                    source = SOURCE_APK,
                    message = "italia.db non configurato",
                    errorType = "MISSING_SNAPSHOT"
                )
            if (!installer.exists()) {
                return DbRefreshResult(
                    success = false,
                    loadedPoiCount = dao.count(),
                    source = SOURCE_APK,
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
                source = "$SOURCE_APK ${meta.generatedAt}",
                message = "POI precaricati: $finalCount (aggiornamento ${meta.generatedAt})",
                remoteTimestamp = meta.remoteTimestamp,
                updatedRows = finalCount,
                updateAvailable = meta.incomplete
            )
        } catch (t: Throwable) {
            DbRefreshResult(
                success = false,
                loadedPoiCount = dao.count(),
                source = SOURCE_APK,
                message = t.message ?: "Import SQLite fallito",
                errorType = t::class.java.simpleName
            )
        }
    }

    override suspend fun checkPoiUpdates(lastKnownRemoteTimestamp: String?): PoiUpdateCheckResult {
        return try {
            val remote = catalog.fetchMeta()
            val remoteTs = remote.effectiveTimestamp()
            val updateAvailable = ItaliaCatalogRemote.isRemoteNewer(remoteTs, lastKnownRemoteTimestamp)
            PoiUpdateCheckResult(
                updateAvailable = updateAvailable,
                remoteTimestamp = remoteTs,
                message = if (updateAvailable) "Nuovo italia.db disponibile sul catalogo" else "DB POI gia aggiornato"
            )
        } catch (t: Throwable) {
            PoiUpdateCheckResult(
                updateAvailable = false,
                remoteTimestamp = null,
                message = t.message ?: "Impossibile verificare il catalogo GitHub"
            )
        }
    }

    override suspend fun refreshFromCatalog(
        lastKnownRemoteTimestamp: String?,
        force: Boolean,
        onProgress: (DbRefreshProgress) -> Unit
    ): DbRefreshResult {
        return try {
            val localCount = dao.count()
            onProgress(DbRefreshProgress(1, 5, "Verifica catalogo GitHub"))
            val remote = catalog.fetchMeta()
            val remoteTs = remote.effectiveTimestamp()
            val newer = ItaliaCatalogRemote.isRemoteNewer(remoteTs, lastKnownRemoteTimestamp)
            if (!force && !newer && localCount >= 20) {
                return DbRefreshResult(
                    success = true,
                    loadedPoiCount = localCount,
                    source = SOURCE_GITHUB,
                    message = "Nessun aggiornamento: italia.db locale e allineato",
                    remoteTimestamp = lastKnownRemoteTimestamp ?: remoteTs,
                    updateAvailable = false
                )
            }

            onProgress(DbRefreshProgress(2, 5, "Download italia.db dal catalogo"))
            val installer = bundledDb
                ?: return DbRefreshResult(
                    success = false,
                    loadedPoiCount = localCount,
                    source = SOURCE_GITHUB,
                    message = "Importer SQLite non configurato",
                    errorType = "MISSING_SNAPSHOT"
                )
            val cacheDest = installer.cacheFile("italia-catalog.db")
            val downloaded = catalog.downloadDb(cacheDest, remote) { downloadedBytes, totalBytes ->
                val mbDown = downloadedBytes / (1024.0 * 1024.0)
                val mbTotal = if (totalBytes > 0L) totalBytes / (1024.0 * 1024.0) else 0.0
                val label = if (mbTotal > 0.0) {
                    "Download italia.db ${"%.1f".format(mbDown)}/${"%.1f".format(mbTotal)} MB"
                } else {
                    "Download italia.db ${"%.1f".format(mbDown)} MB"
                }
                onProgress(DbRefreshProgress(3, 5, label))
            }

            onProgress(DbRefreshProgress(4, 5, "Importazione POI da catalogo"))
            withContext(Dispatchers.IO) {
                try {
                    installer.installFromFile(roomDb.openHelper.writableDatabase, downloaded)
                    roomDb.invalidationTracker.refreshVersionsAsync()
                } finally {
                    downloaded.delete()
                }
            }
            val finalCount = dao.count()
            onProgress(DbRefreshProgress(5, 5, "Database POI aggiornato"))
            DbRefreshResult(
                success = true,
                loadedPoiCount = finalCount,
                source = "$SOURCE_GITHUB ${remoteTs}",
                message = "POI aggiornati dal catalogo: $finalCount (data $remoteTs)",
                remoteTimestamp = remoteTs,
                updatedRows = finalCount,
                updateAvailable = remote.incomplete
            )
        } catch (t: Throwable) {
            val currentCount = dao.count()
            DbRefreshResult(
                success = false,
                loadedPoiCount = currentCount,
                source = SOURCE_GITHUB,
                message = if (currentCount > 0) {
                    "${t.message ?: "Aggiornamento catalogo fallito"} (dati locali mantenuti)"
                } else {
                    t.message ?: "Aggiornamento catalogo fallito"
                },
                errorType = t::class.java.simpleName,
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
        private const val SOURCE_APK = "APK SQLite"
        private const val SOURCE_GITHUB = "GitHub DBs"
    }
}
