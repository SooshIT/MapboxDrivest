package com.drivest.navigation.session

import android.content.Context
import android.util.Log
import com.drivest.navigation.hazards.HazardSpatialIndex
import com.drivest.navigation.hazards.HazardRepository
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.prompts.PromptEngine
import com.drivest.navigation.prompts.PromptEvent
import com.drivest.navigation.settings.PreferredUnitsSetting
import com.drivest.navigation.settings.PromptSensitivity
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.settings.VoiceModeSetting
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class NavigationSessionManager(
    private val mapboxNavigation: MapboxNavigation,
    private val routeProgressObserver: RouteProgressObserver,
    private val voiceInstructionsObserver: VoiceInstructionsObserver,
    private val createReplayObserver: (() -> ReplayProgressObserver)?,
    private val clearVoiceQueue: () -> Unit,
    private val onStateChanged: (SessionState) -> Unit,
    private val onPreviewPracticeRoute: (PracticeRoute) -> Unit,
    private val onPreviewDestination: (Point) -> Unit,
    private val settingsRepository: SettingsRepository,
    private val hazardRepository: HazardRepository,
    private val promptEngine: PromptEngine,
    private val onPromptEvent: (PromptEvent?) -> Unit,
    private val onFeaturesUpdated: (List<OsmFeature>) -> Unit,
    private val onFeatureUnavailable: () -> Unit,
    private val onNearestMiniRoundaboutMeters: (Double) -> Unit = {}
) {

    enum class Mode {
        PRACTICE,
        NAVIGATION
    }

    enum class SessionState {
        BROWSE,
        PREVIEW,
        ACTIVE
    }

    private class ObserverRegistry {
        var attached = false
        var replayObserver: ReplayProgressObserver? = null
    }

    private val observerRegistry = ObserverRegistry()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsCollectionJob: Job? = null
    private var mode: Mode = Mode.PRACTICE
    private var state: SessionState = SessionState.BROWSE
    private var centreId: String? = null
    private var mapView: MapView? = null
    private var voiceModeSetting: VoiceModeSetting = VoiceModeSetting.ALL
    private var preferredUnitsSetting: PreferredUnitsSetting = PreferredUnitsSetting.UK_MPH
    private var visualPromptsEnabled: Boolean = true
    private var promptSensitivity: PromptSensitivity = PromptSensitivity.STANDARD

    private var latestLocationLat: Double? = null
    private var latestLocationLon: Double? = null
    private var latestGpsAccuracyM: Float = 999f
    private var latestSpeedMps: Float = 0f

    private var currentFeatures: List<OsmFeature> = emptyList()
    private val hazardSpatialIndex = HazardSpatialIndex(bucketSizeMeters = HAZARD_GRID_BUCKET_METERS)
    private var currentRoutePoints: List<Point> = emptyList()
    private var currentRouteKey: String? = null
    private var lastFeatureFetchRouteKey: String? = null
    private var lastFeatureFetchAnchorPoint: Point? = null
    private var lastFeatureFetchMs: Long = 0L
    private var featureFetchInProgress: Boolean = false
    private var lastPromptEvaluationMs: Long = 0L
    private var featureUnavailableNotified = false

    private val managedRouteProgressObserver = RouteProgressObserver { progress ->
        routeProgressObserver.onRouteProgressChanged(progress)
        evaluatePrompts(progress)
    }

    fun init(context: Context, mapView: MapView) {
        this.mapView = mapView
        Log.d(TAG, "Session manager init: ${context.packageName}")
        subscribeToSettings()
        emitState(SessionState.BROWSE)
    }

    fun setMode(mode: Mode) {
        this.mode = mode
        if (state == SessionState.ACTIVE) {
            return
        }
        emitState(SessionState.BROWSE)
    }

    fun setVoiceMode(mode: VoiceModeSetting) {
        voiceModeSetting = mode
        Log.d(TAG, "Voice mode updated: ${mode.storageValue}")
    }

    fun setCentreId(centreId: String?) {
        val normalizedCentreId = centreId?.trim()?.takeIf { it.isNotBlank() }
        val centreChanged = HazardFetchTriggerPolicy.shouldForceOnCentreChange(this.centreId, normalizedCentreId)
        this.centreId = normalizedCentreId
        if (centreChanged && (state == SessionState.PREVIEW || state == SessionState.ACTIVE)) {
            maybeFetchFeatures(force = true)
        }
    }

    fun setPreferredUnits(units: PreferredUnitsSetting) {
        preferredUnitsSetting = units
        Log.d(TAG, "Preferred units updated: ${units.storageValue}")
    }

    fun previewPracticeRoute(route: PracticeRoute) {
        if (mode != Mode.PRACTICE) return
        onPreviewPracticeRoute(route)
        emitState(SessionState.PREVIEW)
    }

    fun previewDestination(dest: Point) {
        if (mode != Mode.NAVIGATION) return
        onPreviewDestination(dest)
        emitState(SessionState.PREVIEW)
    }

    fun start() {
        if (state != SessionState.PREVIEW) return
        featureUnavailableNotified = false
        addObservers()
        maybeFetchFeatures(force = HazardFetchTriggerPolicy.shouldForceOnSessionStart())
        emitState(SessionState.ACTIVE)
    }

    fun stop() {
        removeObservers()
        clearVoiceQueue()
        mapboxNavigation.setNavigationRoutes(emptyList())
        currentFeatures = emptyList()
        hazardSpatialIndex.rebuild(emptyList())
        onFeaturesUpdated(emptyList())
        onPromptEvent(null)
        emitState(SessionState.BROWSE)
    }

    fun onDestroy() {
        stop()
        settingsCollectionJob?.cancel()
        mapView = null
        managerScope.cancel()
        Log.d(TAG, "Session manager destroyed")
    }

    fun onNavigationRoutesUpdated(routes: List<NavigationRoute>) {
        val primaryRoute = routes.firstOrNull()
        if (primaryRoute == null) {
            currentRoutePoints = emptyList()
            currentRouteKey = null
            currentFeatures = emptyList()
            lastFeatureFetchAnchorPoint = null
            hazardSpatialIndex.rebuild(emptyList())
            onFeaturesUpdated(emptyList())
            return
        }

        val previousRouteKey = currentRouteKey
        currentRoutePoints = decodeRoutePoints(primaryRoute)
        currentRouteKey = primaryRoute.directionsRoute.geometry().orEmpty()
        if (!previousRouteKey.isNullOrBlank() && previousRouteKey != currentRouteKey) {
            currentFeatures = emptyList()
            lastFeatureFetchAnchorPoint = null
            hazardSpatialIndex.rebuild(emptyList())
            onFeaturesUpdated(emptyList())
        }
        if (
            (state == SessionState.PREVIEW || state == SessionState.ACTIVE) &&
            !currentRouteKey.isNullOrBlank()
        ) {
            if (lastFeatureFetchRouteKey != currentRouteKey) {
                maybeFetchFeatures(force = true)
            }
        }
    }

    fun onLocationUpdate(
        latitude: Double,
        longitude: Double,
        gpsAccuracyM: Float,
        speedMps: Float
    ) {
        latestLocationLat = latitude
        latestLocationLon = longitude
        latestGpsAccuracyM = gpsAccuracyM
        latestSpeedMps = speedMps

        if (state != SessionState.ACTIVE) return
        if (currentRoutePoints.size < 2 || currentRouteKey.isNullOrBlank()) return
        val currentLocationPoint = Point.fromLngLat(longitude, latitude)
        // Refresh hazard data if the user has moved far from the last fetch anchor.
        if (
            HazardFetchTriggerPolicy.shouldForceOnMovement(
                lastFetchAnchorPoint = lastFeatureFetchAnchorPoint,
                currentLocationPoint = currentLocationPoint
            )
        ) {
            maybeFetchFeatures(force = true)
        }
    }

    private fun addObservers() {
        if (observerRegistry.attached) {
            Log.d(TAG, "ObserverRegistry addObservers skipped (already attached)")
            return
        }
        mapboxNavigation.registerRouteProgressObserver(managedRouteProgressObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        createReplayObserver?.invoke()?.let { replayObserver ->
            observerRegistry.replayObserver = replayObserver
            mapboxNavigation.registerRouteProgressObserver(replayObserver)
        }
        observerRegistry.attached = true
        Log.d(TAG, "ObserverRegistry addObservers attached=1")
    }

    private fun removeObservers() {
        if (!observerRegistry.attached) {
            Log.d(TAG, "ObserverRegistry removeObservers skipped (none attached)")
            return
        }
        mapboxNavigation.unregisterRouteProgressObserver(managedRouteProgressObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        observerRegistry.replayObserver?.let {
            mapboxNavigation.unregisterRouteProgressObserver(it)
        }
        observerRegistry.replayObserver = null
        observerRegistry.attached = false
        Log.d(TAG, "ObserverRegistry removeObservers attached=0")
    }

    private fun subscribeToSettings() {
        if (settingsCollectionJob != null) return
        settingsCollectionJob = managerScope.launch {
            launch {
                settingsRepository.visualPromptsEnabled.collectLatest { enabled ->
                    visualPromptsEnabled = enabled
                    if (!enabled) {
                        onPromptEvent(null)
                    }
                }
            }
            launch {
                settingsRepository.promptSensitivity.collectLatest { sensitivity ->
                    promptSensitivity = sensitivity
                }
            }
        }
    }

    private fun maybeFetchFeatures(force: Boolean) {
        val routePoints = currentRoutePoints
        val routeKey = currentRouteKey
        if (routePoints.isEmpty() || routeKey.isNullOrBlank()) return
        if (featureFetchInProgress) return

        val now = System.currentTimeMillis()
        val stale = now - lastFeatureFetchMs >= FEATURE_FETCH_INTERVAL_MS
        val shouldFetch = force || lastFeatureFetchMs == 0L || stale
        if (!shouldFetch) return

        featureFetchInProgress = true
        managerScope.launch {
            val fetchStartedAt = System.currentTimeMillis()
            try {
                val fetched = hazardRepository.getFeaturesForRoute(
                    routePoints = routePoints,
                    radiusMeters = FEATURE_FETCH_RADIUS_METERS,
                    types = HAZARD_TYPES,
                    centreId = centreId
                )
                currentFeatures = fetched
                hazardSpatialIndex.rebuild(fetched)
                onFeaturesUpdated(fetched)
                lastFeatureFetchMs = System.currentTimeMillis()
                lastFeatureFetchRouteKey = routeKey
                lastFeatureFetchAnchorPoint = currentLocationPointOrNull()
                val durationMs = System.currentTimeMillis() - fetchStartedAt
                Log.d(TAG, "Fetched OSM features: ${fetched.size} in ${durationMs}ms")
                Log.d(TAG, "Hazard type counts: ${fetched.groupingBy { it.type }.eachCount()}")
                if (fetched.isEmpty() && !featureUnavailableNotified) {
                    featureUnavailableNotified = true
                    onFeatureUnavailable()
                } else if (fetched.isNotEmpty()) {
                    featureUnavailableNotified = false
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Feature fetch failed: ${ex.message}")
                if (currentFeatures.isEmpty()) {
                    currentFeatures = emptyList()
                    hazardSpatialIndex.rebuild(emptyList())
                }
                if (!featureUnavailableNotified) {
                    featureUnavailableNotified = true
                    onFeatureUnavailable()
                }
            } finally {
                featureFetchInProgress = false
            }
        }
    }

    private fun evaluatePrompts(progress: com.mapbox.navigation.base.trip.model.RouteProgress) {
        if (state != SessionState.ACTIVE) return
        if (mode != Mode.PRACTICE && mode != Mode.NAVIGATION) return

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPromptEvaluationMs < PROMPT_EVALUATION_INTERVAL_MS) return
        lastPromptEvaluationMs = nowMs
        if (latestSpeedMps < PROMPT_EVALUATION_MIN_SPEED_MPS) return

        if (nowMs - lastFeatureFetchMs >= FEATURE_FETCH_INTERVAL_MS) {
            maybeFetchFeatures(force = false)
        }

        val lat = latestLocationLat ?: return
        val lon = latestLocationLon ?: return
        if (currentFeatures.isEmpty()) return
        val nearbyFeatures = hazardSpatialIndex.queryNearby(
            lat = lat,
            lon = lon,
            radiusMeters = PROMPT_NEARBY_QUERY_RADIUS_METERS
        )
        if (nearbyFeatures.isEmpty()) return

        // Report nearest mini-roundabout distance so the camera can apply a dedicated zoom boost.
        val nearestMiniRoundaboutM = nearbyFeatures
            .filter { it.type == OsmFeatureType.MINI_ROUNDABOUT }
            .minOfOrNull { feature ->
                val dLat = Math.toRadians(feature.lat - lat)
                val dLon = Math.toRadians(feature.lon - lon)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(feature.lat)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            } ?: Double.MAX_VALUE
        onNearestMiniRoundaboutMeters(nearestMiniRoundaboutM)

        // Keep no-entry prevention active even if user disables general hazard prompts.
        val evaluationFeatures = if (visualPromptsEnabled) {
            nearbyFeatures
        } else {
            nearbyFeatures.filter { feature -> feature.type == OsmFeatureType.NO_ENTRY }
        }
        if (evaluationFeatures.isEmpty()) return

        val stepProgress = progress.currentLegProgress?.currentStepProgress
        val evaluationStartedAt = System.currentTimeMillis()
        val prompt = promptEngine.evaluate(
            nowMs = nowMs,
            locationLat = lat,
            locationLon = lon,
            gpsAccuracyM = latestGpsAccuracyM,
            speedMps = latestSpeedMps,
            upcomingManeuverDistanceM = stepProgress?.distanceRemaining?.toDouble(),
            upcomingManeuverTimeS = stepProgress?.durationRemaining?.toDouble(),
            features = evaluationFeatures,
            visualEnabled = true,
            sensitivity = promptSensitivity,
            routePolyline = currentRoutePoints
        )
        val evaluationDurationMs = System.currentTimeMillis() - evaluationStartedAt
        val speedKmh = (latestSpeedMps * 3.6f).roundToInt()
        Log.v(
            TAG,
            "Prompt evaluation took ${evaluationDurationMs}ms candidates=${evaluationFeatures.size} speedKmh=$speedKmh"
        )
        if (prompt != null) {
            onPromptEvent(prompt)
        }
    }

    private fun decodeRoutePoints(route: NavigationRoute): List<Point> {
        val geometry = route.directionsRoute.geometry().orEmpty()
        if (geometry.isBlank()) return emptyList()
        return runCatching {
            LineString.fromPolyline(geometry, 6).coordinates()
        }.recoverCatching {
            LineString.fromPolyline(geometry, 5).coordinates()
        }.getOrDefault(emptyList())
    }

    private fun currentLocationPointOrNull(): Point? {
        val lat = latestLocationLat ?: return null
        val lon = latestLocationLon ?: return null
        return Point.fromLngLat(lon, lat)
    }

    private fun emitState(newState: SessionState) {
        state = newState
        onStateChanged(newState)
    }

    private companion object {
        private const val TAG = "NavigationSessionMgr"
        private const val PROMPT_EVALUATION_INTERVAL_MS = 1_000L
        private const val PROMPT_EVALUATION_MIN_SPEED_MPS = 0.556f // 2 km/h
        private const val PROMPT_NEARBY_QUERY_RADIUS_METERS = 300.0
        private const val HAZARD_GRID_BUCKET_METERS = 200.0
        private const val FEATURE_FETCH_INTERVAL_MS = 10L * 60L * 1000L
        private const val FEATURE_FETCH_RADIUS_METERS = 120

        private val HAZARD_TYPES = setOf(
            OsmFeatureType.ROUNDABOUT,
            OsmFeatureType.MINI_ROUNDABOUT,
            OsmFeatureType.SCHOOL_ZONE,
            OsmFeatureType.ZEBRA_CROSSING,
            OsmFeatureType.GIVE_WAY,
            OsmFeatureType.TRAFFIC_SIGNAL,
            OsmFeatureType.SPEED_CAMERA,
            OsmFeatureType.BUS_LANE,
            OsmFeatureType.BUS_STOP,
            OsmFeatureType.NO_ENTRY
        )
    }
}
