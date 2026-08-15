package com.onevelox.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onevelox.app.model.AppSettings
import com.onevelox.app.model.DangerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "onevelox_settings")

class SettingsRepository(private val context: Context) {

    private val mainDistanceKey = intPreferencesKey("main_distance")
    private val lateralDistanceKey = intPreferencesKey("lateral_distance")
    private val safetyMarginKey = intPreferencesKey("safety_margin")
    private val autoveloxEnabledKey = booleanPreferencesKey("alert_autovelox")
    private val veloboxEnabledKey = booleanPreferencesKey("alert_velobox")
    private val velookEnabledKey = booleanPreferencesKey("alert_velook")
    private val tutorEnabledKey = booleanPreferencesKey("alert_tutor")
    private val tRedEnabledKey = booleanPreferencesKey("alert_t_red")
    private val ztlEnabledKey = booleanPreferencesKey("alert_ztl")
    private val zoneAreaEnabledKey = booleanPreferencesKey("alert_zone_area")
    private val surveillanceEnabledKey = booleanPreferencesKey("alert_surveillance")
    private val buswayEnabledKey = booleanPreferencesKey("alert_busway")
    private val hazardEnabledKey = booleanPreferencesKey("alert_hazard")
    private val vehicleIconTypeKey = stringPreferencesKey("vehicle_icon_type")
    private val vehicleColorNameKey = stringPreferencesKey("vehicle_color_name")
    private val poiRemoteTimestampKey = stringPreferencesKey("poi_remote_timestamp")
    private val poiUpdateAvailableKey = booleanPreferencesKey("poi_update_available")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            mainRoadAlertDistanceMeters = prefs[mainDistanceKey] ?: 1000,
            lateralRoadAlertDistanceMeters = prefs[lateralDistanceKey] ?: 250,
            safetyMarginKmh = prefs[safetyMarginKey] ?: 5,
            autoveloxEnabled = prefs[autoveloxEnabledKey] ?: true,
            veloboxEnabled = prefs[veloboxEnabledKey] ?: true,
            velookEnabled = prefs[velookEnabledKey] ?: true,
            tutorEnabled = prefs[tutorEnabledKey] ?: true,
            tRedEnabled = prefs[tRedEnabledKey] ?: true,
            ztlEnabled = prefs[ztlEnabledKey] ?: true,
            zoneAreaEnabled = prefs[zoneAreaEnabledKey] ?: true,
            surveillanceEnabled = prefs[surveillanceEnabledKey] ?: true,
            buswayEnabled = prefs[buswayEnabledKey] ?: true,
            hazardEnabled = prefs[hazardEnabledKey] ?: true,
            vehicleIconType = prefs[vehicleIconTypeKey] ?: "AUTO",
            vehicleColorName = migrateVehicleColor(prefs[vehicleColorNameKey] ?: "Blu")
        )
    }

    suspend fun updateMainDistance(value: Int) {
        context.dataStore.edit { it[mainDistanceKey] = value.coerceIn(300, 2000) }
    }

    suspend fun updateLateralDistance(value: Int) {
        context.dataStore.edit { it[lateralDistanceKey] = value.coerceIn(50, 500) }
    }

    suspend fun updateAlertEnabled(type: DangerType, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (type) {
                DangerType.SPEED_CAMERA -> prefs[autoveloxEnabledKey] = enabled
                DangerType.VELOBOX -> prefs[veloboxEnabledKey] = enabled
                DangerType.VELOOK -> prefs[velookEnabledKey] = enabled
                DangerType.TUTOR -> prefs[tutorEnabledKey] = enabled
                DangerType.T_RED -> prefs[tRedEnabledKey] = enabled
                DangerType.ZTL -> prefs[ztlEnabledKey] = enabled
                DangerType.ZONE_AREA -> prefs[zoneAreaEnabledKey] = enabled
                DangerType.SURVEILLANCE -> prefs[surveillanceEnabledKey] = enabled
                DangerType.BUSWAY -> prefs[buswayEnabledKey] = enabled
                DangerType.HAZARD -> prefs[hazardEnabledKey] = enabled
            }
        }
    }

    suspend fun updateVehicleIconType(value: String) {
        context.dataStore.edit { prefs ->
            prefs[vehicleIconTypeKey] = value
        }
    }

    suspend fun updateVehicleColorName(value: String) {
        context.dataStore.edit { prefs ->
            prefs[vehicleColorNameKey] = value
        }
    }

    suspend fun getPoiRemoteTimestamp(): String? {
        return context.dataStore.data.first()[poiRemoteTimestampKey]
    }

    suspend fun setPoiRemoteTimestamp(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(poiRemoteTimestampKey)
            } else {
                prefs[poiRemoteTimestampKey] = value
            }
        }
    }

    suspend fun getPoiUpdateAvailable(): Boolean {
        return context.dataStore.data.first()[poiUpdateAvailableKey] ?: false
    }

    suspend fun setPoiUpdateAvailable(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[poiUpdateAvailableKey] = value
        }
    }

    private fun migrateVehicleColor(name: String): String {
        val key = name.trim().lowercase()
        return when {
            key.startsWith("rosso") -> "Rosso"
            key.startsWith("verde") -> "Verde"
            key.startsWith("giallo") -> "Giallo"
            key.startsWith("blu") || key.startsWith("ciano") -> "Blu"
            key.startsWith("bianco") -> "Bianco"
            key.startsWith("rosa") -> "Rosa"
            key.startsWith("viola") -> "Viola"
            key.startsWith("aranci") -> "Arancione"
            else -> "Blu"
        }
    }
}
