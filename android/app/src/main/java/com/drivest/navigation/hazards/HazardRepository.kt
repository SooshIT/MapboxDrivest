package com.drivest.navigation.hazards

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point

interface HazardRepository {
    suspend fun getFeaturesForRoute(
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>,
        centreId: String?
    ): List<OsmFeature>
}
