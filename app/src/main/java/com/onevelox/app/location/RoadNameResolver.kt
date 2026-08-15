package com.onevelox.app.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RoadNameResolver(context: Context) {

    private val geocoder = Geocoder(context, Locale.ITALY)
    private var lastRoadName: String = "Strada non disponibile"
    private var lastLookupMs: Long = 0L

    fun resolveRoadName(latitude: Double, longitude: Double): String {
        val now = System.currentTimeMillis()
        if (now - lastLookupMs < 7000) return lastRoadName
        lastLookupMs = now

        if (!Geocoder.isPresent()) return lastRoadName

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveAsync(latitude, longitude)
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }.getOrNull()?.firstOrNull()?.let { address ->
                address.thoroughfare ?: address.subThoroughfare ?: address.featureName
            }
        }

        if (!resolved.isNullOrBlank()) {
            lastRoadName = resolved
        }
        return lastRoadName
    }

    private fun resolveAsync(latitude: Double, longitude: Double): String? {
        val latch = CountDownLatch(1)
        var name: String? = null

        geocoder.getFromLocation(latitude, longitude, 1) { list ->
            name = list.firstOrNull()?.let { it.thoroughfare ?: it.subThoroughfare ?: it.featureName }
            latch.countDown()
        }

        latch.await(1200, TimeUnit.MILLISECONDS)
        return name
    }
}
