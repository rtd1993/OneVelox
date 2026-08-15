package com.onevelox.app.data

import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.absoluteValue

class ItalyGovernmentOpenDataSource {

    suspend fun fetchDangerPoints(limit: Int = 2000): List<DangerPoint> = withContext(Dispatchers.IO) {
        val resources = fetchCandidateResources()
        if (resources.isEmpty()) return@withContext emptyList()

        val points = mutableListOf<DangerPoint>()
        var sequence = 1L
        resources.forEach { resource ->
            val content = downloadText(resource.url) ?: return@forEach
            val parsed = when {
                resource.format.contains("geojson") || resource.format.contains("json") -> {
                    parseJsonContent(content, resource.datasetTitle)
                }
                resource.format.contains("csv") || resource.format.contains("tsv") || resource.url.endsWith(".csv", true) -> {
                    parseCsvContent(content, resource.datasetTitle)
                }
                else -> emptyList()
            }
            parsed.forEach { p ->
                points += p.copy(id = stableId(p, sequence++))
            }
            if (points.size >= limit) return@withContext points.take(limit)
        }
        points
    }

    private fun fetchCandidateResources(): List<DatasetResource> {
        val query = URLEncoder.encode("autovelox fisso tutor t-red ztl controllo velocita", "UTF-8")
        val url = "https://www.dati.gov.it/api/3/action/package_search?q=$query&rows=30"
        val payload = downloadText(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(payload)
            if (!root.optBoolean("success")) return@runCatching emptyList()
            val result = root.optJSONObject("result") ?: return@runCatching emptyList()
            val packages = result.optJSONArray("results") ?: JSONArray()
            val resources = mutableListOf<DatasetResource>()
            for (i in 0 until packages.length()) {
                val pkg = packages.optJSONObject(i) ?: continue
                val datasetTitle = pkg.optString("title", "dataset-italia")
                val organization = pkg.optJSONObject("organization")?.optString("title", "") ?: ""
                val packageText = (pkg.toString() + " " + datasetTitle).lowercase()
                if (!isDangerDataset(packageText)) continue
                val resourcesArray = pkg.optJSONArray("resources") ?: continue
                for (j in 0 until resourcesArray.length()) {
                    val r = resourcesArray.optJSONObject(j) ?: continue
                    val format = r.optString("format", "").lowercase()
                    val resourceUrl = r.optString("url", "")
                    if (resourceUrl.isBlank()) continue
                    if (!(format.contains("csv") || format.contains("json") || format.contains("geojson") || resourceUrl.endsWith(".csv", true))) {
                        continue
                    }
                    val resourceText = (r.toString() + " " + datasetTitle).lowercase()
                    if (!isDangerDataset(resourceText)) continue
                    val sourceLabel = if (organization.isBlank()) datasetTitle else "$organization - $datasetTitle"
                    resources += DatasetResource(resourceUrl, format, sourceLabel)
                }
            }
            resources.take(6)
        }.getOrElse { emptyList() }
    }

