package com.drivest.navigation.parking

import com.drivest.navigation.osm.OverpassElement

internal object ParkingSpotMapper {

    fun map(elements: List<OverpassElement>): List<ParkingSpotRecord> {
        return elements.mapNotNull { element ->
            val lat = element.lat ?: element.centroidLat
            val lon = element.lon ?: element.centroidLon
            if (lat == null || lon == null) return@mapNotNull null

            if (ParkingSpotRules.hasPrivateAccess(element.tags)) return@mapNotNull null

            val tags = filterTags(element.tags)
            val title = tags["name"]?.takeIf { it.isNotBlank() } ?: "Parking"

            ParkingSpotRecord(
                id = stableId(element.elementType, element.id),
                title = title,
                lat = lat,
                lng = lon,
                source = "OSM",
                feeFlag = ParkingSpotRules.feeFlagFor(tags),
                rulesSummary = ParkingSpotRules.rulesSummaryFor(tags),
                confidenceScore = ParkingSpotRules.confidenceScoreFor(tags),
                isAccessible = ParkingSpotRules.isAccessible(tags)
            )
        }
    }

    private fun stableId(type: String, id: Long): String {
        return "${type.lowercase()}-$id"
    }

    private fun filterTags(tags: Map<String, String>): Map<String, String> {
        val allowed = setOf(
            "name",
            "fee",
            "access",
            "maxstay",
            "opening_hours",
            "capacity",
            "operator",
            "park_ride",
            "disabled",
            "wheelchair",
            "lit",
            "covered"
        )
        return tags.filterKeys { key -> allowed.contains(key) }
    }
}
