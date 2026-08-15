package com.onevelox.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

class RealTelemetryProvider(
    private val context: Context
) : VehicleTelemetryProvider {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val roadNameResolver = RoadNameResolver(context)

    override fun telemetryFlow(): Flow<VehicleTelemetry> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(emptyTelemetry(gpsOk = false))
            awaitClose { }
            return@callbackFlow
        }

        var totalDistanceMeters = 0
        var lastLocation: Location? = null
        var lastSpeedKmh = 0.0
        var lastHeadingDeg = 0f

        val listener = LocationListener { location ->
            val previous = lastLocation
            val rawDeltaDistanceMeters = if (previous != null) {
                previous.distanceTo(location).coerceAtLeast(0f)
            } else {
                0f
            }
            val deltaSeconds = if (previous != null) {
                max((location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0, 0.0)
            } else {
                0.0
            }
            val reliableDistanceSample = isReliableDistanceSample(
                previous = previous,
                current = location,
                deltaDistanceMeters = rawDeltaDistanceMeters,
                deltaSeconds = deltaSeconds
            )
            val deltaDistanceMeters = if (reliableDistanceSample) rawDeltaDistanceMeters else 0f

            val speedFromProvider = if (location.hasSpeed() && location.speed >= 0.3f && location.accuracy <= 30f) {
                location.speed * 3.6
            } else {
                null
            }
            val speedFromDistance = if (reliableDistanceSample && deltaSeconds >= 0.7 && deltaDistanceMeters >= 2f) {
                (deltaDistanceMeters / deltaSeconds) * 3.6
            } else {
                null
            }
            val rawSpeedKmh = speedFromProvider ?: speedFromDistance ?: lastSpeedKmh
            val filteredSpeedKmh = if (lastSpeedKmh == 0.0) {
                rawSpeedKmh.coerceAtMost(160.0)
            } else {
                val riseLimit = max(8.0, deltaSeconds * 18.0)
                val dropLimit = max(12.0, deltaSeconds * 30.0)
                val capped = rawSpeedKmh.coerceIn(
                    (lastSpeedKmh - dropLimit).coerceAtLeast(0.0),
                    lastSpeedKmh + riseLimit
                )
                if (capped >= lastSpeedKmh) {
                    (lastSpeedKmh * 0.78) + (capped * 0.22)
                } else {
                    (lastSpeedKmh * 0.55) + (capped * 0.45)
                }
            }
            val moving = filteredSpeedKmh >= 3.0 || (deltaDistanceMeters >= 5f && deltaSeconds <= 3.5)
            val stableSpeedKmh = if (moving) filteredSpeedKmh else 0.0
            val displaySpeedKmh = if (stableSpeedKmh > 30.0) stableSpeedKmh + 5.0 else stableSpeedKmh
            val heading = when {
                location.hasBearing() && moving -> location.bearing
                previous != null && deltaDistanceMeters >= 2f -> previous.bearingTo(location)
                else -> lastHeadingDeg
            }

            if (previous != null) {
                totalDistanceMeters += deltaDistanceMeters.roundToInt().coerceAtLeast(0)
            }
            lastLocation = location
            lastSpeedKmh = stableSpeedKmh
            lastHeadingDeg = heading
            val currentRoadName = if (hasInternet()) {
                roadNameResolver.resolveRoadName(location.latitude, location.longitude)
            } else {
                "Offline: strada non risolta"
            }
            trySend(location.toTelemetry(totalDistanceMeters, displaySpeedKmh, heading, moving, currentRoadName))
        }

        trySend(emptyTelemetry(gpsOk = isGpsEnabled()))
        requestUpdates(listener)

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(listener: LocationListener) {
        val providers = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> listOf(LocationManager.GPS_PROVIDER)
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> listOf(LocationManager.NETWORK_PROVIDER)
            else -> emptyList()
        }

        if (providers.isEmpty()) {
            return
        }

        providers.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                700L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            locationManager.getLastKnownLocation(provider)?.let { lastKnown ->
                listener.onLocationChanged(lastKnown)
            }
        }
    }

    private fun isReliableDistanceSample(
        previous: Location?,
        current: Location,
        deltaDistanceMeters: Float,
        deltaSeconds: Double
    ): Boolean {
        if (previous == null) return false
        if (deltaSeconds <= 0.0 || deltaSeconds > 4.0) return false
        if (current.provider != LocationManager.GPS_PROVIDER || previous.provider != LocationManager.GPS_PROVIDER) return false
        if (previous.accuracy > 25f || current.accuracy > 25f) return false
        val accuracyEnvelope = previous.accuracy + current.accuracy + 6f
        if (deltaDistanceMeters <= accuracyEnvelope * 0.35f) return false
        val inferredSpeedKmh = (deltaDistanceMeters / deltaSeconds) * 3.6
        return inferredSpeedKmh in 1.0..220.0
    }

    private fun Location.toTelemetry(
        totalDistanceMeters: Int,
        stableSpeedKmh: Double,
        headingDeg: Float,
        moving: Boolean,
        currentRoadName: String
    ): VehicleTelemetry {
        return VehicleTelemetry(
            speedKmh = stableSpeedKmh.roundToInt().coerceAtLeast(0),
            headingDeg = headingDeg,
            latitudeDeg = latitude,
            longitudeDeg = longitude,
            horizontalAccuracyMeters = accuracy,
            isMoving = moving,
            gpsSignalOk = accuracy <= 45f || provider == LocationManager.GPS_PROVIDER,
            internetOk = hasInternet(),
            routeMeters = totalDistanceMeters,
            totalDistanceMeters = totalDistanceMeters,
            currentRoadName = currentRoadName,
            nextBranchSide = null,
            scenario = DebugSimulationScenario.CITY_LOOP,
            paused = false
        )
    }

    private fun emptyTelemetry(gpsOk: Boolean): VehicleTelemetry = VehicleTelemetry(
        speedKmh = 0,
        headingDeg = 0f,
        latitudeDeg = null,
        longitudeDeg = null,
        horizontalAccuracyMeters = 999f,
        isMoving = false,
        gpsSignalOk = gpsOk,
        internetOk = hasInternet(),
        routeMeters = 0,
        totalDistanceMeters = 0,
        currentRoadName = if (gpsOk) "In attesa posizione" else "Permesso GPS richiesto",
        nextBranchSide = null,
        scenario = DebugSimulationScenario.CITY_LOOP,
        paused = false
    )

    private fun hasInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun isGpsEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}