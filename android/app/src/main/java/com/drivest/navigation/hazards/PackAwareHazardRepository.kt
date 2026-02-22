package com.drivest.navigation.hazards

import android.content.Context
import android.util.Log
import com.drivest.navigation.backend.BackendHazardRepository
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureCache
import com.drivest.navigation.osm.OsmFeatureRepository
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.osm.OverpassClient
import com.drivest.navigation.pack.PackJsonParser
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.pack.PackValidators
import com.drivest.navigation.practice.PolylineDistance
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.first
import java.util.Locale

class PackAwareHazardRepository(
    context: Context,
    private val settingsRepository: SettingsRepository
) : HazardRepository {

    private val packStore = PackStore(context)
    private val speedCameraCacheStore = SpeedCameraCacheStore(context)
    private val backendHazardRepository = BackendHazardRepository(context = context)
    private val osmFeatureRepository = OsmFeatureRepository(
        overpassClient = OverpassClient(),
        cache = OsmFeatureCache(context)
    )
    private val appContext = context.applicationContext

    override suspend fun getFeaturesForRoute(
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>,
        centreId: String?
    ): List<OsmFeature> {
        val safeRoutePoints = routePoints.filter { point ->
            point.latitude().isFinite() && point.longitude().isFinite()
        }
        val safeCentreId = centreId?.takeIf { it.isNotBlank() }

        if (safeCentreId.isNullOrBlank() && safeRoutePoints.isEmpty()) {
            return emptyList()
        }

        val routeRadius = radiusMeters.coerceIn(80, 250)
        // Keep speed cameras resilient across route-scoped backend gaps with a 24h local cache.
        val cachedSpeedCameras = if (types.contains(OsmFeatureType.SPEED_CAMERA)) {
            speedCameraCacheStore.read(
                centreId = safeCentreId,
                routePoints = safeRoutePoints
            )
        } else {
            emptyList()
        }

        val fetchedFeatures = if (!safeCentreId.isNullOrBlank()) {
            val offlineReadyHazards = loadOfflineReadyHazards(safeCentreId, types)
            if (offlineReadyHazards.isNotEmpty()) {
                if (safeRoutePoints.isEmpty()) {
                    offlineReadyHazards
                } else {
                    filterFeaturesToRouteCorridor(
                        features = offlineReadyHazards,
                        routePoints = safeRoutePoints,
                        radiusMeters = routeRadius
                    )
                }
            } else {
                when (settingsRepository.dataSourceMode.first()) {
                    DataSourceMode.BACKEND_ONLY -> {
                        loadBackendOnly(
                            centreId = safeCentreId,
                            routePoints = safeRoutePoints,
                            radiusMeters = routeRadius,
                            types = types
                        )
                    }

                    DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> loadBackendThenCacheThenAssets(
                        centreId = safeCentreId,
                        routePoints = safeRoutePoints,
                        radiusMeters = routeRadius,
                        types = types
                    )

                    DataSourceMode.ASSETS_ONLY -> {
                        if (safeCentreId.isNullOrBlank()) {
                            emptyList()
                        } else {
                            loadFromAssetsOnly(safeCentreId, types)
                        }
                    }
                }
            }
        } else {
            when (settingsRepository.dataSourceMode.first()) {
                DataSourceMode.BACKEND_ONLY -> {
                    loadBackendOnly(
                        centreId = safeCentreId,
                        routePoints = safeRoutePoints,
                        radiusMeters = routeRadius,
                        types = types
                    )
                }

                DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> loadBackendThenCacheThenAssets(
                    centreId = safeCentreId,
                    routePoints = safeRoutePoints,
                    radiusMeters = routeRadius,
                    types = types
                )

                DataSourceMode.ASSETS_ONLY -> {
                    if (safeCentreId.isNullOrBlank()) {
                        emptyList()
                    } else {
                        loadFromAssetsOnly(safeCentreId, types)
                    }
                }
            }
        }

        return mergeSpeedCameraCache(
            fetchedFeatures = fetchedFeatures,
            cachedSpeedCameras = cachedSpeedCameras,
            routePoints = safeRoutePoints,
            centreId = safeCentreId,
            requestedTypes = types
        )
    }

    private suspend fun mergeSpeedCameraCache(
        fetchedFeatures: List<OsmFeature>,
        cachedSpeedCameras: List<OsmFeature>,
        routePoints: List<Point>,
        centreId: String?,
        requestedTypes: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (!requestedTypes.contains(OsmFeatureType.SPEED_CAMERA)) return fetchedFeatures
        if (routePoints.size < 2) return fetchedFeatures

        val fetchedSpeedCameras = fetchedFeatures.filter { feature ->
            feature.type == OsmFeatureType.SPEED_CAMERA
        }

        if (fetchedSpeedCameras.isNotEmpty()) {
            // Fresh route cameras overwrite cached cameras for this route signature.
            speedCameraCacheStore.write(
                centreId = centreId,
                routePoints = routePoints,
                features = fetchedSpeedCameras
            )
            return fetchedFeatures
        }

        var fallbackCameras = filterFeaturesToRouteCorridor(
            features = cachedSpeedCameras,
            routePoints = routePoints,
            radiusMeters = MAX_ROUTE_CORRIDOR_METERS
        )
        if (fallbackCameras.isEmpty()) {
            val overpassCameras = loadHazardsFromOverpass(
                routePoints = routePoints,
                radiusMeters = MAX_ROUTE_CORRIDOR_METERS,
                types = setOf(OsmFeatureType.SPEED_CAMERA)
            )
            if (overpassCameras.isNotEmpty()) {
                settingsRepository.recordFallbackUsed()
                speedCameraCacheStore.write(
                    centreId = centreId,
                    routePoints = routePoints,
                    features = overpassCameras
                )
                fallbackCameras = overpassCameras
            }
        }
        if (fallbackCameras.isEmpty()) {
            return fetchedFeatures
        }

        return (fetchedFeatures + fallbackCameras)
            .distinctBy { feature ->
                buildString {
                    append(feature.type.name)
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lat))
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lon))
                }
            }
    }

    private suspend fun loadBackendOnly(
        centreId: String?,
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (routePoints.isNotEmpty()) {
            try {
                settingsRepository.clearBackendErrorSummary()
                val routeHazards = backendHazardRepository.loadHazardsForRoute(
                    routePoints = routePoints,
                    radiusMeters = radiusMeters,
                    types = types,
                    centreId = centreId
                )
                return routeHazards
            } catch (error: Exception) {
                val summary = error.message ?: "Backend route hazards request failed."
                settingsRepository.recordBackendErrorSummary(summary)
                if (centreId.isNullOrBlank()) {
                    return emptyList()
                }
            }
        }
        if (centreId.isNullOrBlank()) return emptyList()

        return try {
            settingsRepository.clearBackendErrorSummary()
            backendHazardRepository.loadHazardsForCentre(centreId).filter { it.type in types }
        } catch (error: Exception) {
            val summary = error.message ?: "Backend hazards request failed for $centreId."
            settingsRepository.recordBackendErrorSummary(summary)
            val cachedHazards = loadHazardsFromPackCache(centreId, types)
            if (cachedHazards.isNotEmpty()) {
                settingsRepository.recordFallbackUsed()
                return cachedHazards
            }
            emptyList()
        }
    }

    private suspend fun loadBackendThenCacheThenAssets(
        centreId: String?,
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (routePoints.isNotEmpty()) {
            try {
                val routeHazards = backendHazardRepository.loadHazardsForRoute(
                    routePoints = routePoints,
                    radiusMeters = radiusMeters,
                    types = types,
                    centreId = centreId
                )
                settingsRepository.clearBackendErrorSummary()
                if (routeHazards.isNotEmpty()) {
                    return routeHazards
                }
            } catch (error: Exception) {
                val summary = error.message ?: "Backend route hazards request failed."
                settingsRepository.recordBackendErrorSummary(summary)
            }
        }

        if (!centreId.isNullOrBlank()) {
            try {
                val backendHazards = backendHazardRepository.loadHazardsForCentre(centreId)
                settingsRepository.clearBackendErrorSummary()
                if (backendHazards.isNotEmpty()) {
                    val filtered = filterFeaturesToRouteCorridor(
                        features = backendHazards.filter { it.type in types },
                        routePoints = routePoints,
                        radiusMeters = radiusMeters
                    )
                    return mergeWithOverpassForMissingTypes(
                        baselineFeatures = filtered,
                        routePoints = routePoints,
                        radiusMeters = radiusMeters,
                        requestedTypes = types
                    )
                }
            } catch (error: Exception) {
                val summary = error.message ?: "Backend hazards request failed for $centreId."
                settingsRepository.recordBackendErrorSummary(summary)
            }

            val cachedHazards = loadHazardsFromPackCache(centreId, types)
            if (cachedHazards.isNotEmpty()) {
                settingsRepository.recordFallbackUsed()
                val routeScopedCached = filterFeaturesToRouteCorridor(
                    features = cachedHazards,
                    routePoints = routePoints,
                    radiusMeters = radiusMeters
                )
                return mergeWithOverpassForMissingTypes(
                    baselineFeatures = routeScopedCached,
                    routePoints = routePoints,
                    radiusMeters = radiusMeters,
                    requestedTypes = types
                )
            }

            val assetsHazards = loadHazardsFromLocalAssets(centreId, types)
            if (assetsHazards.isNotEmpty()) {
                settingsRepository.recordFallbackUsed()
                val routeScopedAssets = filterFeaturesToRouteCorridor(
                    features = assetsHazards,
                    routePoints = routePoints,
                    radiusMeters = radiusMeters
                )
                return mergeWithOverpassForMissingTypes(
                    baselineFeatures = routeScopedAssets,
                    routePoints = routePoints,
                    radiusMeters = radiusMeters,
                    requestedTypes = types
                )
            }
        }

        val overpassHazards = loadHazardsFromOverpass(
            routePoints = routePoints,
            radiusMeters = radiusMeters,
            types = types
        )
        if (overpassHazards.isNotEmpty()) {
            settingsRepository.recordFallbackUsed()
            settingsRepository.clearBackendErrorSummary()
            return overpassHazards
        }

        settingsRepository.recordFallbackUsed()
        return emptyList()
    }

    private suspend fun mergeWithOverpassForMissingTypes(
        baselineFeatures: List<OsmFeature>,
        routePoints: List<Point>,
        radiusMeters: Int,
        requestedTypes: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (routePoints.isEmpty()) return baselineFeatures
        if (requestedTypes.isEmpty()) return baselineFeatures

        if (baselineFeatures.isEmpty()) {
            return loadHazardsFromOverpass(
                routePoints = routePoints,
                radiusMeters = radiusMeters,
                types = requestedTypes
            )
        }

        val presentTypes = baselineFeatures.mapTo(mutableSetOf()) { it.type }
        val missingTypes = requestedTypes - presentTypes
        if (missingTypes.isEmpty()) return baselineFeatures

        val missingFromOverpass = loadHazardsFromOverpass(
            routePoints = routePoints,
            radiusMeters = radiusMeters,
            types = missingTypes
        )
        if (missingFromOverpass.isEmpty()) return baselineFeatures

        return (baselineFeatures + missingFromOverpass)
            .distinctBy { feature ->
                buildString {
                    append(feature.type.name)
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lat))
                    append(':')
                    append(String.format(Locale.US, "%.5f", feature.lon))
                }
            }
    }

    private suspend fun loadFromAssetsOnly(
        centreId: String,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        return loadHazardsFromLocalAssets(centreId, types)
    }

    private fun loadHazardsFromPackCache(
        centreId: String,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        val packJson = packStore.readPack(PackType.HAZARDS, centreId) ?: return emptyList()
        val hazardsPack = PackJsonParser.parseHazardsPack(packJson) ?: return emptyList()
        val validation = PackValidators.validateHazardsPack(hazardsPack)
        if (!validation.isValid) return emptyList()
        return hazardsPack.hazards.filter { it.type in types }
    }

    private fun loadHazardsFromLocalAssets(
        centreId: String,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        val candidatePaths = listOf(
            "hazards/$centreId/hazards.json",
            "hazards/$centreId.json"
        )
        for (assetPath in candidatePaths) {
            val parsed = runCatching {
                appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrNull()?.let { jsonText ->
                PackJsonParser.parseHazardsPack(jsonText)
            } ?: continue
            val validation = PackValidators.validateHazardsPack(parsed)
            if (!validation.isValid) continue
            return parsed.hazards.filter { it.type in types }
        }
        return emptyList()
    }

    private fun loadOfflineReadyHazards(
        centreId: String,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (!packStore.isOfflineAvailable(PackType.HAZARDS, centreId)) return emptyList()
        return loadHazardsFromPackCache(centreId, types)
    }

    private fun filterFeaturesToRouteCorridor(
        features: List<OsmFeature>,
        routePoints: List<Point>,
        radiusMeters: Int
    ): List<OsmFeature> {
        if (features.isEmpty()) return emptyList()
        if (routePoints.size < 2) return features
        val corridorRadiusMeters = radiusMeters
            .coerceIn(MIN_ROUTE_CORRIDOR_METERS, MAX_ROUTE_CORRIDOR_METERS)
            .toDouble()
        return features.filter { feature ->
            val featurePoint = Point.fromLngLat(feature.lon, feature.lat)
            PolylineDistance.minimumDistanceMeters(featurePoint, routePoints) <= corridorRadiusMeters
        }
    }

    private suspend fun loadHazardsFromOverpass(
        routePoints: List<Point>,
        radiusMeters: Int,
        types: Set<OsmFeatureType>
    ): List<OsmFeature> {
        if (routePoints.isEmpty() || types.isEmpty()) return emptyList()
        return runCatching {
            osmFeatureRepository.getFeaturesForRoute(
                routePoints = routePoints,
                radiusMeters = radiusMeters,
                types = types
            )
        }.onFailure { error ->
            Log.w(TAG, "Overpass fallback failed: ${error.message}")
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "PackAwareHazardRepo"
        const val MIN_ROUTE_CORRIDOR_METERS = 100
        const val MAX_ROUTE_CORRIDOR_METERS = 300
    }
}
