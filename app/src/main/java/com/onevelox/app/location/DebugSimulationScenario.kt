package com.onevelox.app.location

enum class DebugSimulationScenario(
    val label: String,
    val routeLengthMeters: Int,
    val roadName: String,
    val baseSpeedKmh: Int
) {
    CITY_LOOP("City Loop", 900, "SR11 - Anello urbano", 55),
    RING_ROAD("Ring Road", 1400, "Tangenziale Est", 85),
    ZTL_TEST("ZTL Test", 650, "Centro Storico", 35);

    fun next(): DebugSimulationScenario = entries[(ordinal + 1) % entries.size]
}