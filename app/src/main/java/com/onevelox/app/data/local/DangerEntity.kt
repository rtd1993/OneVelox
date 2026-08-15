package com.onevelox.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide

@Entity(
    tableName = "danger_points",
    indices = [Index(value = ["latitudeDeg", "longitudeDeg"])]
)
data class DangerEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val type: String,
    val allowedSpeedKmh: Int,
    val distanceMeters: Int,
    val headingDeg: Float,
    val side: String,
    val branchRoadName: String?,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val segmentEndLatitudeDeg: Double?,
    val segmentEndLongitudeDeg: Double?,
    val segmentLengthMeters: Int?,
    val restrictionSchedule: String?,
    val sourceDataset: String
)

fun DangerEntity.toDomain(): DangerPoint = DangerPoint(
    id = id,
    name = name,
    type = DangerType.valueOf(type),
    allowedSpeedKmh = allowedSpeedKmh,
    distanceMeters = distanceMeters,
    headingDeg = headingDeg,
    side = RoadSide.valueOf(side),
    branchRoadName = branchRoadName,
    latitudeDeg = latitudeDeg,
    longitudeDeg = longitudeDeg,
    segmentEndLatitudeDeg = segmentEndLatitudeDeg,
    segmentEndLongitudeDeg = segmentEndLongitudeDeg,
    segmentLengthMeters = segmentLengthMeters,
    restrictionSchedule = restrictionSchedule,
    sourceDataset = sourceDataset
)

fun DangerPoint.toEntity(): DangerEntity = DangerEntity(
    id = id,
    name = name,
    type = type.name,
    allowedSpeedKmh = allowedSpeedKmh,
    distanceMeters = distanceMeters,
    headingDeg = headingDeg,
    side = side.name,
    branchRoadName = branchRoadName,
    latitudeDeg = latitudeDeg,
    longitudeDeg = longitudeDeg,
    segmentEndLatitudeDeg = segmentEndLatitudeDeg,
    segmentEndLongitudeDeg = segmentEndLongitudeDeg,
    segmentLengthMeters = segmentLengthMeters,
    restrictionSchedule = restrictionSchedule,
    sourceDataset = sourceDataset
)
