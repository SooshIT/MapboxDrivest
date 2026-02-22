package com.drivest.navigation.pack

import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.practice.PracticeRoute

data class PackBbox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

data class PackMetadata(
    val version: String,
    val generatedAt: String,
    val bbox: PackBbox
)

data class CentresPack(
    val metadata: PackMetadata,
    val centres: List<TestCentre>
)

data class RoutesPack(
    val metadata: PackMetadata,
    val centreId: String,
    val routes: List<PracticeRoute>
)

data class HazardsPack(
    val metadata: PackMetadata,
    val centreId: String,
    val hazards: List<OsmFeature>
)
