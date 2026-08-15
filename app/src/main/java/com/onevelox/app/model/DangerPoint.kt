package com.onevelox.app.model

data class DangerPoint(
    val id: Long,
    val name: String,
    val type: DangerType,
    val allowedSpeedKmh: Int,
    val distanceMeters: Int,
    val headingDeg: Float,
    val side: RoadSide,
    val branchRoadName: String? = null,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val segmentEndLatitudeDeg: Double? = null,
    val segmentEndLongitudeDeg: Double? = null,
    val segmentLengthMeters: Int? = null,
    val restrictionSchedule: String? = null,
    val sourceDataset: String = "local-seed"
)
