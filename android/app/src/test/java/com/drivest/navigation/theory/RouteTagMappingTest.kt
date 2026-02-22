package com.drivest.navigation.theory

import com.drivest.navigation.report.SessionSummaryPayload
import com.drivest.navigation.theory.services.MapRouteTagsToTheoryTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTagMappingTest {

    @Test
    fun mapsKnownRouteTagsToTopicIds() {
        val mapped = MapRouteTagsToTheoryTopics.mapTags(
            listOf("zebra_crossings", "traffic_lights", "roundabouts", "unknown")
        )
        assertEquals(
            listOf(
                "pedestrian_crossings",
                "signals_and_junction_control",
                "roundabouts"
            ),
            mapped
        )
    }

    @Test
    fun infersTagsFromSessionSummaryCounts() {
        val summary = SessionSummaryPayload(
            centreId = "colchester",
            routeId = "route-1",
            stressIndex = 45,
            complexityScore = 52,
            confidenceScore = 64,
            offRouteCount = 1,
            completionFlag = true,
            durationSeconds = 1300,
            distanceMetersDriven = 5400,
            roundaboutCount = 2,
            trafficSignalCount = 4,
            zebraCount = 1,
            schoolCount = 1,
            busLaneCount = 0
        )
        val tags = MapRouteTagsToTheoryTopics.inferTagsFromSessionSummary(summary)
        assertTrue(tags.contains("roundabouts"))
        assertTrue(tags.contains("traffic_lights"))
        assertTrue(tags.contains("zebra_crossings"))
        assertTrue(tags.contains("school_zones"))
    }
}
