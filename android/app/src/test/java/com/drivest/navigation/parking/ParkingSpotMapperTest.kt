package com.drivest.navigation.parking

import com.drivest.navigation.osm.OverpassParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingSpotMapperTest {

    @Test
    fun `normalises node way and relation elements`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 101,
                  "lat": 51.0,
                  "lon": 0.1,
                  "tags": {
                    "amenity": "parking",
                    "name": "Node Parking",
                    "fee": "no",
                    "opening_hours": "Mo-Fr 9:00-18:00",
                    "maxstay": "2 hours",
                    "wheelchair": "yes"
                  }
                },
                {
                  "type": "way",
                  "id": 202,
                  "center": { "lat": 51.1, "lon": 0.2 },
                  "tags": {
                    "amenity": "parking",
                    "name": "Way Parking",
                    "fee": "yes"
                  }
                },
                {
                  "type": "relation",
                  "id": 303,
                  "center": { "lat": 51.2, "lon": 0.3 },
                  "tags": {
                    "amenity": "parking"
                  }
                }
              ]
            }
        """.trimIndent()

        val elements = OverpassParser.parse(json)
        val records = ParkingSpotMapper.map(elements)

        assertEquals(3, records.size)
        assertTrue(records.any { it.id == "node-101" && it.lat == 51.0 && it.lng == 0.1 })
        assertTrue(records.any { it.id == "way-202" && it.lat == 51.1 && it.lng == 0.2 })
        assertTrue(records.any { it.id == "relation-303" && it.lat == 51.2 && it.lng == 0.3 })
    }

    @Test
    fun `fee parsing handles yes no missing and unknown`() {
        assertEquals(ParkingFeeFlag.LIKELY_FREE, ParkingSpotRules.feeFlagFor(mapOf("fee" to "no")))
        assertEquals(ParkingFeeFlag.LIKELY_PAID, ParkingSpotRules.feeFlagFor(mapOf("fee" to "yes")))
        assertEquals(ParkingFeeFlag.UNKNOWN, ParkingSpotRules.feeFlagFor(emptyMap()))
        assertEquals(ParkingFeeFlag.UNKNOWN, ParkingSpotRules.feeFlagFor(mapOf("fee" to "permit")))
    }

    @Test
    fun `confidence scoring matches rules`() {
        val score = ParkingSpotRules.confidenceScoreFor(
            mapOf(
                "fee" to "yes",
                "opening_hours" to "Mo-Fr 9:00-18:00",
                "maxstay" to "2 hours",
                "name" to "Test Parking"
            )
        )
        assertEquals(95, score)
        assertEquals(30, ParkingSpotRules.confidenceScoreFor(emptyMap()))
    }
}
