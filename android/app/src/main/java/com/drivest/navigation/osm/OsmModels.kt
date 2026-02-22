package com.drivest.navigation.osm

enum class OsmFeatureType {
    TRAFFIC_SIGNAL,
    ZEBRA_CROSSING,
    GIVE_WAY,
    SPEED_CAMERA,
    ROUNDABOUT,
    MINI_ROUNDABOUT,
    SCHOOL_ZONE,
    BUS_LANE,
    BUS_STOP,
    NO_ENTRY
}

data class OsmFeature(
    val id: String,
    val type: OsmFeatureType,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>,
    val source: String,
    val confidenceHint: Float
)
