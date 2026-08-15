package com.onevelox.app.ui

import com.onevelox.app.model.AppSettings
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.MainRoadAlert
import com.onevelox.app.model.RoadSide

data class ActiveTutorSegmentUi(
    val dangerId: Long,
    val label: String,
    val maxAverageSpeedKmh: Int,
    val currentAverageSpeedKmh: Int,
    val remainingMeters: Int,
    val enteredRoadName: String
)

data class TutorSegmentResultAlert(
    val label: String,
    val averageSpeedKmh: Int,
    val maxAverageSpeedKmh: Int,
    val compliant: Boolean,
    val shownAtEpochMs: Long
)

data class RecentTutorAverageUi(
    val averageSpeedKmh: Int,
    val maxAverageSpeedKmh: Int,
    val visibleUntilEpochMs: Long
)

data class DriveUiState(
    val speedKmh: Int = 0,
    val moving: Boolean = false,
    val headingDeg: Float = 0f,
    val gpsOk: Boolean = false,
    val internetOk: Boolean = false,
    val databaseOk: Boolean = false,
    val loadedPoiCount: Int = 0,
    val dbSyncInProgress: Boolean = false,
    val dbSyncProgress: Float = 0f,
    val dbSyncStatus: String = "",
    val dbSyncErrorType: String? = null,
    val dbUpdateAvailable: Boolean = false,
    val dataSourceLabel: String = "",
    val currentRoadName: String = "",
    val routeMeters: Int = 0,
    val totalDistanceMeters: Int = 0,
    val nextBranchSide: RoadSide? = null,
    val scenarioName: String = "",
    val simulationPaused: Boolean = false,
    val simulationEnabled: Boolean = false,
    val turnSlowdownDetected: Boolean = false,
    val uncertainJunctionMode: Boolean = false,
    val activeTutorSegment: ActiveTutorSegmentUi? = null,
    val recentTutorAverage: RecentTutorAverageUi? = null,
    val tutorSegmentResultAlert: TutorSegmentResultAlert? = null,
    val mainAlert: MainRoadAlert? = null,
    val lateralAlerts: List<DangerPoint> = emptyList(),
    val allDirectionsAlerts: List<DangerPoint> = emptyList(),
    val settings: AppSettings = AppSettings()
)
