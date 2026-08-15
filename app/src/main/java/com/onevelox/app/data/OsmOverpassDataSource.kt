package com.onevelox.app.data

import com.onevelox.app.model.DangerPoint
import com.onevelox.app.model.DangerType
import com.onevelox.app.model.RoadSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.math.absoluteValue

class OsmOverpassDataSource {

    data class FetchResult(
        val points: List<DangerPoint>,
        val remoteTimestamp: String?,
        val incomplete: Boolean = false
    )

    private data class GeoTile(
        val id: String,
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double
    )

    private data class TileFetchOutcome(
        val points: List<DangerPoint>,
        val timestamp: String?,
        val error: Throwable?
    )

    suspend fun fetchDatasetTimestamp(): String? = withContext(Dispatchers.IO) {
        val endpoints = overpassEndpoints()
        val timestampQuery = """
[out:json][timeout:25];
node(41.89,12.48,41.90,12.49);
out ids 1;
""".trimIndent()

        for (endpoint in endpoints) {
            val payload = overpassPost(endpoint, timestampQuery) ?: continue
            val trimmed = payload.trim()
            if (trimmed.isBlank() || looksLikeXml(trimmed)) continue
            val root = runCatching { parseRootObject(trimmed) }.getOrNull() ?: continue
            val timestamp = root.optJSONObject("osm3s")?.optString("timestamp_osm_base")?.trim()
            if (!timestamp.isNullOrBlank()) return@withContext timestamp
        }
        null
    }

    suspend fun fetchItalyPoi(
        limit: Int = 200000,
        onProgress: ((DbRefreshProgress) -> Unit)? = null
    ): FetchResult = withContext(Dispatchers.IO) {
        val tiles = italyTiles()
        val endpoints = overpassEndpoints()
        val endpointCursor = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val totalSteps = tiles.size.coerceAtLeast(1)
        val semaphore = Semaphore(4)

        val outcomes = coroutineScope {
            tiles.map { tile ->
                async {
                    semaphore.withPermit {
                        val outcome = fetchTile(tile, endpoints, endpointCursor)
                        val done = completed.incrementAndGet()
                        onProgress?.invoke(
                            DbRefreshProgress(
                                step = done,
                                totalSteps = totalSteps,
                                message = "Tile ${tile.id} (${outcome.points.size} POI)"
                            )
                        )
                        outcome
                    }
                }
            }.awaitAll()
        }

        val unique = outcomes.asSequence()
            .flatMap { it.points.asSequence() }
            .distinctBy { it.id }
            .take(limit)
            .toList()
        val latestTimestamp = outcomes.mapNotNull { it.timestamp }.maxOrNull()
        val incomplete = outcomes.any { it.error != null && it.points.isEmpty() }

        if (unique.isNotEmpty()) {
            return@withContext FetchResult(
                points = unique,
                remoteTimestamp = latestTimestamp,
                incomplete = incomplete
            )
        }

        val bestError = outcomes.firstNotNullOfOrNull { it.error }
        if (bestError != null) {
            val reason = bestError.message.orEmpty().ifBlank { "endpoint in timeout o limitati" }
            throw IllegalStateException("Overpass non disponibile: $reason")
        }
        throw IllegalStateException("Overpass non disponibile: nessun tile ha restituito POI validi")
    }

    private suspend fun fetchTile(
        tile: GeoTile,
        endpoints: List<String>,
        endpointCursor: AtomicInteger
    ): TileFetchOutcome {
        val query = buildTileQuery(tile)
        var lastError: Throwable? = null
        val start = endpointCursor.getAndIncrement()
        repeat(endpoints.size.coerceAtMost(3)) { attempt ->
            val endpoint = endpoints[(start + attempt) % endpoints.size]
            try {
                val payload = overpassPost(endpoint, query)
                    ?: throw IllegalStateException("Nessuna risposta da endpoint")
                val trimmed = payload.trim()
                if (trimmed.isBlank()) throw IllegalStateException("Risposta Overpass vuota")
                if (looksLikeXml(trimmed)) throw IllegalStateException(extractXmlError(trimmed))

                val root = parseRootObject(trimmed)
                val timestamp = root.optJSONObject("osm3s")?.optString("timestamp_osm_base")?.trim()
                    ?.takeIf { it.isNotBlank() }
                val remark = root.optString("remark").takeIf { it.isNotBlank() }
                val parsed = parsePayload(root, 20000)
                if (parsed.isEmpty() && remark != null) {
                    throw IllegalStateException(remark)
                }
                return TileFetchOutcome(points = parsed, timestamp = timestamp, error = null)
            } catch (t: Throwable) {
                lastError = t
                val msg = t.message.orEmpty().lowercase()
                val transient = msg.contains("rate") || msg.contains("quota") ||
                    msg.contains("timeout") || msg.contains("gateway") ||
                    msg.contains("busy") || msg.contains("tempor")
                if (transient) delay(400L * (attempt + 1))
            }
        }
        return TileFetchOutcome(points = emptyList(), timestamp = null, error = lastError)
    }