    private fun parseJsonContent(content: String, datasetTitle: String): List<DangerPoint> {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                when {
                    root.has("features") -> parseGeoJsonFeatures(root.optJSONArray("features"), datasetTitle)
                    root.has("result") && root.optJSONObject("result")?.has("records") == true -> {
                        parseGenericJsonArray(root.optJSONObject("result")?.optJSONArray("records"), datasetTitle)
                    }
                    root.has("records") -> parseGenericJsonArray(root.optJSONArray("records"), datasetTitle)
                    else -> emptyList()
                }
            }
            trimmed.startsWith("[") -> parseGenericJsonArray(JSONArray(trimmed), datasetTitle)
            else -> emptyList()
        }
    }

    private fun parseGeoJsonFeatures(features: JSONArray?, datasetTitle: String): List<DangerPoint> {
        if (features == null) return emptyList()
        val out = mutableListOf<DangerPoint>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry")
            val coordinates = geometry?.optJSONArray("coordinates")
            val lon = coordinates?.optDouble(0)
            val lat = coordinates?.optDouble(1)
            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue

            val props = feature.optJSONObject("properties") ?: JSONObject()
            out += dangerFromRow(
                row = props,
                latitude = lat,
                longitude = lon,
                datasetTitle = datasetTitle
            )
        }
        return out
    }

    private fun parseGenericJsonArray(rows: JSONArray?, datasetTitle: String): List<DangerPoint> {
        if (rows == null) return emptyList()
        val out = mutableListOf<DangerPoint>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val coords = extractCoordinatesFromRow(row) ?: continue
            out += dangerFromRow(row, coords.first, coords.second, datasetTitle)
        }
        return out
    }

    private fun parseCsvContent(content: String, datasetTitle: String): List<DangerPoint> {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val separator = when {
            lines.first().count { it == ';' } >= 2 -> ';'
            lines.first().count { it == '\t' } >= 2 -> '\t'
            else -> ','
        }

        val headers = splitCsvLine(lines.first(), separator).map { it.trim().lowercase() }
        val latIndex = indexOfAny(headers, listOf("lat", "latitude", "latitudine", "y"))
        val lonIndex = indexOfAny(headers, listOf("lon", "lng", "longitude", "longitudine", "x"))

        val out = mutableListOf<DangerPoint>()
        for (line in lines.drop(1)) {
            val cells = splitCsvLine(line, separator)

            val row = JSONObject()
            headers.forEachIndexed { i, h ->
                row.put(h, cells.getOrNull(i) ?: "")
            }

            val coords = if (latIndex >= 0 && lonIndex >= 0 && cells.size > maxOf(latIndex, lonIndex)) {
                val lat = cells[latIndex].replace(',', '.').toDoubleOrNull()
                val lon = cells[lonIndex].replace(',', '.').toDoubleOrNull()
                normalizeLatLon(lat, lon)
            } else {
                extractCoordinatesFromRow(row)
            } ?: continue

            out += dangerFromRow(row, coords.first, coords.second, datasetTitle)
        }
        return out
    }

    private fun dangerFromRow(
        row: JSONObject,
        latitude: Double,
        longitude: Double,
        datasetTitle: String
    ): DangerPoint {
        val rowText = row.toString().lowercase()
        val type = when {
            rowText.contains("tutor") || rowText.contains("controllo medio") || rowText.contains("media velocita") -> DangerType.TUTOR
            rowText.contains("ztl") -> DangerType.ZTL
            rowText.contains("t-red") || rowText.contains("semaforo") -> DangerType.T_RED
            rowText.contains("cantiere") || rowText.contains("incidente") || rowText.contains("pericolo") -> DangerType.HAZARD
            else -> DangerType.SPEED_CAMERA
        }

        val speedLimit = findInt(
            row,
            listOf("limite", "limite_velocita", "speed_limit", "kmh", "velocita")
        )?.coerceIn(20, 130) ?: 50

        val segmentLength = findInt(
            row,
            listOf("lunghezza_tratto", "segment_length", "lunghezza", "metri_tratto")
        )?.coerceIn(200, 15000)

        val endLat = findDouble(row, listOf("end_lat", "lat_fine", "fine_lat", "latitudine_fine"))
        val endLon = findDouble(row, listOf("end_lon", "end_lng", "lon_fine", "longitudine_fine"))

        val sideHint = findString(row, listOf("lato", "side", "direzione", "ramo", "svolta"))
        val side = when {
            sideHint?.contains("sin", true) == true || sideHint?.contains("left", true) == true -> RoadSide.LEFT
            sideHint?.contains("des", true) == true || sideHint?.contains("right", true) == true -> RoadSide.RIGHT
            else -> RoadSide.MAIN
        }

        val name = findString(row, listOf("nome", "name", "strada", "via", "descrizione", "comune"))
            ?.takeIf { it.isNotBlank() }
            ?: "Punto monitorato"

        val branch = findString(row, listOf("ramo", "strada_limitrofa", "branch", "svincolo"))
            ?.takeIf { it.isNotBlank() }

        return DangerPoint(
            id = 0L,
            name = name,
            type = type,
            allowedSpeedKmh = speedLimit,
            distanceMeters = 0,
            headingDeg = 0f,
            side = side,
            branchRoadName = branch,
            latitudeDeg = latitude,
            longitudeDeg = longitude,
            segmentEndLatitudeDeg = endLat,
            segmentEndLongitudeDeg = endLon,
            segmentLengthMeters = if (type == DangerType.TUTOR) segmentLength ?: 1000 else null,
            sourceDataset = "dati.gov.it | $datasetTitle"
        )
    }

    private fun stableId(point: DangerPoint, salt: Long): Long {
        val latHash = ((point.latitudeDeg ?: 0.0) * 100000).toLong()
        val lonHash = ((point.longitudeDeg ?: 0.0) * 100000).toLong()
        return (latHash xor lonHash xor salt).absoluteValue + 1
    }

    private fun downloadText(url: String): String? {
        val conn = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            conn.requestMethod = "GET"
            conn.connectTimeout = 9000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/json,text/csv,text/plain,*/*")
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        }.getOrNull().also {
            conn.disconnect()
        }
    }

    private fun findDouble(row: JSONObject, keys: List<String>): Double? {
        val key = row.keys().asSequence().firstOrNull { k -> keys.any { it.equals(k, true) } } ?: return null
        val value = row.opt(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    private fun extractCoordinatesFromRow(row: JSONObject): Pair<Double, Double>? {
        val lat = findDouble(row, listOf("lat", "latitude", "y", "latitudine"))
        val lon = findDouble(row, listOf("lon", "lng", "longitude", "x", "longitudine"))
        val direct = normalizeLatLon(lat, lon)
        if (direct != null) return direct

        val keys = row.keys().asSequence().toList()
        keys.forEach { key ->
            val value = row.opt(key)
            when (value) {
                is String -> {
                    parseLatLonString(value)?.let { return it }
                    parseWktPoint(value)?.let { return it }
                }
                is JSONObject -> {
                    val nestedLat = value.optDouble("lat", Double.NaN)
                    val nestedLon = value.optDouble("lon", Double.NaN)
                    val nestedLng = value.optDouble("lng", Double.NaN)
                    normalizeLatLon(
                        if (nestedLat.isNaN()) null else nestedLat,
                        if (nestedLon.isNaN()) if (nestedLng.isNaN()) null else nestedLng else nestedLon
                    )?.let { return it }
                }
                is JSONArray -> {
                    if (value.length() >= 2) {
                        val a = value.optDouble(0)
                        val b = value.optDouble(1)
                        normalizeLatLon(b, a)?.let { return it }
                        normalizeLatLon(a, b)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun parseLatLonString(text: String): Pair<Double, Double>? {
        val normalized = text.trim().replace(";", ",").replace("  ", " ")
        val pairComma = normalized.split(",")
            .map { it.trim().replace(',', '.') }
            .filter { it.isNotBlank() }
        if (pairComma.size >= 2) {
            val first = pairComma[0].toDoubleOrNull()
            val second = pairComma[1].toDoubleOrNull()
            normalizeLatLon(first, second)?.let { return it }
            normalizeLatLon(second, first)?.let { return it }
        }

        val pairSpace = normalized.split(" ")
            .map { it.trim().replace(',', '.') }
            .filter { it.isNotBlank() }
        if (pairSpace.size >= 2) {
            val first = pairSpace[0].toDoubleOrNull()
            val second = pairSpace[1].toDoubleOrNull()
            normalizeLatLon(first, second)?.let { return it }
            normalizeLatLon(second, first)?.let { return it }
        }
        return null
    }

    private fun parseWktPoint(text: String): Pair<Double, Double>? {
        val regex = Regex("POINT\\s*\\(\\s*([+-]?[0-9]*\\.?[0-9]+)\\s+([+-]?[0-9]*\\.?[0-9]+)\\s*\\)", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null
        val lon = match.groupValues.getOrNull(1)?.toDoubleOrNull()
        val lat = match.groupValues.getOrNull(2)?.toDoubleOrNull()
        return normalizeLatLon(lat, lon)
    }

    private fun normalizeLatLon(lat: Double?, lon: Double?): Pair<Double, Double>? {
        if (lat == null || lon == null) return null
        if (lat in 35.0..48.5 && lon in 6.0..19.0) return lat to lon
        if (lon in 35.0..48.5 && lat in 6.0..19.0) return lon to lat
        if (lat in -90.0..90.0 && lon in -180.0..180.0) return lat to lon
        return null
    }

    private fun findInt(row: JSONObject, keys: List<String>): Int? {
        val key = row.keys().asSequence().firstOrNull { k -> keys.any { it.equals(k, true) } } ?: return null
        val value = row.opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.filter { it.isDigit() }.toIntOrNull()
            else -> null
        }
    }

    private fun findString(row: JSONObject, keys: List<String>): String? {
        val key = row.keys().asSequence().firstOrNull { k -> keys.any { it.equals(k, true) } } ?: return null
        return row.optString(key).takeIf { it.isNotBlank() }
    }

    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        line.forEach { ch ->
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == separator && !inQuotes -> {
                    cells += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        cells += sb.toString().trim()
        return cells
    }

    private fun indexOfAny(headers: List<String>, expected: List<String>): Int {
        return headers.indexOfFirst { h -> expected.any { h.equals(it, true) } }
    }

    private fun isDangerDataset(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("autovelox") ||
            normalized.contains("velox") ||
            normalized.contains("ztl") ||
            normalized.contains("t-red") ||
            normalized.contains("tutor") ||
            normalized.contains("controllo velocita")
    }

    private data class DatasetResource(
        val url: String,
        val format: String,
        val datasetTitle: String
    )
}
