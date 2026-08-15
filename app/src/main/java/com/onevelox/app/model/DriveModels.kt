package com.onevelox.app.model

import com.onevelox.app.model.DangerType.BUSWAY
import com.onevelox.app.model.DangerType.SPEED_CAMERA
import com.onevelox.app.model.DangerType.SURVEILLANCE
import com.onevelox.app.model.DangerType.TUTOR
import com.onevelox.app.model.DangerType.T_RED
import com.onevelox.app.model.DangerType.VELOBOX
import com.onevelox.app.model.DangerType.VELOOK
import com.onevelox.app.model.DangerType.ZONE_AREA
import com.onevelox.app.model.DangerType.ZTL

data class VehicleState(
    val speedKmh: Int,
    val headingDeg: Float,
    val gpsSignalOk: Boolean,
    val internetOk: Boolean,
    val databaseOk: Boolean
)

data class AppSettings(
    val mainRoadAlertDistanceMeters: Int = 1000,
    val lateralRoadAlertDistanceMeters: Int = 250,
    val safetyMarginKmh: Int = 5,
    val autoveloxEnabled: Boolean = true,
    val veloboxEnabled: Boolean = true,
    val velookEnabled: Boolean = true,
    val tutorEnabled: Boolean = true,
    val tRedEnabled: Boolean = true,
    val ztlEnabled: Boolean = true,
    val zoneAreaEnabled: Boolean = true,
    val surveillanceEnabled: Boolean = true,
    val buswayEnabled: Boolean = true,
    val hazardEnabled: Boolean = true,
    val vehicleIconType: String = "AUTO",
    val vehicleColorName: String = "Blu"
) {
    fun isEnabled(type: DangerType): Boolean = when (type) {
        SPEED_CAMERA -> autoveloxEnabled
        VELOBOX -> veloboxEnabled
        VELOOK -> velookEnabled
        TUTOR -> tutorEnabled
        T_RED -> tRedEnabled
        ZTL -> ztlEnabled
        ZONE_AREA -> zoneAreaEnabled
        SURVEILLANCE -> surveillanceEnabled
        BUSWAY -> buswayEnabled
        DangerType.HAZARD -> hazardEnabled
    }
}

data class MainRoadAlert(
    val danger: DangerPoint,
    val recommendedSpeedKmh: Int,
    val overspeed: Boolean
)

data class DetectionResult(
    val mainRoadAlert: MainRoadAlert?,
    val lateralAlerts: List<DangerPoint>,
    val uncertainJunctionMode: Boolean,
    val allDirectionsAlerts: List<DangerPoint>
)