    private fun overpassEndpoints(): List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.openstreetmap.ru/api/interpreter"
    )

    private fun italyTiles(): List<GeoTile> {
        val southLat = 36.619
        val northLat = 47.092
        val westLon = 6.626
        val eastLon = 18.797
        val latBands = 3
        val lonBands = 4
        val latStep = (northLat - southLat) / latBands
        val lonStep = (eastLon - westLon) / lonBands

        val tiles = mutableListOf<GeoTile>()
        for (row in 0 until latBands) {
            for (col in 0 until lonBands) {
                val tileSouth = southLat + row * latStep
                val tileNorth = if (row == latBands - 1) northLat else tileSouth + latStep
                val tileWest = westLon + col * lonStep
                val tileEast = if (col == lonBands - 1) eastLon else tileWest + lonStep
                tiles += GeoTile(
                    id = "r${row + 1}c${col + 1}",
                    south = tileSouth,
                    west = tileWest,
                    north = tileNorth,
                    east = tileEast
                )
            }
        }
        return tiles
    }

    private fun buildTileQuery(tile: GeoTile): String {
        val bbox = "${tile.south},${tile.west},${tile.north},${tile.east}"
        return """
[out:json][timeout:70];
(
  node["highway"="speed_camera"]($bbox);
  node["man_made"="speed_camera"]($bbox);
  node["enforcement"="maxspeed"]($bbox);
  node["enforcement"="average_speed"]($bbox);
  node["camera:type"="speed"]($bbox);
  node["camera:type"="average_speed"]($bbox);
  node["camera:type"="red_light"]($bbox);
  node["highway"="speed_camera"]["box"="yes"]($bbox);
  node["highway"="traffic_signals"]["camera"="yes"]($bbox);
  node["highway"="traffic_signals"]["enforcement"="maxspeed"]($bbox);
  node["highway"="traffic_signals"]["enforcement"="red_light"]($bbox);
  node["traffic_sign"~"ztl|ZTL",i]($bbox);
  node["access:conditional"~"ztl|ZTL",i]($bbox);
  node["zone:traffic"="limited"]($bbox);
  node["barrier"="checkpoint"]($bbox);
  way["traffic_sign"~"ztl|ZTL",i]($bbox);
  way["access:conditional"~"ztl|ZTL",i]($bbox);
  way["zone:traffic"~"ztl|restricted|limited",i]($bbox);
  way["boundary"="traffic_admin"]($bbox);
  way["boundary"="low_emission_zone"]($bbox);
  node["man_made"="surveillance"]($bbox);
  node["traffic_surveillance"]($bbox);
  node["surveillance:type"="traffic_surveillance"]($bbox);
  node["enforcement"="overtaking"]($bbox);
  node["overtaking"="no"]($bbox);
  node["highway"="busway"]($bbox);
  node["busway"]($bbox);
  node["lanes:bus"]($bbox);
  way["man_made"="surveillance"]($bbox);
  way["traffic_surveillance"]($bbox);
  way["surveillance:type"="traffic_surveillance"]($bbox);
  way["enforcement"="overtaking"]($bbox);
  way["overtaking"="no"]($bbox);
  way["highway"="busway"]($bbox);
  way["busway"]($bbox);
  way["lanes:bus"]($bbox);
);
out center tags;
""".trimIndent()
    }

    private fun parsePayload(root: JSONObject, limit: Int): List<DangerPoint> {
        val elements = parseElements(root)
        val remark = root.optString("remark").takeIf { it.isNotBlank() }
        if (elements.length() == 0 && remark != null) throw IllegalStateException(remark)

        val out = mutableListOf<DangerPoint>()
        for (i in 0 until elements.length()) {
            val element = toJsonObject(elements.opt(i)) ?: continue
            val tags = toJsonObject(element.opt("tags")) ?: JSONObject()
            val latLon = elementLatLon(element) ?: continue
            val type = inferDangerType(tags) ?: continue

            out += DangerPoint(
                id = stableId(element),
                name = inferName(tags, type),
                type = type,
                allowedSpeedKmh = inferSpeedLimit(tags, type),
                distanceMeters = 0,
                headingDeg = 0f,
                side = inferSide(tags),
                branchRoadName = tags.optString("name").takeIf { it.isNotBlank() },
                latitudeDeg = latLon.first,
                longitudeDeg = latLon.second,
                segmentEndLatitudeDeg = null,
                segmentEndLongitudeDeg = null,
                segmentLengthMeters = if (type == DangerType.TUTOR) 1000 else null,
                restrictionSchedule = inferRestrictionSchedule(tags),
                sourceDataset = "OpenStreetMap Overpass Italia"
            )
            if (out.size >= limit) break
        }

        return out.distinctBy { it.id }
    }

    private fun overpassPost(endpoint: String, query: String): String? {
        val conn = (URL(endpoint).openConnection() as? HttpURLConnection) ?: return null
        return runCatching {
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 75000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json,text/plain,application/xml,text/xml,*/*")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("User-Agent", "OneVelox/1.0")
            conn.outputStream.use { os ->
                val body = "data=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
                os.write(body.toByteArray(Charsets.UTF_8))
            }
            val raw = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val encoding = conn.contentEncoding.orEmpty()
            val stream = if (encoding.contains("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            stream.bufferedReader().use(BufferedReader::readText)
        }.getOrNull().also {
            conn.disconnect()
        }
    }

    private fun looksLikeXml(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("<?xml", ignoreCase = true) || trimmed.startsWith("<osm") || trimmed.startsWith("<error")
    }

    private fun extractXmlError(text: String): String {
        val title = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text)?.groupValues?.getOrNull(1)
        val message = Regex("<p>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text)?.groupValues?.getOrNull(1)
        return listOfNotNull(title?.stripTags(), message?.stripTags()).joinToString(" - ").ifBlank {
            "Risposta XML/errore non JSON da Overpass"
        }
    }

    private fun String.stripTags(): String = replace(Regex("<.*?>"), " ").replace("\\s+".toRegex(), " ").trim()

    private fun parseRootObject(payload: String): JSONObject {
        val root = JSONTokener(payload).nextValue()
        return when (root) {
            is JSONObject -> root
            is JSONArray -> JSONObject().put("elements", root)
            is String -> {
                val trimmed = root.trim()
                when {
                    trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }.getOrNull()
                    trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()?.let { JSONObject().put("elements", it) }
                    else -> null
                }
            }
            else -> null
        } ?: throw IllegalStateException("Payload Overpass non interpretabile come JSON valido")
    }

    private fun parseElements(root: JSONObject): JSONArray {
        val raw = root.opt("elements")
        return when (raw) {
            is JSONArray -> raw
            is String -> runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            else -> JSONArray()
        }
    }

    private fun toJsonObject(value: Any?): JSONObject? {
        return when (value) {
            is JSONObject -> value
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("{")) runCatching { JSONObject(trimmed) }.getOrNull() else null
            }
            else -> null
        }
    }

    private fun elementLatLon(element: JSONObject): Pair<Double, Double>? {
        val lat = element.optDouble("lat", Double.NaN)
        val lon = element.optDouble("lon", Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) return lat to lon
        val center = safeObject(element, "center")
        val cLat = center?.optDouble("lat", Double.NaN) ?: Double.NaN
        val cLon = center?.optDouble("lon", Double.NaN) ?: Double.NaN
        if (!cLat.isNaN() && !cLon.isNaN()) return cLat to cLon
        return null
    }

    private fun safeObject(element: JSONObject, key: String): JSONObject? {
        return when (val value = element.opt(key)) {
            is JSONObject -> value
            else -> null
        }
    }

    private fun inferDangerType(tags: JSONObject): DangerType? {
        val text = tags.toString().lowercase()
        val highway = tags.optString("highway", "").lowercase()
        val manMade = tags.optString("man_made", "").lowercase()
        val enforcement = tags.optString("enforcement", "").lowercase()
        val cameraType = tags.optString("camera:type", "").lowercase()
        val camera = tags.optString("camera", "").lowercase()
        val surveillanceType = tags.optString("surveillance:type", "").lowercase()
        val overtaking = tags.optString("overtaking", "").lowercase()
        val trafficSign = tags.optString("traffic_sign", "").lowercase()
        val accessConditional = tags.optString("access:conditional", "").lowercase()
        val zoneTraffic = tags.optString("zone:traffic", "").lowercase()
        val boundary = tags.optString("boundary", "").lowercase()
        val barrier = tags.optString("barrier", "").lowercase()
        val busway = tags.optString("busway", "").lowercase()
        val lanesBus = tags.optString("lanes:bus", "").lowercase()
        val box = tags.optString("box", "").lowercase()

        return when {
            enforcement == "average_speed" || cameraType == "average_speed" || text.contains("tutor") -> DangerType.TUTOR
            box == "yes" || (highway == "speed_camera" && tags.has("operator")) -> DangerType.VELOBOX
            surveillanceType == "traffic_surveillance" || tags.has("traffic_surveillance") || manMade == "surveillance" -> DangerType.VELOOK
            highway == "speed_camera" || manMade == "speed_camera" || enforcement == "maxspeed" || cameraType == "speed" -> DangerType.SPEED_CAMERA
            cameraType == "red_light" || enforcement == "red_light" || (highway == "traffic_signals" && (camera == "yes" || enforcement == "maxspeed")) -> DangerType.T_RED
            enforcement == "overtaking" || overtaking == "no" || text.contains("divieto di sorpasso") -> DangerType.SURVEILLANCE
            highway == "busway" || busway.isNotBlank() || lanesBus.isNotBlank() -> DangerType.BUSWAY
            (boundary == "administrative" && (zoneTraffic.contains("ztl") || zoneTraffic.contains("restricted") || zoneTraffic.contains("limited"))) ||
                (boundary == "traffic_admin" && zoneTraffic.isNotBlank()) -> DangerType.ZONE_AREA
            trafficSign.contains("ztl") || accessConditional.contains("ztl") || boundary == "traffic_admin" || boundary == "low_emission_zone" || zoneTraffic.isNotBlank() || barrier == "checkpoint" -> DangerType.ZTL
            else -> null
        }
    }

    private fun inferName(tags: JSONObject, type: DangerType): String {
        val explicit = tags.optString("name", "").takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return when (type) {
            DangerType.SPEED_CAMERA -> "Autovelox"
            DangerType.VELOBOX -> "VeloBox"
            DangerType.VELOOK -> "VeloOK"
            DangerType.TUTOR -> "Tutor"
            DangerType.T_RED -> "T-Red"
            DangerType.ZTL -> "Varco ZTL"
            DangerType.ZONE_AREA -> "Area controllata"
            DangerType.SURVEILLANCE -> "Sorpassometro"
            DangerType.BUSWAY -> "Corsia preferenziale"
            DangerType.HAZARD -> "Pericolo"
        }
    }

    private fun inferSpeedLimit(tags: JSONObject, type: DangerType): Int {
        val maxSpeedRaw = tags.optString("maxspeed", "")
        val fromTag = maxSpeedRaw.filter { it.isDigit() }.toIntOrNull()?.coerceIn(20, 130)
        if (fromTag != null) return fromTag
        return when (type) {
            DangerType.SPEED_CAMERA -> 70
            DangerType.VELOBOX -> 70
            DangerType.VELOOK -> 70
            DangerType.TUTOR -> 90
            DangerType.T_RED -> 50
            DangerType.ZTL -> 30
            DangerType.ZONE_AREA -> 30
            DangerType.SURVEILLANCE -> 50
            DangerType.BUSWAY -> 30
            DangerType.HAZARD -> 40
        }
    }

    private fun inferSide(tags: JSONObject): RoadSide {
        val side = listOf(
            tags.optString("side", ""),
            tags.optString("placement", ""),
            tags.optString("location", ""),
            tags.optString("direction", "")
        ).joinToString(" ").lowercase()
        return when {
            side.contains("left") -> RoadSide.LEFT
            side.contains("right") -> RoadSide.RIGHT
            else -> RoadSide.MAIN
        }
    }

    private fun inferRestrictionSchedule(tags: JSONObject): String? {
        val values = listOf(
            tags.optString("opening_hours", ""),
            tags.optString("access:conditional", ""),
            tags.optString("motor_vehicle:conditional", ""),
            tags.optString("vehicle:conditional", ""),
            tags.optString("hours", ""),
            tags.optString("restriction:conditional", "")
        ).map { it.trim() }.filter { it.isNotBlank() }
        return values.firstOrNull()
    }

    private fun stableId(element: JSONObject): Long {
        val type = element.optString("type", "node")
        val osmId = element.optLong("id", 0L)
        val prefix = when (type) {
            "node" -> 1_000_000_000_000L
            "way" -> 2_000_000_000_000L
            "relation" -> 3_000_000_000_000L
            else -> 9_000_000_000_000L
        }
        return (prefix + osmId).absoluteValue
    }
}
