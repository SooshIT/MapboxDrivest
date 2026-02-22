package com.drivest.navigation.osm

object OsmFeatureMapper {

    fun map(elements: List<OverpassElement>): List<OsmFeature> {
        val features = mutableListOf<OsmFeature>()
        for (element in elements) {
            val coordinate = resolveCoordinate(element) ?: continue
            val matchingTypes = resolveTypes(element.tags)
            if (matchingTypes.isEmpty()) continue
            for (type in matchingTypes) {
                features.add(
                    OsmFeature(
                        id = "${element.elementType}:${element.id}:${type.name.lowercase()}",
                        type = type,
                        lat = coordinate.first,
                        lon = coordinate.second,
                        tags = element.tags,
                        source = SOURCE_OVERPASS,
                        confidenceHint = confidenceFor(type)
                    )
                )
            }
        }
        return features
    }

    private fun resolveCoordinate(element: OverpassElement): Pair<Double, Double>? {
        if (element.elementType == "node" && element.lat != null && element.lon != null) {
            return Pair(element.lat, element.lon)
        }
        if (element.centroidLat != null && element.centroidLon != null) {
            return Pair(element.centroidLat, element.centroidLon)
        }
        return null
    }

    private fun resolveTypes(tags: Map<String, String>): Set<OsmFeatureType> {
        if (tags.isEmpty()) return emptySet()
        val types = mutableSetOf<OsmFeatureType>()

        if (tags["highway"] == "traffic_signals") {
            types += OsmFeatureType.TRAFFIC_SIGNAL
        }

        val isZebraCrossing = tags["crossing"] == "zebra" ||
            tags["crossing_ref"] == "zebra" ||
            (tags["highway"] == "crossing" &&
                (tags["crossing"] == "zebra" || tags["crossing_ref"] == "zebra"))
        if (isZebraCrossing) {
            types += OsmFeatureType.ZEBRA_CROSSING
        }

        val isGiveWay = tags["highway"] == "give_way" ||
            tags["give_way"] == "yes" ||
            (tags["traffic_sign"]?.contains("give_way", ignoreCase = true) == true) ||
            (tags["traffic_sign"]?.contains("give way", ignoreCase = true) == true)
        if (isGiveWay) {
            types += OsmFeatureType.GIVE_WAY
        }

        val isSpeedCamera = tags["highway"] == "speed_camera" ||
            tags["enforcement"] == "speed_camera" ||
            tags["speed_camera"] == "yes" ||
            tags["camera:speed"] == "yes"
        if (isSpeedCamera) {
            types += OsmFeatureType.SPEED_CAMERA
        }

        if (tags["junction"] == "roundabout") {
            types += OsmFeatureType.ROUNDABOUT
        }

        val isMiniRoundabout = tags["highway"] == "mini_roundabout" ||
            tags["junction"] == "mini_roundabout" ||
            tags["mini_roundabout"] == "yes"
        if (isMiniRoundabout) {
            types += OsmFeatureType.MINI_ROUNDABOUT
        }

        if (tags["amenity"] == "school" || tags["landuse"] == "school") {
            types += OsmFeatureType.SCHOOL_ZONE
        }

        if (
            tags.containsKey("bus:lanes") ||
            tags.containsKey("lanes:bus") ||
            tags.containsKey("busway") ||
            tags.containsKey("busway:left") ||
            tags.containsKey("busway:right")
        ) {
            types += OsmFeatureType.BUS_LANE
        }

        val isBusStop = tags["highway"] == "bus_stop" ||
            (tags["public_transport"] == "stop_position" &&
                (tags["bus"] == "yes" || tags["bus"] == "designated")) ||
            (tags["public_transport"] == "platform" &&
                (tags["bus"] == "yes" || tags["highway"] == "bus_stop"))
        if (isBusStop) {
            types += OsmFeatureType.BUS_STOP
        }

        if (isNoEntryOrRestriction(tags)) {
            types += OsmFeatureType.NO_ENTRY
        }

        return types
    }

    private fun isNoEntryOrRestriction(tags: Map<String, String>): Boolean {
        if (tags.isEmpty()) return false
        val hasRoadContext = tags.containsKey("highway") || tags.containsKey("junction") || tags.containsKey("oneway")
        val accessRestricted = hasRoadContext && (
            tags["access"] == "no" ||
                tags["vehicle"] == "no" ||
                tags["motor_vehicle"] == "no"
            )
        val reverseOneWay = hasRoadContext && tags["oneway"] == "-1"
        val noEntrySign = hasNoEntryTrafficSign(tags)
        val turnRestriction = tags["type"] == "restriction" &&
            tags["restriction"]?.startsWith("no_", ignoreCase = true) == true

        return accessRestricted || reverseOneWay || noEntrySign || turnRestriction
    }

    private fun hasNoEntryTrafficSign(tags: Map<String, String>): Boolean {
        val trafficSignValues = listOf(
            tags["traffic_sign"],
            tags["traffic_sign:forward"],
            tags["traffic_sign:backward"]
        )
        return trafficSignValues.any { raw ->
            raw?.contains("no_entry", ignoreCase = true) == true ||
                raw?.contains("no entry", ignoreCase = true) == true
        }
    }

    private fun confidenceFor(type: OsmFeatureType): Float {
        return when (type) {
            OsmFeatureType.NO_ENTRY -> 0.92f
            OsmFeatureType.SPEED_CAMERA -> 0.85f
            OsmFeatureType.GIVE_WAY -> 0.68f
            OsmFeatureType.BUS_LANE -> 0.3f
            OsmFeatureType.BUS_STOP -> 0.72f
            OsmFeatureType.SCHOOL_ZONE -> 0.6f
            OsmFeatureType.MINI_ROUNDABOUT -> 0.8f
            OsmFeatureType.ROUNDABOUT -> 0.7f
            OsmFeatureType.ZEBRA_CROSSING -> 0.7f
            OsmFeatureType.TRAFFIC_SIGNAL -> 0.8f
        }
    }

    private const val SOURCE_OVERPASS = "osm_overpass"
}
