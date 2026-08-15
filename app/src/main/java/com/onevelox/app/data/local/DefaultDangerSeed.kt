package com.onevelox.app.data.local

import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide

object DefaultDangerSeed {
    val sample = listOf(
        DangerPoint(1, "SR11 Autovelox", DangerType.SPEED_CAMERA, 70, 320, 10f, RoadSide.MAIN, latitudeDeg = 45.4382, longitudeDeg = 10.9916, sourceDataset = "seed-italia"),
        DangerPoint(2, "Incrocio Viale Roma", DangerType.T_RED, 50, 180, 12f, RoadSide.MAIN, latitudeDeg = 45.4376, longitudeDeg = 10.9986, sourceDataset = "seed-italia"),
        DangerPoint(3, "ZTL Centro", DangerType.ZTL, 30, 90, 8f, RoadSide.RIGHT, "Via Garibaldi", latitudeDeg = 45.4408, longitudeDeg = 10.9962, sourceDataset = "seed-italia"),
        DangerPoint(4, "Autovelox laterale", DangerType.SPEED_CAMERA, 80, 70, 14f, RoadSide.LEFT, "SP2", latitudeDeg = 45.4427, longitudeDeg = 10.9892, sourceDataset = "seed-italia"),
        DangerPoint(
            5,
            "Tutor tangenziale",
            DangerType.TUTOR,
            90,
            250,
            9f,
            RoadSide.MAIN,
            latitudeDeg = 45.4353,
            longitudeDeg = 10.9946,
            segmentEndLatitudeDeg = 45.4305,
            segmentEndLongitudeDeg = 11.0067,
            segmentLengthMeters = 1200,
            sourceDataset = "seed-italia"
        )
    )
}
