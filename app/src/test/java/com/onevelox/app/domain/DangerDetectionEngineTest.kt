package com.onevelox.app.domain

import com.onevelox.app.model.AppSettings
import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerDetectionEngineTest {

    private val engine = DangerDetectionEngine()

    @Test
    fun detectsMainAlertWithinConfiguredDistance() {
        val dangers = listOf(
            DangerPoint(1, "Autovelox", DangerType.SPEED_CAMERA, 70, 350, 12f, RoadSide.MAIN)
        )

        val result = engine.detect(
            dangers = dangers,
            currentSpeedKmh = 90,
            currentHeadingDeg = 10f,
            settings = AppSettings(mainRoadAlertDistanceMeters = 400)
        )

        assertEquals("Autovelox", result.mainRoadAlert?.danger?.name)
        assertTrue(result.mainRoadAlert?.overspeed == true)
    }

    @Test
    fun filtersOutMainAlertIfTooFar() {
        val dangers = listOf(
            DangerPoint(1, "Autovelox", DangerType.SPEED_CAMERA, 70, 700, 12f, RoadSide.MAIN)
        )

        val result = engine.detect(
            dangers = dangers,
            currentSpeedKmh = 80,
            currentHeadingDeg = 10f,
            settings = AppSettings(mainRoadAlertDistanceMeters = 400)
        )

        assertEquals(null, result.mainRoadAlert)
    }

    @Test
    fun returnsLateralAlertsWithinLateralRange() {
        val dangers = listOf(
            DangerPoint(1, "Via Destra", DangerType.ZTL, 30, 90, 0f, RoadSide.RIGHT),
            DangerPoint(2, "Via Lontana", DangerType.ZTL, 30, 250, 0f, RoadSide.LEFT)
        )

        val result = engine.detect(
            dangers = dangers,
            currentSpeedKmh = 50,
            currentHeadingDeg = 0f,
            settings = AppSettings(lateralRoadAlertDistanceMeters = 100)
        )

        assertEquals(1, result.lateralAlerts.size)
        assertEquals("Via Destra", result.lateralAlerts.first().name)
    }
}
