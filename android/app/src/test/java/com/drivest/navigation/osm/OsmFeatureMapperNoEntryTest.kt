package com.drivest.navigation.osm

import org.junit.Assert.assertTrue
import org.junit.Test

class OsmFeatureMapperNoEntryTest {

    @Test
    fun mapsNoEntryFromAccessRestrictionsAndOneWay() {
        val accessRestrictedWay = OverpassElement(
            id = 1001L,
            elementType = "way",
            lat = null,
            lon = null,
            centroidLat = 51.5000,
            centroidLon = 0.1000,
            tags = mapOf(
                "highway" to "residential",
                "access" to "no"
            )
        )
        val reverseOneWay = OverpassElement(
            id = 1002L,
            elementType = "way",
            lat = null,
            lon = null,
            centroidLat = 51.5005,
            centroidLon = 0.1000,
            tags = mapOf(
                "highway" to "service",
                "oneway" to "-1"
            )
        )

        val mapped = OsmFeatureMapper.map(listOf(accessRestrictedWay, reverseOneWay))

        assertTrue(mapped.any { it.type == OsmFeatureType.NO_ENTRY && it.id.contains("1001") })
        assertTrue(mapped.any { it.type == OsmFeatureType.NO_ENTRY && it.id.contains("1002") })
    }

    @Test
    fun mapsNoEntryFromTurnRestrictionRelationTags() {
        val turnRestriction = OverpassElement(
            id = 2001L,
            elementType = "relation",
            lat = null,
            lon = null,
            centroidLat = 51.5010,
            centroidLon = 0.1010,
            tags = mapOf(
                "type" to "restriction",
                "restriction" to "no_right_turn"
            )
        )

        val mapped = OsmFeatureMapper.map(listOf(turnRestriction))
        assertTrue(mapped.any { it.type == OsmFeatureType.NO_ENTRY && it.id.contains("2001") })
    }
}

