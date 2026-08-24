package com.geotime.ar.network

import com.geotime.ar.spatial.SpatialCandidate
import com.geotime.ar.spatial.SpatialSourceType
import com.geotime.ar.spatial.Vector3
import com.geotime.ar.time.TimelineMoment
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class NearbyZone(
    val id: String,
    val name: String,
    val distanceM: Double,
    val radiusM: Double,
)

class GeoTimeApiClient(
    private val baseUrl: String,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    fun loadNearby(
        latitude: Double,
        longitude: Double,
        onResult: (Result<NearbyZone?>) -> Unit,
    ) = executor.execute {
        runCatching {
            val response = get(
                "/geozones/nearby?latitude=$latitude&longitude=$longitude&radius_m=5000&limit=1"
            )
            val zones = JSONArray(response)
            if (zones.length() == 0) null else zones.getJSONObject(0).let {
                NearbyZone(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    distanceM = it.getDouble("distance_m"),
                    radiusM = it.getDouble("radius_m"),
                )
            }
        }.also(onResult)
    }

    fun loadCandidates(
        zoneId: String,
        at: Instant,
        momentWindowMinutes: Int = 1,
        onResult: (Result<List<SpatialCandidate>>) -> Unit,
    ) = executor.execute {
        runCatching {
            val encodedTime = URLEncoder.encode(at.toString(), StandardCharsets.UTF_8.name())
            val response = get(
                "/geozones/$zoneId/content-candidates?at=$encodedTime" +
                    "&moment_window_minutes=$momentWindowMinutes&limit=100"
            )
            val items = JSONArray(response)
            buildList {
                repeat(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    val placement = item.getJSONObject("placement")
                    add(
                        SpatialCandidate(
                            id = item.getString("source_id"),
                            title = item.getJSONObject("content").getString("title"),
                            position = Vector3(
                                placement.getDouble("local_x").toFloat(),
                                placement.getDouble("local_y").toFloat(),
                                placement.getDouble("local_z").toFloat(),
                            ),
                            minDistanceM = placement.getDouble("min_visible_distance_m").toFloat(),
                            maxDistanceM = placement.getDouble("max_visible_distance_m").toFloat(),
                            viewConeDegrees = placement.getDouble("view_cone_degrees").toFloat(),
                            sourceType = when (item.getString("source_type")) {
                                "campaign" -> SpatialSourceType.CAMPAIGN
                                else -> SpatialSourceType.MOMENT
                            },
                        )
                    )
                }
            }
        }.also(onResult)
    }

    fun loadTimeline(
        zoneId: String,
        onResult: (Result<List<TimelineMoment>>) -> Unit,
    ) = executor.execute {
        runCatching {
            val response = get("/geozones/$zoneId/timeline?limit=200")
            val items = JSONArray(response)
            buildList {
                repeat(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    add(
                        TimelineMoment(
                            id = item.getString("id"),
                            title = item.getJSONObject("content").getString("title"),
                            recordedAt = Instant.parse(item.getString("recorded_at")),
                        )
                    )
                }
            }
        }.also(onResult)
    }

    fun close() = executor.shutdownNow()

    private fun get(path: String): String {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                error("HTTP ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
