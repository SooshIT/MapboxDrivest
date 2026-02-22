package com.drivest.navigation.hazards

import com.drivest.navigation.osm.OsmFeature
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class HazardSpatialIndex(
    private val bucketSizeMeters: Double = DEFAULT_BUCKET_SIZE_METERS
) {

    private val buckets = mutableMapOf<CellKey, MutableList<OsmFeature>>()

    fun rebuild(features: List<OsmFeature>) {
        buckets.clear()
        for (feature in features) {
            val key = toCellKey(feature.lat, feature.lon)
            buckets.getOrPut(key) { mutableListOf() }.add(feature)
        }
    }

    fun queryNearby(
        lat: Double,
        lon: Double,
        radiusMeters: Double
    ): List<OsmFeature> {
        if (buckets.isEmpty()) return emptyList()
        val center = toCellKey(lat, lon)
        val cellRadius = ceil(radiusMeters / bucketSizeMeters).toInt().coerceAtLeast(1)
        val results = mutableListOf<OsmFeature>()

        for (x in (center.x - cellRadius)..(center.x + cellRadius)) {
            for (y in (center.y - cellRadius)..(center.y + cellRadius)) {
                buckets[CellKey(x, y)]?.let { bucket ->
                    results.addAll(bucket)
                }
            }
        }
        return results
    }

    private fun toCellKey(lat: Double, lon: Double): CellKey {
        val xMeters = WEB_MERCATOR_RADIUS_M * Math.toRadians(lon)
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val yMeters = WEB_MERCATOR_RADIUS_M * ln(
            tan(Math.PI / 4.0 + Math.toRadians(latClamped) / 2.0)
        )
        return CellKey(
            x = floor(xMeters / bucketSizeMeters).toInt(),
            y = floor(yMeters / bucketSizeMeters).toInt()
        )
    }

    private data class CellKey(
        val x: Int,
        val y: Int
    )

    private companion object {
        const val WEB_MERCATOR_RADIUS_M = 6_378_137.0
        const val DEFAULT_BUCKET_SIZE_METERS = 200.0
    }
}

