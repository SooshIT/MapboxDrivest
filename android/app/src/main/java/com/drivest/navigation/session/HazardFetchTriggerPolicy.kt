package com.drivest.navigation.session

import com.mapbox.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HazardFetchTriggerPolicy {

    const val MOVEMENT_REFRESH_METERS = 5_000.0

    fun shouldForceOnSessionStart(): Boolean = true

    fun shouldForceOnCentreChange(previousCentreId: String?, nextCentreId: String?): Boolean {
        val previous = previousCentreId?.trim().orEmpty()
        val next = nextCentreId?.trim().orEmpty()
        return previous != next
    }

    fun shouldForceOnMovement(
        lastFetchAnchorPoint: Point?,
        currentLocationPoint: Point,
        thresholdMeters: Double = MOVEMENT_REFRESH_METERS
    ): Boolean {
        val anchor = lastFetchAnchorPoint ?: return false
        return distanceMeters(anchor, currentLocationPoint) > thresholdMeters
    }

    private fun distanceMeters(a: Point, b: Point): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude())
        val lat2 = Math.toRadians(b.latitude())
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude() - a.longitude())
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(haversine.coerceIn(0.0, 1.0)), sqrt((1.0 - haversine).coerceIn(0.0, 1.0)))
        return earthRadiusMeters * c
    }
}
