package com.drivest.navigation.pack

import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.practice.PracticeRoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PackValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object PackValidators {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val ROUTE_CENTRE_MATCH_MAX_DISTANCE_M = 60.0

    fun validateRoutesPack(routesPack: RoutesPack, centre: TestCentre): PackValidationResult {
        val errors = mutableListOf<String>()
        routesPack.routes.forEach { route ->
            validateRouteAgainstCentre(route, centre, errors)
        }
        return PackValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    fun validateHazardsPack(hazardsPack: HazardsPack): PackValidationResult {
        val errors = mutableListOf<String>()
        val bbox = hazardsPack.metadata.bbox
        hazardsPack.hazards.forEach { hazard ->
            val latInBounds = hazard.lat in bbox.south..bbox.north
            val lonInBounds = hazard.lon in bbox.west..bbox.east
            if (!latInBounds || !lonInBounds) {
                errors += "Hazard ${hazard.id} is outside bbox."
            }
        }
        return PackValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    private fun validateRouteAgainstCentre(
        route: PracticeRoute,
        centre: TestCentre,
        errors: MutableList<String>
    ) {
        val centrePoint = PracticeRoutePoint(lat = centre.lat, lon = centre.lon)
        val startPoint = PracticeRoutePoint(lat = route.startLat, lon = route.startLon)
        val endPoint = route.geometry.lastOrNull() ?: startPoint

        val startDistanceM = haversineDistanceMeters(centrePoint, startPoint)
        val endDistanceM = haversineDistanceMeters(centrePoint, endPoint)

        if (startDistanceM > ROUTE_CENTRE_MATCH_MAX_DISTANCE_M) {
            errors += "Route ${route.id} start is ${startDistanceM.toInt()}m from centre."
        }
        if (endDistanceM > ROUTE_CENTRE_MATCH_MAX_DISTANCE_M) {
            errors += "Route ${route.id} end is ${endDistanceM.toInt()}m from centre."
        }
    }

    private fun haversineDistanceMeters(
        from: PracticeRoutePoint,
        to: PracticeRoutePoint
    ): Double {
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)

        val a = sin(dLat / 2).pow2() + cos(lat1) * cos(lat2) * sin(dLon / 2).pow2()
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    private fun Double.pow2(): Double = this * this
}
