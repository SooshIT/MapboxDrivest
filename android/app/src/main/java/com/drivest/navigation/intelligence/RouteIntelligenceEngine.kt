package com.drivest.navigation.intelligence

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.practice.PolylineDistance
import com.mapbox.geojson.Point
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class RouteDifficultyLabel {
    EASY,
    MEDIUM,
    HARD
}

data class RouteIntelligenceSummary(
    val roundaboutCount: Int,
    val trafficSignalCount: Int,
    val zebraCount: Int,
    val schoolCount: Int,
    val busLaneCount: Int,
    val complexityScore: Int,
    val stressIndex: Int,
    val difficultyLabel: RouteDifficultyLabel
)

class RouteIntelligenceEngine {

    fun evaluate(
        routeGeometry: List<Point>,
        hazards: List<OsmFeature>
    ): RouteIntelligenceSummary {
        if (routeGeometry.size < 2 || hazards.isEmpty()) {
            return zeroSummary()
        }

        var roundaboutCount = 0
        var trafficSignalCount = 0
        var zebraCount = 0
        var giveWayCount = 0
        var speedCameraCount = 0
        var schoolCount = 0
        var busLaneCount = 0
        var busStopCount = 0
        var noEntryCount = 0

        hazards.forEach { hazard ->
            val distanceToRoute = PolylineDistance.minimumDistanceMeters(
                point = Point.fromLngLat(hazard.lon, hazard.lat),
                polyline = routeGeometry
            )
            if (distanceToRoute > HAZARD_MATCH_DISTANCE_METERS) return@forEach

            when (hazard.type) {
                OsmFeatureType.ROUNDABOUT -> roundaboutCount += 1
                OsmFeatureType.MINI_ROUNDABOUT -> roundaboutCount += 1
                OsmFeatureType.TRAFFIC_SIGNAL -> trafficSignalCount += 1
                OsmFeatureType.ZEBRA_CROSSING -> zebraCount += 1
                OsmFeatureType.GIVE_WAY -> giveWayCount += 1
                OsmFeatureType.SPEED_CAMERA -> speedCameraCount += 1
                OsmFeatureType.SCHOOL_ZONE -> schoolCount += 1
                OsmFeatureType.BUS_LANE -> busLaneCount += 1
                OsmFeatureType.BUS_STOP -> busStopCount += 1
                OsmFeatureType.NO_ENTRY -> noEntryCount += 1
            }
        }

        val weightedScore = (roundaboutCount * 3) +
            (trafficSignalCount * 2) +
            (zebraCount * 1) +
            (giveWayCount * 1) +
            (speedCameraCount * 2) +
            (schoolCount * 2) +
            (busLaneCount * 2) +
            (busStopCount * 1) +
            (noEntryCount * 4)

        val routeLengthKm = max(routeLengthMeters(routeGeometry) / 1000.0, 0.25)
        val weightedDensity = weightedScore / routeLengthKm
        val complexityScore = min(100, ((weightedDensity * 6.5) + (weightedScore * 0.8)).roundToInt())
        val stressIndex = min(
            100,
            (
                (roundaboutCount * 8) +
                    (schoolCount * 7) +
                    (trafficSignalCount * 5) +
                    (zebraCount * 3) +
                    (giveWayCount * 2) +
                    (speedCameraCount * 6) +
                    (busLaneCount * 4) +
                    (busStopCount * 2) +
                    (noEntryCount * 9) +
                    (weightedDensity * 4.0)
                ).roundToInt()
        )

        val difficultyLabel = difficultyFromScore(complexityScore)
        return RouteIntelligenceSummary(
            roundaboutCount = roundaboutCount,
            trafficSignalCount = trafficSignalCount,
            zebraCount = zebraCount,
            schoolCount = schoolCount,
            busLaneCount = busLaneCount,
            complexityScore = complexityScore,
            stressIndex = stressIndex,
            difficultyLabel = difficultyLabel
        )
    }

    private fun difficultyFromScore(score: Int): RouteDifficultyLabel {
        return when (score.coerceIn(0, 100)) {
            in 0..33 -> RouteDifficultyLabel.EASY
            in 34..66 -> RouteDifficultyLabel.MEDIUM
            else -> RouteDifficultyLabel.HARD
        }
    }

    private fun routeLengthMeters(routeGeometry: List<Point>): Double {
        var total = 0.0
        for (index in 0 until routeGeometry.lastIndex) {
            total += haversineDistanceMeters(routeGeometry[index], routeGeometry[index + 1])
        }
        return total
    }

    private fun haversineDistanceMeters(a: Point, b: Point): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return earthRadiusMeters * c
    }

    private fun zeroSummary(): RouteIntelligenceSummary {
        return RouteIntelligenceSummary(
            roundaboutCount = 0,
            trafficSignalCount = 0,
            zebraCount = 0,
            schoolCount = 0,
            busLaneCount = 0,
            complexityScore = 0,
            stressIndex = 0,
            difficultyLabel = RouteDifficultyLabel.EASY
        )
    }

    private companion object {
        const val HAZARD_MATCH_DISTANCE_METERS = 45.0
    }
}
