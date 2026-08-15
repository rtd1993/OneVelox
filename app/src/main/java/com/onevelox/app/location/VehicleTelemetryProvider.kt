package com.onevelox.app.location

import com.onevelox.app.model.RoadSide
import kotlinx.coroutines.flow.Flow

data class VehicleTelemetry(
    val speedKmh: Int,
    val headingDeg: Float,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val horizontalAccuracyMeters: Float,
    val isMoving: Boolean,
    val gpsSignalOk: Boolean,
    val internetOk: Boolean,
    val routeMeters: Int,
    val totalDistanceMeters: Int,
    val currentRoadName: String,
    val nextBranchSide: RoadSide?,
    val scenario: DebugSimulationScenario,
    val paused: Boolean
)

interface VehicleTelemetryProvider {
    fun telemetryFlow(): Flow<VehicleTelemetry>
}

interface DebugTelemetryControls {
    fun togglePause()
    fun resetRoute()
    fun nextScenario()
}
