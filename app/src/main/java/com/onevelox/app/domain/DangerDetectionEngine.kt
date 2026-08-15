package com.onevelox.app.domain

import com.onevelox.app.model.AppSettings
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DetectionResult
import com.onevelox.app.model.MainRoadAlert
import com.onevelox.app.model.RoadSide

class DangerDetectionEngine {

    fun detect(
        dangers: List<DangerPoint>,
        currentSpeedKmh: Int,
        currentHeadingDeg: Float,
        settings: AppSettings
    ): DetectionResult {
        val mainCandidates = dangers
            .asSequence()
            .filter { it.side == RoadSide.MAIN }
            .filter { it.distanceMeters <= settings.mainRoadAlertDistanceMeters }
            .filter { headingDelta(currentHeadingDeg, it.headingDeg) <= 35f }
            .sortedBy { it.distanceMeters }
            .toList()

        val fallbackMainCandidate = dangers
            .asSequence()
            .filter { it.side == RoadSide.MAIN }
            .filter { it.distanceMeters <= 1200 }
            .filter { headingDelta(currentHeadingDeg, it.headingDeg) <= 45f }
            .sortedBy { it.distanceMeters }
            .firstOrNull()

        val main = (mainCandidates.firstOrNull() ?: fallbackMainCandidate)?.let { danger ->
            val recommended = (danger.allowedSpeedKmh - settings.safetyMarginKmh).coerceAtLeast(20)
            MainRoadAlert(
                danger = danger,
                recommendedSpeedKmh = recommended,
                overspeed = currentSpeedKmh > danger.allowedSpeedKmh
            )
        }

        val lateral = dangers
            .asSequence()
            .filter { it.side != RoadSide.MAIN }
            .filter { it.distanceMeters <= settings.lateralRoadAlertDistanceMeters }
            .sortedBy { it.distanceMeters }
            .toList()

        val upToOneKm = dangers
            .asSequence()
            .filter { it.distanceMeters <= 1000 }
            .sortedBy { it.distanceMeters }
            .toList()

        val nearOneKmLateral = upToOneKm.filter { it.side != RoadSide.MAIN }
        val hasLeft = nearOneKmLateral.any { it.side == RoadSide.LEFT }
        val hasRight = nearOneKmLateral.any { it.side == RoadSide.RIGHT }
        val nearestLateral = nearOneKmLateral.minByOrNull { it.distanceMeters }

        // Heuristic for uncertain direction at road end / T-junction:
        // no clear forward continuation and dangers available on connected branches.
        val uncertainJunctionMode =
            (main == null || main.danger.distanceMeters > 250) &&
                hasLeft &&
                hasRight &&
                nearestLateral != null &&
                nearestLateral.distanceMeters <= 220

        return DetectionResult(
            mainRoadAlert = main,
            lateralAlerts = lateral,
            uncertainJunctionMode = uncertainJunctionMode,
            allDirectionsAlerts = if (uncertainJunctionMode) upToOneKm else emptyList()
        )
    }

    private fun headingDelta(a: Float, b: Float): Float {
        val raw = kotlin.math.abs(a - b) % 360f
        return if (raw > 180f) 360f - raw else raw
    }
}
