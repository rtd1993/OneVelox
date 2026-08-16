package com.onevelox.app.ui

import android.app.Application
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.onevelox.app.data.DbRefreshResult
import com.onevelox.app.data.DbRefreshProgress
import com.onevelox.app.data.BundledItaliaDb
import com.onevelox.app.data.DangerRepositoryImpl
import com.onevelox.app.data.ItaliaCatalogRemote
import com.onevelox.app.data.SettingsRepository
import com.onevelox.app.data.local.OneVeloxDatabase
import com.onevelox.app.domain.DangerDetectionEngine
import com.onevelox.app.location.RealTelemetryProvider
import com.onevelox.app.location.VehicleTelemetry
import com.onevelox.app.location.VehicleTelemetryProvider
import com.onevelox.app.model.AppSettings
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class DriveViewModel(application: Application) : AndroidViewModel(application) {

    init {
        BundledItaliaDb.deleteLegacyWorkingCopy(application)
    }

    private val db = Room.databaseBuilder(
        application,
        OneVeloxDatabase::class.java,
        BundledItaliaDb.WORKING_DB_NAME
    )
        .fallbackToDestructiveMigration()
        .build()

    private val repository = DangerRepositoryImpl(
        db,
        ItaliaCatalogRemote(),
        BundledItaliaDb(application)
    )
    private val settingsRepository = SettingsRepository(application)
    private val telemetryProvider: VehicleTelemetryProvider = RealTelemetryProvider(application)
    private val engine = DangerDetectionEngine()
    private val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
    private var lastTurnAssistDistanceMeters: Int? = null
    private var activeTutor: ActiveTutor? = null
    private var lastLatitudeDeg: Double? = null
    private var lastLongitudeDeg: Double? = null

    private data class DbSyncRuntime(
        val inProgress: Boolean = false,
        val progress: Float = 0f,
        val status: String = "",
        val errorType: String? = null,
        val updateAvailable: Boolean = false
    )

    private data class CombineFrame(
        val telemetry: VehicleTelemetry,
        val settings: AppSettings,
        val syncRuntime: DbSyncRuntime,
        val poiCount: Int
    )

    private data class ActiveTutor(
        val dangerId: Long,
        val label: String,
        val maxAverageSpeedKmh: Int,
        val enteredRoadName: String,
        val enteredAtDistanceMeters: Int,
        val enteredAtEpochMs: Long,
        val segmentLengthMeters: Int,
        val endLatitudeDeg: Double?,
        val endLongitudeDeg: Double?
    )

    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()
    private val dbSyncRuntime = MutableStateFlow(DbSyncRuntime(status = "DB locale in uso"))
    private var cachedNearby: List<DangerPoint> = emptyList()
    private var cachedNearbyLat: Double? = null
    private var cachedNearbyLon: Double? = null

    init {
        viewModelScope.launch {
            bootstrapPoiSync()
        }

        viewModelScope.launch {
            combine(
                telemetryProvider.telemetryFlow(),
                settingsRepository.settingsFlow,
                dbSyncRuntime,
                repository.observePoiCount()
            ) { telemetry, settings, syncRuntime, poiCount ->
                CombineFrame(telemetry, settings, syncRuntime, poiCount)
            }.collect { frame ->
                val telemetry = frame.telemetry
                val settings = frame.settings
                val syncRuntime = frame.syncRuntime
                val poiCount = frame.poiCount
                lastLatitudeDeg = telemetry.latitudeDeg
                lastLongitudeDeg = telemetry.longitudeDeg
                val dangers = nearbyDangers(telemetry.latitudeDeg, telemetry.longitudeDeg)
                val projectedDangers = projectDangersForLiveLocation(
                    dangers = dangers,
                    latitudeDeg = telemetry.latitudeDeg,
                    longitudeDeg = telemetry.longitudeDeg,
                    headingDeg = telemetry.headingDeg
                )
                val enabledDangers = projectedDangers.filter { settings.isEnabled(it.type) }
                val detection = engine.detect(
                    dangers = enabledDangers,
                    currentSpeedKmh = telemetry.speedKmh,
                    currentHeadingDeg = telemetry.headingDeg,
                    settings = settings
                )
                val slowdownDetected = isTurnSlowdownDetected(
                    previousState = _state.value,
                    speedKmh = telemetry.speedKmh,
                    lateralAlerts = detection.lateralAlerts,
                    nextBranchSide = telemetry.nextBranchSide,
                    isMoving = telemetry.isMoving
                )
                val lateralWithTurnAssist = lateralAlertsForTurnSlowdown(
                    baseLateral = detection.lateralAlerts,
                    allProjectedDangers = enabledDangers,
                    nextBranchSide = telemetry.nextBranchSide,
                    slowdownDetected = slowdownDetected
                )

                val newState = DriveUiState(
                    speedKmh = telemetry.speedKmh,
                    moving = telemetry.isMoving,
                    headingDeg = telemetry.headingDeg,
                    gpsOk = telemetry.gpsSignalOk,
                    internetOk = telemetry.internetOk,
                    databaseOk = poiCount >= 20,
                    loadedPoiCount = poiCount,
                    dbSyncInProgress = syncRuntime.inProgress,
                    dbSyncProgress = syncRuntime.progress,
                    dbSyncStatus = syncRuntime.status,
                    dbSyncErrorType = syncRuntime.errorType,
                    dbUpdateAvailable = syncRuntime.updateAvailable,
                    dataSourceLabel = enabledDangers.firstOrNull()?.sourceDataset
                        ?: projectedDangers.firstOrNull()?.sourceDataset.orEmpty(),
                    currentRoadName = liveRoadLabel(telemetry.currentRoadName),
                    routeMeters = telemetry.routeMeters,
                    totalDistanceMeters = telemetry.totalDistanceMeters,
                    nextBranchSide = telemetry.nextBranchSide,
                    scenarioName = "GPS reale",
                    simulationPaused = telemetry.paused,
                    simulationEnabled = false,
                    turnSlowdownDetected = slowdownDetected,
                    uncertainJunctionMode = detection.uncertainJunctionMode,
                    mainAlert = detection.mainRoadAlert,
                    lateralAlerts = lateralWithTurnAssist,
                    allDirectionsAlerts = detection.allDirectionsAlerts,
                    settings = settings
                )
                val old = _state.value
                val withTutorState = applyTutorTracking(
                    baseState = newState,
                    previousState = old,
                    currentLatitudeDeg = lastLatitudeDeg,
                    currentLongitudeDeg = lastLongitudeDeg
                )
                _state.value = withTutorState
                val nowOver = newState.mainAlert?.overspeed == true
                val wasOver = old.mainAlert?.overspeed == true
                if (nowOver && !wasOver) {
                    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                }
                if (newState.turnSlowdownDetected) {
                    val nearest = newState.lateralAlerts.minByOrNull { it.distanceMeters }
                    val shouldNotify = nearest != null && shouldNotifyTurnAssist(nearest.distanceMeters)
                    if (shouldNotify) {
                        lastTurnAssistDistanceMeters = nearest?.distanceMeters
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                    }
                }
            }
        }
    }

    fun refreshDatabase() {
        viewModelScope.launch {
            val lastKnownTimestamp = settingsRepository.getPoiRemoteTimestamp()
            dbSyncRuntime.value = DbSyncRuntime(
                inProgress = true,
                progress = 0f,
                status = "Verifica aggiornamenti catalogo...",
                errorType = null,
                updateAvailable = false
            )
            val result = repository.refreshFromCatalog(
                lastKnownRemoteTimestamp = lastKnownTimestamp,
                force = false,
                onProgress = { progress ->
                    dbSyncRuntime.value = DbSyncRuntime(
                        inProgress = true,
                        progress = (progress.step.toFloat() / progress.totalSteps.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                        status = progress.message,
                        errorType = null,
                        updateAvailable = false
                    )
                }
            )
            if (result.success && !result.remoteTimestamp.isNullOrBlank()) {
                settingsRepository.setPoiRemoteTimestamp(result.remoteTimestamp)
            }
            settingsRepository.setPoiUpdateAvailable(result.updateAvailable)
            invalidateNearbyCache()
            dbSyncRuntime.value = result.toRuntimeMessage()
        }
    }

    fun setMainDistance(value: Int) {
        viewModelScope.launch { settingsRepository.updateMainDistance(value) }
    }

    fun setLateralDistance(value: Int) {
        viewModelScope.launch { settingsRepository.updateLateralDistance(value) }
    }

    fun setAlertEnabled(type: DangerType, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAlertEnabled(type, enabled) }
    }

    fun setVehicleIconType(value: String) {
        viewModelScope.launch { settingsRepository.updateVehicleIconType(value) }
    }

    fun setVehicleColorName(value: String) {
        viewModelScope.launch { settingsRepository.updateVehicleColorName(value) }
    }

    fun toggleSimulationPaused() {
        // Simulation disabled by design: emulator route simulation is used instead.
    }

    fun nextScenario() {
        // Simulation disabled by design: emulator route simulation is used instead.
    }

    fun resetSimulation() {
        // Simulation disabled by design: emulator route simulation is used instead.
    }

    private fun DbRefreshResult.toRuntimeMessage(): DbSyncRuntime {
        val prefix = if (success) "OK" else "ERRORE"
        val base = "$prefix • $source • POI: $loadedPoiCount • $message"
        val showType = !errorType.isNullOrBlank() && errorType != "CATALOG_UNAVAILABLE"
        val full = if (showType) "$base • tipo: $errorType" else base
        return DbSyncRuntime(
            inProgress = false,
            progress = if (success) 1f else 0f,
            status = full,
            errorType = errorType,
            updateAvailable = updateAvailable
        )
    }

    private suspend fun bootstrapPoiSync() {
        val localCount = repository.localPoiCount()
        if (localCount < 20) {
            dbSyncRuntime.value = DbSyncRuntime(
                inProgress = true,
                progress = 0.1f,
                status = "Caricamento POI preinstallati...",
                errorType = null,
                updateAvailable = false
            )
            val snapshot = repository.installBundledSnapshot { progress ->
                dbSyncRuntime.value = DbSyncRuntime(
                    inProgress = true,
                    progress = (progress.step.toFloat() / progress.totalSteps.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    status = progress.message,
                    errorType = null,
                    updateAvailable = false
                )
            }
            if (snapshot.success && !snapshot.remoteTimestamp.isNullOrBlank()) {
                settingsRepository.setPoiRemoteTimestamp(snapshot.remoteTimestamp)
            }
            invalidateNearbyCache()
            dbSyncRuntime.value = snapshot.toRuntimeMessage()
        } else {
            val lastUpdateAvailable = settingsRepository.getPoiUpdateAvailable()
            dbSyncRuntime.value = DbSyncRuntime(
                inProgress = false,
                progress = 0f,
                status = "DB locale pronto • POI: $localCount",
                errorType = null,
                updateAvailable = lastUpdateAvailable
            )
        }
        refreshDatabase()
    }

    private fun invalidateNearbyCache() {
        cachedNearby = emptyList()
        cachedNearbyLat = null
        cachedNearbyLon = null
    }

    private suspend fun nearbyDangers(latitudeDeg: Double?, longitudeDeg: Double?): List<DangerPoint> {
        if (latitudeDeg == null || longitudeDeg == null) return emptyList()
        val prevLat = cachedNearbyLat
        val prevLon = cachedNearbyLon
        if (prevLat != null && prevLon != null && cachedNearby.isNotEmpty()) {
            val delta = FloatArray(1)
            Location.distanceBetween(prevLat, prevLon, latitudeDeg, longitudeDeg, delta)
            if (delta[0] < REQUERY_MOVE_METERS) return cachedNearby
        }
        val nearby = withContext(Dispatchers.IO) {
            repository.getNearby(latitudeDeg, longitudeDeg, NEARBY_RADIUS_METERS)
        }
        cachedNearby = nearby
        cachedNearbyLat = latitudeDeg
        cachedNearbyLon = longitudeDeg
        return nearby
    }

    private fun projectDangersForLiveLocation(
        dangers: List<DangerPoint>,
        latitudeDeg: Double?,
        longitudeDeg: Double?,
        headingDeg: Float
    ): List<DangerPoint> {
        if (latitudeDeg == null || longitudeDeg == null) return emptyList()

        val out = mutableListOf<DangerPoint>()
        dangers.forEach { danger ->
            val lat = danger.latitudeDeg ?: return@forEach
            val lon = danger.longitudeDeg ?: return@forEach
            val distance = FloatArray(1)
            Location.distanceBetween(latitudeDeg, longitudeDeg, lat, lon, distance)
            val meters = distance[0].toInt().coerceAtLeast(0)

            val bearingToDanger = bearing(latitudeDeg, longitudeDeg, lat, lon)
            val side = sideFromBearing(headingDeg, bearingToDanger)
            out += danger.copy(
                distanceMeters = meters,
                headingDeg = bearingToDanger,
                side = side
            )
        }
        return out
    }

    private fun liveRoadLabel(base: String): String {
        return base
    }

    private fun sideFromBearing(currentHeadingDeg: Float, bearingToDanger: Float): com.onevelox.app.model.RoadSide {
        val delta = normalizeAngle(bearingToDanger - currentHeadingDeg)
        return when {
            kotlin.math.abs(delta) <= 35f -> com.onevelox.app.model.RoadSide.MAIN
            delta < 0f -> com.onevelox.app.model.RoadSide.LEFT
            else -> com.onevelox.app.model.RoadSide.RIGHT
        }
    }

    private fun bearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Float {
        val result = FloatArray(2)
        Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
        return normalizeBearing(result[1])
    }

    private fun normalizeBearing(value: Float): Float {
        var v = value % 360f
        if (v < 0f) v += 360f
        return v
    }

    private fun normalizeAngle(value: Float): Float {
        var v = (value + 540f) % 360f - 180f
        if (v <= -180f) v += 360f
        return v
    }

    private fun isTurnSlowdownDetected(
        previousState: DriveUiState,
        speedKmh: Int,
        lateralAlerts: List<DangerPoint>,
        nextBranchSide: com.onevelox.app.model.RoadSide?,
        isMoving: Boolean
    ): Boolean {
        val nearest = lateralAlerts.minByOrNull { it.distanceMeters } ?: return false
        val sideMatches = nextBranchSide == null || nearest.side == nextBranchSide
        if (!sideMatches) return false
        if (nearest.distanceMeters !in 1..100) return false
        if (!isMoving && speedKmh <= 1) return false
        val speedDrop = previousState.speedKmh - speedKmh
        return speedDrop >= 10 && previousState.speedKmh >= 25
    }

    private fun shouldNotifyTurnAssist(distanceMeters: Int): Boolean {
        val previousDistance = lastTurnAssistDistanceMeters ?: return true
        return abs(previousDistance - distanceMeters) > 20
    }

    private fun lateralAlertsForTurnSlowdown(
        baseLateral: List<DangerPoint>,
        allProjectedDangers: List<DangerPoint>,
        nextBranchSide: com.onevelox.app.model.RoadSide?,
        slowdownDetected: Boolean
    ): List<DangerPoint> {
        if (!slowdownDetected) return baseLateral

        val targetSide = nextBranchSide ?: baseLateral.minByOrNull { it.distanceMeters }?.side ?: return baseLateral
        if (targetSide == com.onevelox.app.model.RoadSide.MAIN) return baseLateral

        val lateralCriticalTypes = setOf(
            DangerType.SPEED_CAMERA,
            DangerType.VELOBOX,
            DangerType.VELOOK,
            DangerType.T_RED
        )

        val hasAlready = baseLateral.any { it.side == targetSide && it.type in lateralCriticalTypes }
        if (hasAlready) return baseLateral

        val promoted = allProjectedDangers
            .asSequence()
            .filter { it.type in lateralCriticalTypes }
            .filter { it.distanceMeters in 1..380 }
            .sortedBy { it.distanceMeters }
            .firstOrNull()
            ?.copy(side = targetSide)

        return if (promoted == null) baseLateral else (baseLateral + promoted).sortedBy { it.distanceMeters }
    }

    private fun applyTutorTracking(
        baseState: DriveUiState,
        previousState: DriveUiState,
        currentLatitudeDeg: Double?,
        currentLongitudeDeg: Double?
    ): DriveUiState {
        val nowMs = System.currentTimeMillis()
        var resultAlert = previousState.tutorSegmentResultAlert
        var recentAverage = previousState.recentTutorAverage

        if (resultAlert != null && nowMs - resultAlert.shownAtEpochMs >= 30_000L) {
            resultAlert = null
        }
        if (recentAverage != null && nowMs >= recentAverage.visibleUntilEpochMs) {
            recentAverage = null
        }

        val active = activeTutor
        if (active == null) {
            val candidate = baseState.mainAlert?.danger
                ?.takeIf { it.type == DangerType.TUTOR && it.distanceMeters <= 120 }
            if (candidate != null) {
                activeTutor = ActiveTutor(
                    dangerId = candidate.id,
                    label = candidate.name,
                    maxAverageSpeedKmh = candidate.allowedSpeedKmh,
                    enteredRoadName = baseState.currentRoadName,
                    enteredAtDistanceMeters = baseState.totalDistanceMeters,
                    enteredAtEpochMs = nowMs,
                    segmentLengthMeters = candidate.segmentLengthMeters ?: 1000,
                    endLatitudeDeg = candidate.segmentEndLatitudeDeg,
                    endLongitudeDeg = candidate.segmentEndLongitudeDeg
                )
            }
            return baseState.copy(
                tutorSegmentResultAlert = resultAlert,
                recentTutorAverage = recentAverage
            )
        }

        val traveledMeters = (baseState.totalDistanceMeters - active.enteredAtDistanceMeters).coerceAtLeast(0)
        val elapsedSeconds = ((nowMs - active.enteredAtEpochMs).coerceAtLeast(1L) / 1000.0)
        val avgSpeed = ((traveledMeters / elapsedSeconds) * 3.6).toInt().coerceAtLeast(0)

        val remainingByDistance = (active.segmentLengthMeters - traveledMeters).coerceAtLeast(0)
        val remainingByEndPoint = estimateRemainingToTutorEnd(
            active = active,
            currentLatitudeDeg = currentLatitudeDeg,
            currentLongitudeDeg = currentLongitudeDeg
        )
        val remainingMeters = minOf(remainingByDistance, remainingByEndPoint ?: Int.MAX_VALUE)

        val leftRoad = baseState.currentRoadName.isNotBlank() &&
            active.enteredRoadName.isNotBlank() &&
            !sameRoadLabel(active.enteredRoadName, baseState.currentRoadName)

        val reachedEnd = remainingMeters <= 20 || traveledMeters >= active.segmentLengthMeters
        if (reachedEnd || leftRoad) {
            val compliant = avgSpeed <= active.maxAverageSpeedKmh
            val finalAlert = TutorSegmentResultAlert(
                label = active.label,
                averageSpeedKmh = avgSpeed,
                maxAverageSpeedKmh = active.maxAverageSpeedKmh,
                compliant = compliant,
                shownAtEpochMs = nowMs
            )
            activeTutor = null
            viewModelScope.launch {
                delay(30_000L)
                val current = _state.value.tutorSegmentResultAlert
                if (current != null && current.shownAtEpochMs == finalAlert.shownAtEpochMs) {
                    _state.value = _state.value.copy(tutorSegmentResultAlert = null)
                }
            }
            return baseState.copy(
                activeTutorSegment = null,
                tutorSegmentResultAlert = finalAlert,
                recentTutorAverage = RecentTutorAverageUi(
                    averageSpeedKmh = avgSpeed,
                    maxAverageSpeedKmh = active.maxAverageSpeedKmh,
                    visibleUntilEpochMs = nowMs + 30_000L
                )
            )
        }

        return baseState.copy(
            activeTutorSegment = ActiveTutorSegmentUi(
                dangerId = active.dangerId,
                label = active.label,
                maxAverageSpeedKmh = active.maxAverageSpeedKmh,
                currentAverageSpeedKmh = avgSpeed,
                remainingMeters = remainingMeters,
                enteredRoadName = active.enteredRoadName
            ),
            tutorSegmentResultAlert = resultAlert,
            recentTutorAverage = RecentTutorAverageUi(
                averageSpeedKmh = avgSpeed,
                maxAverageSpeedKmh = active.maxAverageSpeedKmh,
                visibleUntilEpochMs = nowMs + 30_000L
            )
        )
    }

    private fun estimateRemainingToTutorEnd(
        active: ActiveTutor,
        currentLatitudeDeg: Double?,
        currentLongitudeDeg: Double?
    ): Int? {
        val endLat = active.endLatitudeDeg
        val endLon = active.endLongitudeDeg
        val currentLat = currentLatitudeDeg
        val currentLon = currentLongitudeDeg
        if (endLat == null || endLon == null || currentLat == null || currentLon == null) return null
        val out = FloatArray(1)
        Location.distanceBetween(currentLat, currentLon, endLat, endLon, out)
        return out[0].toInt().coerceAtLeast(0)
    }

    private fun sameRoadLabel(a: String, b: String): Boolean {
        val na = a.trim().lowercase().replace("\\s+".toRegex(), " ")
        val nb = b.trim().lowercase().replace("\\s+".toRegex(), " ")
        if (na == nb) return true
        return na.contains(nb) || nb.contains(na)
    }

    override fun onCleared() {
        tone.release()
        db.close()
        super.onCleared()
    }

    companion object {
        private const val NEARBY_RADIUS_METERS = 3_000.0
        private const val REQUERY_MOVE_METERS = 400f
    }
}
