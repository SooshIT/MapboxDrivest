package com.drivest.navigation.restrictions

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoEntryRestrictionGuardTest {

    private val route = listOf(
        Point.fromLngLat(0.10000, 51.50000),
        Point.fromLngLat(0.10000, 51.50300)
    )

    @Test
    fun countRouteConflictsOnlyCountsFeaturesNearRouteCorridor() {
        val onRoute = noEntryFeature(id = "on_route", lat = 51.50100, lon = 0.10000)
        val offRoute = noEntryFeature(id = "off_route", lat = 51.50100, lon = 0.10200)

        val count = NoEntryRestrictionGuard.countRouteConflicts(
            routePoints = route,
            noEntryFeatures = listOf(onRoute, offRoute)
        )

        assertEquals(1, count)
    }

    @Test
    fun nearestConflictAheadReturnsOnlyUpcomingRestrictionWithinWarningDistance() {
        val userPoint = Point.fromLngLat(0.10000, 51.50040)
        val aheadNear = noEntryFeature(id = "ahead_near", lat = 51.50085, lon = 0.10000)
        val aheadFar = noEntryFeature(id = "ahead_far", lat = 51.50170, lon = 0.10000)
        val behind = noEntryFeature(id = "behind", lat = 51.49960, lon = 0.10000)

        val conflict = NoEntryRestrictionGuard.nearestConflictAhead(
            userPoint = userPoint,
            routePoints = route,
            noEntryFeatures = listOf(aheadNear, aheadFar, behind)
        )

        assertNotNull(conflict)
        assertEquals("ahead_near", conflict?.featureId)
    }

    @Test
    fun nearestConflictAheadReturnsNullWhenRestrictionBeyondWarningDistance() {
        val userPoint = Point.fromLngLat(0.10000, 51.50040)
        val farOnly = noEntryFeature(id = "ahead_far", lat = 51.50170, lon = 0.10000)

        val conflict = NoEntryRestrictionGuard.nearestConflictAhead(
            userPoint = userPoint,
            routePoints = route,
            noEntryFeatures = listOf(farOnly)
        )

        assertNull(conflict)
    }

    private fun noEntryFeature(id: String, lat: Double, lon: Double): OsmFeature {
        return OsmFeature(
            id = id,
            type = OsmFeatureType.NO_ENTRY,
            lat = lat,
            lon = lon,
            tags = mapOf("access" to "no"),
            source = "test",
            confidenceHint = 1f
        )
    }
}

