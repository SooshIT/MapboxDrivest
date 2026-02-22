package com.drivest.navigation.theory.services

import com.drivest.navigation.report.SessionSummaryPayload

object MapRouteTagsToTheoryTopics {

    private val routeTagToTopicId = mapOf(
        "zebra_crossings" to "pedestrian_crossings",
        "traffic_lights" to "signals_and_junction_control",
        "bus_lanes" to "bus_lane_rules",
        "roundabouts" to "roundabouts",
        "school_zones" to "speed_limits_and_school_zones",
        "mini_roundabouts" to "roundabouts"
    )

    fun mapTags(tags: Collection<String>): List<String> {
        return tags.mapNotNull { tag ->
            routeTagToTopicId[tag.trim().lowercase()]
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
