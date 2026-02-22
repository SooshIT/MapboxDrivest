package com.drivest.navigation.osm

object OverpassQueryBuilders {

    fun trafficSignalsBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            trafficSignalsClauses(south, west, north, east)
        )
    }

    fun zebraBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            zebraClauses(south, west, north, east)
        )
    }

    fun giveWayBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            giveWayClauses(south, west, north, east)
        )
    }

    fun speedCameraBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            speedCameraClauses(south, west, north, east)
        )
    }

    fun roundaboutBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            roundaboutClauses(south, west, north, east)
        )
    }

    fun miniRoundaboutBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            miniRoundaboutClauses(south, west, north, east)
        )
    }

    fun schoolBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            schoolClauses(south, west, north, east)
        )
    }

    fun busLaneBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            busLaneClauses(south, west, north, east)
        )
    }

    fun busStopBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            busStopClauses(south, west, north, east)
        )
    }

    fun noEntryBBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return wrapWithOverpassEnvelope(
            noEntryClauses(south, west, north, east)
        )
    }

    fun clausesForType(
        type: OsmFeatureType,
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return when (type) {
            OsmFeatureType.TRAFFIC_SIGNAL -> trafficSignalsClauses(south, west, north, east)
            OsmFeatureType.ZEBRA_CROSSING -> zebraClauses(south, west, north, east)
            OsmFeatureType.GIVE_WAY -> giveWayClauses(south, west, north, east)
            OsmFeatureType.SPEED_CAMERA -> speedCameraClauses(south, west, north, east)
            OsmFeatureType.ROUNDABOUT -> roundaboutClauses(south, west, north, east)
            OsmFeatureType.MINI_ROUNDABOUT -> miniRoundaboutClauses(south, west, north, east)
            OsmFeatureType.SCHOOL_ZONE -> schoolClauses(south, west, north, east)
            OsmFeatureType.BUS_LANE -> busLaneClauses(south, west, north, east)
            OsmFeatureType.BUS_STOP -> busStopClauses(south, west, north, east)
            OsmFeatureType.NO_ENTRY -> noEntryClauses(south, west, north, east)
        }
    }

    fun unionQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        types: Set<OsmFeatureType>
    ): String {
        val clauses = types.sortedBy { it.name }
            .joinToString(separator = "\n") { type ->
                clausesForType(type, south, west, north, east)
            }
        return wrapWithOverpassEnvelope(clauses)
    }

    private fun trafficSignalsClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"="traffic_signals"]($south,$west,$north,$east);
            way["highway"="traffic_signals"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun zebraClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["crossing"="zebra"]($south,$west,$north,$east);
            way["crossing"="zebra"]($south,$west,$north,$east);
            node["crossing_ref"="zebra"]($south,$west,$north,$east);
            way["crossing_ref"="zebra"]($south,$west,$north,$east);
            node["highway"="crossing"]["crossing"="zebra"]($south,$west,$north,$east);
            way["highway"="crossing"]["crossing"="zebra"]($south,$west,$north,$east);
            node["highway"="crossing"]["crossing_ref"="zebra"]($south,$west,$north,$east);
            way["highway"="crossing"]["crossing_ref"="zebra"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun giveWayClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"="give_way"]($south,$west,$north,$east);
            way["highway"="give_way"]($south,$west,$north,$east);
            node["give_way"="yes"]($south,$west,$north,$east);
            way["give_way"="yes"]($south,$west,$north,$east);
            node["traffic_sign"~"give[_ ]?way", i]($south,$west,$north,$east);
            way["traffic_sign"~"give[_ ]?way", i]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun speedCameraClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"="speed_camera"]($south,$west,$north,$east);
            way["highway"="speed_camera"]($south,$west,$north,$east);
            node["enforcement"="speed_camera"]($south,$west,$north,$east);
            way["enforcement"="speed_camera"]($south,$west,$north,$east);
            node["speed_camera"="yes"]($south,$west,$north,$east);
            way["speed_camera"="yes"]($south,$west,$north,$east);
            node["camera:speed"="yes"]($south,$west,$north,$east);
            way["camera:speed"="yes"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun roundaboutClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["junction"="roundabout"]($south,$west,$north,$east);
            way["junction"="roundabout"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun miniRoundaboutClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"="mini_roundabout"]($south,$west,$north,$east);
            way["highway"="mini_roundabout"]($south,$west,$north,$east);
            node["junction"="mini_roundabout"]($south,$west,$north,$east);
            way["junction"="mini_roundabout"]($south,$west,$north,$east);
            node["mini_roundabout"="yes"]($south,$west,$north,$east);
            way["mini_roundabout"="yes"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun schoolClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["amenity"="school"]($south,$west,$north,$east);
            way["amenity"="school"]($south,$west,$north,$east);
            node["landuse"="school"]($south,$west,$north,$east);
            way["landuse"="school"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun busLaneClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["bus:lanes"]($south,$west,$north,$east);
            way["bus:lanes"]($south,$west,$north,$east);
            node["lanes:bus"]($south,$west,$north,$east);
            way["lanes:bus"]($south,$west,$north,$east);
            node["busway"]($south,$west,$north,$east);
            way["busway"]($south,$west,$north,$east);
            node["busway:left"]($south,$west,$north,$east);
            way["busway:left"]($south,$west,$north,$east);
            node["busway:right"]($south,$west,$north,$east);
            way["busway:right"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun busStopClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"="bus_stop"]($south,$west,$north,$east);
            way["highway"="bus_stop"]($south,$west,$north,$east);
            node["public_transport"="stop_position"]["bus"="yes"]($south,$west,$north,$east);
            way["public_transport"="stop_position"]["bus"="yes"]($south,$west,$north,$east);
            node["public_transport"="platform"]["bus"="yes"]($south,$west,$north,$east);
            way["public_transport"="platform"]["bus"="yes"]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun noEntryClauses(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): String {
        return """
            node["highway"]["access"="no"]($south,$west,$north,$east);
            way["highway"]["access"="no"]($south,$west,$north,$east);
            node["highway"]["vehicle"="no"]($south,$west,$north,$east);
            way["highway"]["vehicle"="no"]($south,$west,$north,$east);
            node["highway"]["motor_vehicle"="no"]($south,$west,$north,$east);
            way["highway"]["motor_vehicle"="no"]($south,$west,$north,$east);
            way["oneway"="-1"]($south,$west,$north,$east);
            node["traffic_sign"~"no[_ ]?entry", i]($south,$west,$north,$east);
            way["traffic_sign"~"no[_ ]?entry", i]($south,$west,$north,$east);
            node["traffic_sign:forward"~"no[_ ]?entry", i]($south,$west,$north,$east);
            way["traffic_sign:forward"~"no[_ ]?entry", i]($south,$west,$north,$east);
            node["traffic_sign:backward"~"no[_ ]?entry", i]($south,$west,$north,$east);
            way["traffic_sign:backward"~"no[_ ]?entry", i]($south,$west,$north,$east);
            relation["type"="restriction"]["restriction"~"^no_", i]($south,$west,$north,$east);
        """.trimIndent()
    }

    private fun wrapWithOverpassEnvelope(clauses: String): String {
        return """
            [out:json][timeout:20];
            (
              $clauses
            );
            out body center;
        """.trimIndent()
    }
}
