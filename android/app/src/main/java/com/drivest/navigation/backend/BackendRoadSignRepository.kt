package com.drivest.navigation.backend

import android.util.Log
import com.drivest.navigation.BuildConfig
import com.drivest.navigation.signs.RoadSignFeature
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class BackendRoadSignRepository(
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun loadSignsForRoute(
        routePoints: List<Point>,
        radiusMeters: Int,
        centreId: String?
    ): List<RoadSignFeature> = withContext(Dispatchers.IO) {
        if (routePoints.isEmpty()) return@withContext emptyList()
        if (BackendRateLimitBackoff.isActive()) {
            throw IOException(
                "Signs fetch paused after backend throttling. retryInMs=${BackendRateLimitBackoff.remainingMs()}"
            )
        }

        val bbox = computeExpandedBbox(routePoints, radiusMeters.coerceIn(80, 250))
        val baseUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/signs/route"
        val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Invalid API base URL for route signs.")

        urlBuilder
            .addQueryParameter("south", formatCoord(bbox.south))
            .addQueryParameter("west", formatCoord(bbox.west))
            .addQueryParameter("north", formatCoord(bbox.north))
            .addQueryParameter("east", formatCoord(bbox.east))
        centreId?.trim()?.takeIf { it.isNotBlank() }?.let { safeCentreId ->
            urlBuilder.addQueryParameter("centreId", safeCentreId)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) {
                BackendRateLimitBackoff.record429()
                throw IOException("Route signs fetch throttled (429). Backing off backend calls for 10 minutes.")
            }
            if (!response.isSuccessful) {
                throw IOException("Route signs fetch failed with code=${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val signs = parseSigns(body)
            Log.d(TAG, "Fetched route-scoped signs: ${signs.size}")
            signs
        }
    }

    private fun parseSigns(body: String): List<RoadSignFeature> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val signsArray = root.optJSONArray("signs") ?: return emptyList()
        return buildList {
            for (index in 0 until signsArray.length()) {
                val signJson = signsArray.optJSONObject(index) ?: continue
                val id = signJson.optString("id", "").trim()
                val lat = signJson.optDouble("lat", Double.NaN)
                val lon = signJson.optDouble("lon", Double.NaN)
                if (id.isBlank() || lat.isNaN() || lon.isNaN()) continue

                val values = parseSignValues(signJson)
                val tags = parseTags(signJson.optJSONObject("tags"))
                val source = signJson.optString("source", "backend")

                add(
                    RoadSignFeature(
                        id = id,
                        lat = lat,
                        lon = lon,
                        signValues = values,
                        tags = tags,
                        source = source
                    )
                )
            }
        }
    }

    private fun parseSignValues(signJson: JSONObject): List<String> {
        val values = mutableListOf<String>()
        val valuesArray = signJson.optJSONArray("signValues")
        if (valuesArray != null) {
            for (i in 0 until valuesArray.length()) {
                val value = valuesArray.optString(i, "").trim()
                if (value.isNotBlank()) values += value
            }
        }
        val singleValue = signJson.optString("signValue", "").trim()
        if (singleValue.isNotBlank()) values += singleValue
        return values.distinct()
    }

    private fun parseTags(tagsJson: JSONObject?): Map<String, String> {
        tagsJson ?: return emptyMap()
        val tags = mutableMapOf<String, String>()
        val keys = tagsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next() ?: continue
            tags[key] = tagsJson.optString(key, "")
        }
        return tags
    }

    private fun computeExpandedBbox(
        routePoints: List<Point>,
        radiusMeters: Int
    ): BBox {
        var south = Double.POSITIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        var north = Double.NEGATIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var latSum = 0.0

        for (point in routePoints) {
            val lat = point.latitude()
            val lon = point.longitude()
            south = min(south, lat)
            north = max(north, lat)
            west = min(west, lon)
            east = max(east, lon)
            latSum += lat
        }

        val meanLat = latSum / routePoints.size.toDouble()
        val latDelta = radiusMeters / METERS_PER_DEGREE_LAT
        val lonMetersPerDegree = max(
            abs(METERS_PER_DEGREE_LAT * cos(Math.toRadians(meanLat))),
            MIN_LON_METERS_PER_DEGREE
        )
        val lonDelta = radiusMeters / lonMetersPerDegree

        return BBox(
            south = south - latDelta,
            west = west - lonDelta,
            north = north + latDelta,
            east = east + lonDelta
        )
    }

    private fun formatCoord(value: Double): String = String.format(Locale.US, "%.6f", value)

    private data class BBox(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double
    )

    private companion object {
        const val TAG = "BackendRoadSigns"
        const val METERS_PER_DEGREE_LAT = 111_320.0
        const val MIN_LON_METERS_PER_DEGREE = 10_000.0
    }
}
