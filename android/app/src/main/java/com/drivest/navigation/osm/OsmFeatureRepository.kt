package com.drivest.navigation.osm

import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

class OsmFeatureRepository(
    private val overpassClient: OverpassClient,
    private val cache: OsmFeatureCache
) {

    suspend fun getFeaturesForRoute(
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> = withContext(Dispatchers.Default) {
        if (routePoints.isEmpty() || types.isEmpty()) return@withContext emptyList()

        val bbox = computeExpandedBbox(routePoints, radiusMeters)
        val cacheKey = buildCacheKey(bbox, types)

        cache.get(cacheKey)?.let { cached -> return@withContext cached.filter { it.type in types } }

        val query = OverpassQueryBuilders.unionQuery(
            south = bbox.south,
            west = bbox.west,
            north = bbox.north,
            east = bbox.east,
            types = types
        )
        val response = overpassClient.fetch(query)
        val elements = OverpassParser.parse(response)
        val features = OsmFeatureMapper.map(elements)
            .asSequence()
            .filter { it.type in types }
            .distinctBy { it.id }
            .toList()

        cache.put(cacheKey, features)
        features
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
            METERS_PER_DEGREE_LAT * cos(Math.toRadians(meanLat)).let { kotlin.math.abs(it) },
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

    private fun buildCacheKey(bbox: BBox, types: Set<OsmFeatureType>): String {
        val roundedSouth = "%.5f".format(java.util.Locale.US, bbox.south)
        val roundedWest = "%.5f".format(java.util.Locale.US, bbox.west)
        val roundedNorth = "%.5f".format(java.util.Locale.US, bbox.north)
        val roundedEast = "%.5f".format(java.util.Locale.US, bbox.east)
        val typeKey = types.map { it.name }.sorted().joinToString(",")
        return "bbox=$roundedSouth,$roundedWest,$roundedNorth,$roundedEast;types=$typeKey"
    }

    private data class BBox(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double
    )

    private companion object {
        const val METERS_PER_DEGREE_LAT = 111_320.0
        const val MIN_LON_METERS_PER_DEGREE = 10_000.0
    }
}
