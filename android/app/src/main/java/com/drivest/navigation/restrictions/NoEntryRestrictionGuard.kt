package com.drivest.navigation.restrictions

import com.drivest.navigation.geo.RouteProjection
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point

data class NoEntryConflict(
    val featureId: String,
    val distanceAheadMeters: Double
)

object NoEntryRestrictionGuard {

    fun noEntryFeatures(features: List<OsmFeature>): List<OsmFeature> {
        return features.filter { it.type == OsmFeatureType.NO_ENTRY }
    }

    fun countRouteConflicts(
        routePoints: List<Point>,
        noEntryFeatures: List<OsmFeature>,
        routeMatchDistanceMeters: Double = DEFAULT_ROUTE_MATCH_DISTANCE_METERS
    ): Int {
        if (routePoints.size < 2 || noEntryFeatures.isEmpty()) return 0
        return noEntryFeatures.count { feature ->
            val featurePoint = Point.fromLngLat(feature.lon, feature.lat)
            val projected = RouteProjection.projectPointOntoRoute(routePoints, featurePoint) ?: return@count false
            projected.lateralDistanceMeters <= routeMatchDistanceMeters
        }
    }

    fun nearestConflictAhead(
        userPoint: Point,
        routePoints: List<Point>,
        noEntryFeatures: List<OsmFeature>,
        warningDistanceMeters: Double = DEFAULT_WARNING_DISTANCE_METERS,
        routeMatchDistanceMeters: Double = DEFAULT_ROUTE_MATCH_DISTANCE_METERS
    ): NoEntryConflict? {
        if (routePoints.size < 2 || noEntryFeatures.isEmpty()) return null
        return noEntryFeatures.mapNotNull { feature ->
            val featurePoint = Point.fromLngLat(feature.lon, feature.lat)
            val projected = RouteProjection.projectPointOntoRoute(routePoints, featurePoint) ?: return@mapNotNull null
            if (projected.lateralDistanceMeters > routeMatchDistanceMeters) return@mapNotNull null
            val aheadDistance = RouteProjection.alongDistanceAheadMeters(
                routePoints = routePoints,
                userPoint = userPoint,
                featurePoint = featurePoint
            ) ?: return@mapNotNull null
            if (aheadDistance < 0.0 || aheadDistance > warningDistanceMeters) return@mapNotNull null
            NoEntryConflict(featureId = feature.id, distanceAheadMeters = aheadDistance)
        }.minByOrNull { it.distanceAheadMeters }
    }

    const val DEFAULT_WARNING_DISTANCE_METERS = 60.0
    const val DEFAULT_ROUTE_MATCH_DISTANCE_METERS = 30.0
}

