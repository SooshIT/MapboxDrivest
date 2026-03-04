package com.drivest.navigation.signs

data class RoadSignFeature(
    val id: String,
    val lat: Double,
    val lon: Double,
    val signValues: List<String>,
    val tags: Map<String, String>,
    val source: String
)
