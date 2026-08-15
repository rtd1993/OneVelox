package com.onevelox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.onevelox.app.R

internal val VehicleColorNames = listOf(
    "Rosso",
    "Verde",
    "Giallo",
    "Blu",
    "Bianco",
    "Rosa",
    "Viola",
    "Arancione"
)

@Composable
internal fun VehicleTopGlyph(
    vehicleType: String,
    colorName: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(vehicleIconRes(vehicleType, colorName)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

internal fun vehicleIconRes(vehicleType: String, colorName: String): Int {
    val type = when (vehicleType.uppercase()) {
        "MOTO" -> "moto"
        "CAMION" -> "camion"
        else -> "auto"
    }
    val color = normalizeVehicleColor(colorName)
    return VehicleIcons[type + "_" + color] ?: R.drawable.ic_vehicle_auto_blu
}

internal fun normalizeVehicleColor(name: String): String {
    val key = name.trim().lowercase()
    return when {
        key.startsWith("rosso") -> "rosso"
        key.startsWith("verde") -> "verde"
        key.startsWith("giallo") -> "giallo"
        key.startsWith("blu") || key.startsWith("ciano") -> "blu"
        key.startsWith("bianco") -> "bianco"
        key.startsWith("rosa") -> "rosa"
        key.startsWith("viola") -> "viola"
        key.startsWith("aranci") -> "arancione"
        else -> "blu"
    }
}

private val VehicleIcons = mapOf(
    "auto_rosso" to R.drawable.ic_vehicle_auto_rosso,
    "auto_verde" to R.drawable.ic_vehicle_auto_verde,
    "auto_giallo" to R.drawable.ic_vehicle_auto_giallo,
    "auto_blu" to R.drawable.ic_vehicle_auto_blu,
    "auto_bianco" to R.drawable.ic_vehicle_auto_bianco,
    "auto_rosa" to R.drawable.ic_vehicle_auto_rosa,
    "auto_viola" to R.drawable.ic_vehicle_auto_viola,
    "auto_arancione" to R.drawable.ic_vehicle_auto_arancione,
    "moto_rosso" to R.drawable.ic_vehicle_moto_rosso,
    "moto_verde" to R.drawable.ic_vehicle_moto_verde,
    "moto_giallo" to R.drawable.ic_vehicle_moto_giallo,
    "moto_blu" to R.drawable.ic_vehicle_moto_blu,
    "moto_bianco" to R.drawable.ic_vehicle_moto_bianco,
    "moto_rosa" to R.drawable.ic_vehicle_moto_rosa,
    "moto_viola" to R.drawable.ic_vehicle_moto_viola,
    "moto_arancione" to R.drawable.ic_vehicle_moto_arancione,
    "camion_rosso" to R.drawable.ic_vehicle_camion_rosso,
    "camion_verde" to R.drawable.ic_vehicle_camion_verde,
    "camion_giallo" to R.drawable.ic_vehicle_camion_giallo,
    "camion_blu" to R.drawable.ic_vehicle_camion_blu,
    "camion_bianco" to R.drawable.ic_vehicle_camion_bianco,
    "camion_rosa" to R.drawable.ic_vehicle_camion_rosa,
    "camion_viola" to R.drawable.ic_vehicle_camion_viola,
    "camion_arancione" to R.drawable.ic_vehicle_camion_arancione
)
