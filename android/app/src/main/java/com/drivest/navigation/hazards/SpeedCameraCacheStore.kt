package com.drivest.navigation.hazards

import android.content.Context
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class SpeedCameraCacheStore(
    private val cacheDir: File,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = DEFAULT_TTL_MS
) {

    constructor(
        context: Context,
        nowProvider: () -> Long = { System.currentTimeMillis() },
        ttlMs: Long = DEFAULT_TTL_MS
    ) : this(
        cacheDir = File(context.filesDir, "speed_camera_cache"),
        nowProvider = nowProvider,
        ttlMs = ttlMs
    )

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun read(
        centreId: String?,
        routePoints: List<Point>
    ): List<OsmFeature> {
        val file = cacheFileFor(centreId, routePoints)
        if (!file.exists()) return emptyList()

        val payload = runCatching { JSONObject(file.readText()) }.getOrNull()
            ?: run {
                file.delete()
                return emptyList()
            }
        val cachedAtMs = payload.optLong(KEY_CACHED_AT_MS, 0L)
        if (cachedAtMs <= 0L || nowProvider() - cachedAtMs > ttlMs) {
            file.delete()
            return emptyList()
        }
        val cameraArray = payload.optJSONArray(KEY_CAMERAS) ?: return emptyList()
        return parseFeatures(cameraArray).filter { it.type == OsmFeatureType.SPEED_CAMERA }
    }

    fun write(
        centreId: String?,
        routePoints: List<Point>,
        features: List<OsmFeature>
    ) {
        val speedCameras = features
            .asSequence()
            .filter { feature ->
                feature.type == OsmFeatureType.SPEED_CAMERA &&
                    feature.lat.isFinite() &&
                    feature.lon.isFinite()
            }
            .distinctBy { feature ->
                buildString {
                    append(feature.id)
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lat))
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lon))
                }
            }
            .toList()
        if (speedCameras.isEmpty()) return

        val file = cacheFileFor(centreId, routePoints)
        val payload = JSONObject().apply {
            put(KEY_CACHED_AT_MS, nowProvider())
            put(KEY_CENTRE_ID, normalizeCentreId(centreId))
            put(KEY_ROUTE_SIGNATURE, routeSignature(routePoints))
            put(KEY_CAMERAS, JSONArray().apply {
                speedCameras.forEach { feature ->
                    put(
                        JSONObject().apply {
                            put("id", feature.id)
                            put("type", feature.type.name)
                            put("lat", feature.lat)
                            put("lon", feature.lon)
                            put("source", feature.source)
                            put("confidenceHint", feature.confidenceHint.toDouble())
                            put("tags", JSONObject(feature.tags))
                        }
                    )
                }
            })
        }
        file.writeText(payload.toString())
    }

    private fun parseFeatures(array: JSONArray): List<OsmFeature> {
        val items = mutableListOf<OsmFeature>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val type = runCatching {
                OsmFeatureType.valueOf(item.optString("type"))
            }.getOrNull() ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue
            val tagsJson = item.optJSONObject("tags")
            val tags = buildMap {
                if (tagsJson != null) {
                    val keys = tagsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, tagsJson.optString(key))
                    }
                }
            }
            items += OsmFeature(
                id = item.optString("id", "camera_$index"),
                type = type,
                lat = lat,
                lon = lon,
                tags = tags,
                source = item.optString("source", "speed_camera_cache"),
                confidenceHint = item.optDouble("confidenceHint", 1.0).toFloat()
            )
        }
        return items
    }

    private fun cacheFileFor(centreId: String?, routePoints: List<Point>): File {
        val centrePart = normalizeCentreId(centreId)
        val routePart = routeSignature(routePoints).hashCode().toUInt().toString(16)
        return File(cacheDir, "${centrePart}_${routePart}.json")
    }

    private fun normalizeCentreId(centreId: String?): String {
        return centreId
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-z0-9._-]"), "_")
            ?.ifBlank { "default" }
            ?: "default"
    }

    private fun routeSignature(routePoints: List<Point>): String {
        if (routePoints.isEmpty()) return "empty"
        val first = routePoints.first()
        val mid = routePoints[routePoints.size / 2]
        val last = routePoints.last()
        return buildString {
            append(routePoints.size)
            append('|')
            append(String.format(Locale.US, "%.5f,%.5f", first.latitude(), first.longitude()))
            append('|')
            append(String.format(Locale.US, "%.5f,%.5f", mid.latitude(), mid.longitude()))
            append('|')
            append(String.format(Locale.US, "%.5f,%.5f", last.latitude(), last.longitude()))
        }
    }

    private companion object {
        const val KEY_CACHED_AT_MS = "cachedAtMs"
        const val KEY_CENTRE_ID = "centreId"
        const val KEY_ROUTE_SIGNATURE = "routeSignature"
        const val KEY_CAMERAS = "cameras"
        const val DEFAULT_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
