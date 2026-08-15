package com.onevelox.app.location

import com.onevelox.app.model.RoadSide
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedTelemetryProvider : VehicleTelemetryProvider, DebugTelemetryControls {
    private data class Controls(
        val paused: Boolean = false,
        val scenario: DebugSimulationScenario = DebugSimulationScenario.CITY_LOOP
    )

    private val commands = MutableSharedFlow<(Controls) -> Controls>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun togglePause() {
        commands.tryEmit { controls -> controls.copy(paused = !controls.paused) }
    }

    override fun resetRoute() {
        commands.tryEmit { controls -> controls.copy(paused = controls.paused) }
        resetPending = true
    }

    override fun nextScenario() {
        commands.tryEmit { controls -> controls.copy(scenario = controls.scenario.next(), paused = false) }
        resetPending = true
    }

    @Volatile
    private var resetPending = false

    override fun telemetryFlow(): Flow<VehicleTelemetry> = flow {
        var t = 0.0
        var controls = Controls()
        var routeMeters = 0
        var totalDistanceMeters = 0

        while (true) {
            while (true) {
                val update = commands.replayCache.lastOrNull()
                if (update != null) {
                    controls = update(controls)
                    commands.resetReplayCache()
                }
                break
            }

            if (resetPending) {
                routeMeters = 0
                totalDistanceMeters = 0
                t = 0.0
                resetPending = false
            }

            val scenario = controls.scenario
            val rawSpeed = (scenario.baseSpeedKmh + (sin(t) * 25)).toInt()
            val speed = if (controls.paused) 0 else rawSpeed.coerceIn(15, 140)
            val heading = when (scenario) {
                DebugSimulationScenario.CITY_LOOP -> (10 + sin(t / 2) * 10).toFloat()
                DebugSimulationScenario.RING_ROAD -> (25 + sin(t / 3) * 5).toFloat()
                DebugSimulationScenario.ZTL_TEST -> (2 + sin(t / 4) * 15).toFloat()
            }
            val internet = abs(sin(t / 5)) > 0.05
            if (!controls.paused) {
                val metersPerSecond = speed / 3.6
                routeMeters = ((routeMeters + metersPerSecond).toInt()) % scenario.routeLengthMeters
                totalDistanceMeters += metersPerSecond.toInt()
            }
            emit(
                VehicleTelemetry(
                    speedKmh = speed,
                    headingDeg = heading,
                    latitudeDeg = null,
                    longitudeDeg = null,
                    horizontalAccuracyMeters = 3f,
                    isMoving = speed > 2,
                    gpsSignalOk = true,
                    internetOk = internet,
                    routeMeters = routeMeters,
                    totalDistanceMeters = totalDistanceMeters,
                    currentRoadName = scenario.roadName,
                    nextBranchSide = nextBranchSide(scenario, routeMeters),
                    scenario = scenario,
                    paused = controls.paused
                )
            )
            t += 0.25
            delay(1000)
        }
    }

    private fun nextBranchSide(
        scenario: DebugSimulationScenario,
        routeMeters: Int
    ): RoadSide? = when (scenario) {
        DebugSimulationScenario.CITY_LOOP -> when (routeMeters) {
            in 120..210 -> RoadSide.RIGHT
            in 330..430 -> RoadSide.LEFT
            else -> null
        }
        DebugSimulationScenario.RING_ROAD -> when (routeMeters) {
            in 250..340 -> RoadSide.RIGHT
            in 700..830 -> RoadSide.LEFT
            else -> null
        }
        DebugSimulationScenario.ZTL_TEST -> when (routeMeters) {
            in 60..180 -> RoadSide.RIGHT
            else -> null
        }
    }
}
