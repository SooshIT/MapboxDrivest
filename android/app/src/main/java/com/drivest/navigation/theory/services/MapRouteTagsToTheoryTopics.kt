package com.drivest.navigation.theory.services

import com.drivest.navigation.report.SessionSummaryPayload

object MapRouteTagsToTheoryTopics {

    private val routeTagToTopicIds = mapOf(
        // Keep legacy ids first, then compatible ids used by the imported 1200-question pack.
        "zebra_crossings" to listOf("pedestrian_crossings", "vulnerable_road_users"),
        "traffic_lights" to listOf("signals_and_junction_control", "traffic_signals"),
        "bus_lanes" to listOf("bus_lane_rules", "legal_requirements"),
        "roundabouts" to listOf("roundabouts"),
        "school_zones" to listOf("speed_limits_and_school_zones", "vulnerable_road_users", "hazard_awareness"),
        "mini_roundabouts" to listOf("roundabouts")
    )

    fun mapTags(tags: Collection<String>): List<String> {
        return tags.flatMap { tag ->
            routeTagToTopicIds[tag.trim().lowercase()].orEmpty()
        }.distinct()
    }

    fun inferTagsFromSessionSummary(summary: SessionSummaryPayload): List<String> {
        return buildList {
            if (summary.zebraCount > 0) add("zebra_crossings")
            if (summary.trafficSignalCount > 0) add("traffic_lights")
            if (summary.busLaneCount > 0) add("bus_lanes")
            if (summary.roundaboutCount > 0) add("roundabouts")
            if (summary.schoolCount > 0) add("school_zones")
        }
    }
}
