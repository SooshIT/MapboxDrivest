package com.drivest.navigation.session

import com.mapbox.geojson.Point
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardFetchTriggerPolicyTest {

    @Test
    fun sessionStartAlwaysForcesRefresh() {
        assertTrue(HazardFetchTriggerPolicy.shouldForceOnSessionStart())
    }

    @Test
    fun centreChangeForcesRefresh() {
        assertTrue(
            HazardFetchTriggerPolicy.shouldForceOnCentreChange(
                previousCentreId = "colchester",
                nextCentreId = "chelmsford"
            )
        )
        assertFalse(
            HazardFetchTriggerPolicy.shouldForceOnCentreChange(
                previousCentreId = "colchester",
                nextCentreId = "colchester"
            )
        )
    }

    @Test
    fun movementAboveFiveKmForcesRefresh() {
        val anchor = Point.fromLngLat(0.1000, 51.5000)
        val farAway = Point.fromLngLat(0.1000, 51.5480) // >5km north
        assertTrue(
            HazardFetchTriggerPolicy.shouldForceOnMovement(
                lastFetchAnchorPoint = anchor,
                currentLocationPoint = farAway
            )
        )
    }

    @Test
    fun movementBelowFiveKmDoesNotForceRefresh() {
        val anchor = Point.fromLngLat(0.1000, 51.5000)
        val nearby = Point.fromLngLat(0.1000, 51.5200) // ~2.2km north
        assertFalse(
            HazardFetchTriggerPolicy.shouldForceOnMovement(
                lastFetchAnchorPoint = anchor,
                currentLocationPoint = nearby
            )
        )
    }
}
