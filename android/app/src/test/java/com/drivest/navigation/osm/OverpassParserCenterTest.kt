package com.drivest.navigation.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassParserCenterTest {

    @Test
    fun parsesWayAndRelationCenterCoordinates() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 101,
                  "center": { "lat": 51.5001, "lon": 0.1001 },
                  "tags": { "highway": "residential", "oneway": "-1" }
                },
                {
                  "type": "relation",
                  "id": 202,
                  "center": { "lat": 51.5009, "lon": 0.1012 },
                  "tags": { "type": "restriction", "restriction": "no_left_turn" }
                }
              ]
            }
        """.trimIndent()

        val parsed = OverpassParser.parse(json)

        assertEquals(2, parsed.size)
        val way = parsed.first { it.id == 101L }
        val relation = parsed.first { it.id == 202L }
        assertEquals(51.5001, way.centroidLat!!, 0.00001)
        assertEquals(0.1001, way.centroidLon!!, 0.00001)
        assertEquals(51.5009, relation.centroidLat!!, 0.00001)
        assertEquals(0.1012, relation.centroidLon!!, 0.00001)
        assertTrue(relation.tags["restriction"] == "no_left_turn")
    }
}

