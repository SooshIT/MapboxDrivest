package com.drivest.navigation.osm

data class OverpassElement(
    val id: Long,
    val elementType: String,
    val lat: Double?,
    val lon: Double?,
    val centroidLat: Double?,
    val centroidLon: Double?,
    val tags: Map<String, String>
)
