package com.drivest.navigation.backend

import android.content.Context
import android.util.Log
import com.drivest.navigation.BuildConfig
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.pack.PackJsonParser
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class BackendHazardRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val packStore = PackStore(context)

    suspend fun loadHazardsForCentre(centreId: String): List<OsmFeature> = withContext(Dispatchers.IO) {
        if (BackendRateLimitBackoff.isActive()) {
            throw IOException(
                "Hazards fetch paused after backend throttling. retryInMs=${BackendRateLimitBackoff.remainingMs()}"
            )
        }
        val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/centres/$centreId/hazards"
        val cachedEtag = packStore.readPackEtag(PackType.HAZARDS, centreId)
        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cachedEtag.isNullOrBlank()) {
                    header("If-None-Match", cachedEtag)
                }
            }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 304) {
                val cachedJson = packStore.readPack(PackType.HAZARDS, centreId)
                    ?: throw IOException("Hazards 304 received but cache is unavailable for $centreId.")
                val cachedPack = PackJsonParser.parseHazardsPack(cachedJson)
                    ?: throw IOException("Hazards 304 received but cached payload is invalid for $centreId.")
                Log.d(TAG, "Hazards not modified, using cached payload for $centreId: ${cachedPack.hazards.size}")
                return@withContext cachedPack.hazards
            }
            if (response.code == 429) {
                BackendRateLimitBackoff.record429()
                throw IOException("Hazards fetch throttled (429). Backing off backend calls for 10 minutes.")
            }
            if (!response.isSuccessful) {
                throw IOException("Hazards fetch failed for $centreId with code=${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val pack = PackJsonParser.parseHazardsPack(body)
                ?: throw IOException("Hazards response parsing failed for $centreId.")
            packStore.writePack(
                packType = PackType.HAZARDS,
                centreId = centreId,
                packJson = body,
                metadata = pack.metadata,
                etag = response.header("ETag")
            )
            Log.d(TAG, "Fetched backend hazards for $centreId: ${pack.hazards.size}")
            pack.hazards
        }
    }

    suspend fun loadHazardsForRoute(
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>,
        centreId: String?
    ): List<OsmFeature> = withContext(Dispatchers.IO) {
        if (routePoints.isEmpty() || types.isEmpty()) return@withContext emptyList()
        if (BackendRateLimitBackoff.isActive()) {
            throw IOException(
                "Hazards fetch paused after backend throttling. retryInMs=${BackendRateLimitBackoff.remainingMs()}"
            )
        }

        val bbox = computeExpandedBbox(routePoints, radiusMeters.coerceIn(80, 250))
        val typesParam = types.map { it.name }.sorted().joinToString(",")
        val baseUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/hazards/route"
        val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Invalid API base URL for route hazards.")

        urlBuilder
            .addQueryParameter("south", formatCoord(bbox.south))
            .addQueryParameter("west", formatCoord(bbox.west))
            .addQueryParameter("north", formatCoord(bbox.north))
            .addQueryParameter("east", formatCoord(bbox.east))
            .addQueryParameter("types", typesParam)
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
                throw IOException("Route hazards fetch throttled (429). Backing off backend calls for 10 minutes.")
            }
            if (!response.isSuccessful) {
                throw IOException("Route hazards fetch failed with code=${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val pack = PackJsonParser.parseHazardsPack(body)
                ?: throw IOException("Route hazards response parsing failed.")
            val filtered = pack.hazards.filter { it.type in types }
            Log.d(TAG, "Fetched route-scoped backend hazards: ${filtered.size}")
            filtered
        }
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
        const val TAG = "BackendHazardRepo"
        const val METERS_PER_DEGREE_LAT = 111_320.0
        const val MIN_LON_METERS_PER_DEGREE = 10_000.0
    }
}
