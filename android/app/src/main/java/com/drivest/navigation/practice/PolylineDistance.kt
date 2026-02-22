package com.drivest.navigation.practice

import com.mapbox.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot

object PolylineDistance {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun minimumDistanceMeters(point: Point, polyline: List<Point>): Double {
        if (polyline.isEmpty()) return Double.POSITIVE_INFINITY
        if (polyline.size == 1) {
            return haversineDistanceMeters(point, polyline.first())
        }

        val referenceLatRad = Math.toRadians(point.latitude())
        val px = projectedX(point.longitude(), referenceLatRad)
        val py = projectedY(point.latitude())
        var minDistance = Double.POSITIVE_INFINITY

        for (index in 0 until polyline.lastIndex) {
            val start = polyline[index]
            val end = polyline[index + 1]
            val ax = projectedX(start.longitude(), referenceLatRad)
            val ay = projectedY(start.latitude())
            val bx = projectedX(end.longitude(), referenceLatRad)
            val by = projectedY(end.latitude())
            val candidateDistance = pointToSegmentDistanceMeters(px, py, ax, ay, bx, by)
            if (candidateDistance < minDistance) {
                minDistance = candidateDistance
            }
        }
        return minDistance
    }

    private fun pointToSegmentDistanceMeters(
        px: Double,
        py: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double
    ): Double {
        val segmentDx = bx - ax
        val segmentDy = by - ay
        val segmentLengthSquared = (segmentDx * segmentDx) + (segmentDy * segmentDy)
        if (segmentLengthSquared <= 0.0) {
            return hypot(px - ax, py - ay)
        }
        val projectionRatio = (((px - ax) * segmentDx) + ((py - ay) * segmentDy)) / segmentLengthSquared
        val clampedRatio = projectionRatio.coerceIn(0.0, 1.0)
        val closestX = ax + (clampedRatio * segmentDx)
        val closestY = ay + (clampedRatio * segmentDy)
        return hypot(px - closestX, py - closestY)
    }

    private fun projectedX(longitude: Double, referenceLatRad: Double): Double {
        return EARTH_RADIUS_METERS * Math.toRadians(longitude) * cos(referenceLatRad)
    }

    private fun projectedY(latitude: Double): Double {
        return EARTH_RADIUS_METERS * Math.toRadians(latitude)
    }

    private fun haversineDistanceMeters(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val sinLat = kotlin.math.sin(dLat / 2.0)
        val sinLon = kotlin.math.sin(dLon / 2.0)
        val haversine = (sinLat * sinLat) + (kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon)
        val angularDistance = 2.0 * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * angularDistance
    }
}
