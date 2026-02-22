package com.drivest.navigation.geo

import com.mapbox.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteProjectionResult(
    val projectedPoint: Point,
    val segmentIndex: Int,
    val segmentProgress: Double,
    val distanceAlongMeters: Double,
    val segmentBearingDegrees: Double,
    val lateralDistanceMeters: Double
)

object RouteProjection {

    /**
     * Projects [target] to the nearest segment on [routePoints] and returns distance-along-route.
     * Uses a local equirectangular projection for stable, deterministic geometry math offline.
     */
    fun projectPointOntoRoute(
        routePoints: List<Point>,
        target: Point
    ): RouteProjectionResult? {
        if (routePoints.size < 2) return null

        val referenceLatRad = Math.toRadians(target.latitude())
        val targetX = metersX(target.longitude(), referenceLatRad)
        val targetY = metersY(target.latitude())

        var cumulativeBeforeSegment = 0.0
        var best: RouteProjectionResult? = null
        var bestDistanceSq = Double.MAX_VALUE

        for (index in 0 until routePoints.lastIndex) {
            val start = routePoints[index]
            val end = routePoints[index + 1]

            val startX = metersX(start.longitude(), referenceLatRad)
            val startY = metersY(start.latitude())
            val endX = metersX(end.longitude(), referenceLatRad)
            val endY = metersY(end.latitude())

            val segX = endX - startX
            val segY = endY - startY
            val segLenSq = segX * segX + segY * segY
            if (segLenSq <= 1e-6) {
                continue
            }

            val rawT = ((targetX - startX) * segX + (targetY - startY) * segY) / segLenSq
            val t = rawT.coerceIn(0.0, 1.0)
            val projX = startX + (segX * t)
            val projY = startY + (segY * t)

            val dx = targetX - projX
            val dy = targetY - projY
            val distanceSq = dx * dx + dy * dy

            val segLen = sqrt(segLenSq)
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                best = RouteProjectionResult(
                    projectedPoint = Point.fromLngLat(
                        longitudeFromMetersX(projX, referenceLatRad),
                        latitudeFromMetersY(projY)
                    ),
                    segmentIndex = index,
                    segmentProgress = t,
                    distanceAlongMeters = cumulativeBeforeSegment + (segLen * t),
                    segmentBearingDegrees = bearingDegrees(start, end),
                    lateralDistanceMeters = sqrt(distanceSq)
                )
            }

            cumulativeBeforeSegment += segLen
        }

        return best
    }

    fun alongDistanceAheadMeters(
        routePoints: List<Point>,
        userPoint: Point,
        featurePoint: Point
    ): Double? {
        val userProjection = projectPointOntoRoute(routePoints, userPoint) ?: return null
        val featureProjection = projectPointOntoRoute(routePoints, featurePoint) ?: return null
        return featureProjection.distanceAlongMeters - userProjection.distanceAlongMeters
    }

    private fun bearingDegrees(start: Point, end: Point): Double {
        val lat1 = Math.toRadians(start.latitude())
        val lat2 = Math.toRadians(end.latitude())
        val dLon = Math.toRadians(end.longitude() - start.longitude())
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing % 360.0) + 360.0) % 360.0
    }

    private fun metersX(longitude: Double, referenceLatRad: Double): Double {
        return Math.toRadians(longitude) * EARTH_RADIUS_METERS * cos(referenceLatRad)
    }

    private fun metersY(latitude: Double): Double {
        return Math.toRadians(latitude) * EARTH_RADIUS_METERS
    }

    private fun longitudeFromMetersX(x: Double, referenceLatRad: Double): Double {
        val cosLat = max(1e-6, min(1.0, cos(referenceLatRad)))
        return Math.toDegrees(x / (EARTH_RADIUS_METERS * cosLat))
    }

    private fun latitudeFromMetersY(y: Double): Double {
        return Math.toDegrees(y / EARTH_RADIUS_METERS)
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
