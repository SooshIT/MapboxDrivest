package com.drivest.navigation.prompts

import com.drivest.navigation.geo.RouteProjection
import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.settings.PromptSensitivity
import com.mapbox.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class PromptEngine {

    private val featurePromptState = mutableMapOf<String, FeaturePromptState>()
    private val lastFiredAtByType = mutableMapOf<PromptType, Long>()

    fun evaluate(
        nowMs: Long,
        locationLat: Double,
        locationLon: Double,
        gpsAccuracyM: Float,
        speedMps: Float,
        upcomingManeuverDistanceM: Double?,
        upcomingManeuverTimeS: Double?,
        features: List<OsmFeature>,
        visualEnabled: Boolean,
        sensitivity: PromptSensitivity = PromptSensitivity.STANDARD,
        routePolyline: List<Point> = emptyList()
    ): PromptEvent? {
        if (!visualEnabled) return null
        if (gpsAccuracyM > MAX_GPS_ACCURACY_M) return null
        if (upcomingManeuverTimeS != null && upcomingManeuverTimeS < MANEUVER_TIME_SUPPRESSION_S) return null
        if (upcomingManeuverDistanceM != null && upcomingManeuverDistanceM < MANEUVER_DISTANCE_SUPPRESSION_M) return null
        if (features.isEmpty()) return null

        val locationPoint = Point.fromLngLat(locationLon, locationLat)
        val mphSpeed = speedMps * METERS_PER_SECOND_TO_MPH
        val candidates = mutableListOf<Candidate>()
        for (feature in features) {
            val promptType = feature.type.toPromptType() ?: continue
            if (feature.confidenceHint < VISUAL_MIN_CONFIDENCE_HINT) continue
            val featurePoint = Point.fromLngLat(feature.lon, feature.lat)
            val state = featurePromptState.getOrPut(feature.id) { FeaturePromptState() }
            val directDistance = distanceMeters(
                lat1 = locationLat,
                lon1 = locationLon,
                lat2 = feature.lat,
                lon2 = feature.lon
            )
            val alongAheadDistance = if (routePolyline.size >= 2) {
                RouteProjection.alongDistanceAheadMeters(
                    routePoints = routePolyline,
                    userPoint = locationPoint,
                    featurePoint = featurePoint
                )
            } else {
                null
            }

            if (alongAheadDistance != null) {
                if (alongAheadDistance <= -PASS_RESET_DISTANCE_METERS) {
                    featurePromptState.remove(feature.id)
                    continue
                }
            }

            // Prefer along-route distance when available; fallback to direct distance
            // so prompt evaluation still works if route geometry is temporarily unavailable.
            val distanceAheadMeters: Double = alongAheadDistance ?: directDistance
            if (distanceAheadMeters < 0.0) continue

            val stage = triggerStage(
                promptType = promptType,
                state = state,
                distanceAheadMeters = distanceAheadMeters,
                speedMph = mphSpeed,
                sensitivity = sensitivity
            ) ?: continue

            if (shouldApplyTypeCooldown(promptType)) {
                val lastFiredAt = lastFiredAtByType[promptType]
                if (lastFiredAt != null && nowMs - lastFiredAt < TYPE_COOLDOWN_MS) continue
            }

            candidates += Candidate(
                feature = feature,
                type = promptType,
                stage = stage,
                distanceM = distanceAheadMeters.roundToInt(),
                priority = priority(promptType) + if (stage == TriggerStage.SECONDARY) 1 else 0
            )
        }

        val selected = candidates
            .sortedWith(
                compareByDescending<Candidate> { it.priority }
                    .thenBy { it.distanceM }
            )
            .firstOrNull() ?: return null

        val selectedState = featurePromptState.getOrPut(selected.feature.id) { FeaturePromptState() }
        when (selected.stage) {
            TriggerStage.PRIMARY -> {
                selectedState.primaryFired = true
                if (selectedState.primaryFiredDistanceM == null) {
                    selectedState.primaryFiredDistanceM = selected.distanceM.toDouble()
                }
            }
            TriggerStage.SECONDARY -> {
                selectedState.primaryFired = true
                selectedState.secondaryFired = true
                if (selectedState.primaryFiredDistanceM == null) {
                    selectedState.primaryFiredDistanceM = selected.distanceM.toDouble()
                }
            }
        }
        lastFiredAtByType[selected.type] = nowMs

        return PromptEvent(
            id = "${selected.feature.id}:$nowMs",
            type = selected.type,
            message = messageFor(selected.type),
            featureId = selected.feature.id,
            priority = selected.priority,
            distanceM = selected.distanceM,
            expiresAtEpochMs = nowMs + VISUAL_EXPIRY_MS,
            confidenceHint = selected.feature.confidenceHint
        )
    }

    private fun triggerStage(
        promptType: PromptType,
        state: FeaturePromptState,
        distanceAheadMeters: Double,
        speedMph: Float,
        sensitivity: PromptSensitivity
    ): TriggerStage? {
        return when (promptType) {
            PromptType.BUS_STOP,
            PromptType.TRAFFIC_SIGNAL,
            PromptType.MINI_ROUNDABOUT -> {
                val eligibleForSecondary = (state.primaryFiredDistanceM ?: Double.MAX_VALUE) >
                    SECONDARY_ADVISORY_TRIGGER_METERS
                when {
                    !state.primaryFired && distanceAheadMeters <= PRIMARY_ADVISORY_TRIGGER_METERS ->
                        TriggerStage.PRIMARY
                    state.primaryFired &&
                        !state.secondaryFired &&
                        eligibleForSecondary &&
                        distanceAheadMeters <= SECONDARY_ADVISORY_TRIGGER_METERS &&
                        speedMph > SECONDARY_MIN_SPEED_MPH ->
                        TriggerStage.SECONDARY
                    else -> null
                }
            }
            PromptType.SPEED_CAMERA -> {
                val eligibleForSecondary = (state.primaryFiredDistanceM ?: Double.MAX_VALUE) >
                    SPEED_CAMERA_SECONDARY_TRIGGER_METERS
                when {
                    !state.primaryFired && distanceAheadMeters <= SPEED_CAMERA_PRIMARY_TRIGGER_METERS ->
                        TriggerStage.PRIMARY
                    state.primaryFired &&
                        !state.secondaryFired &&
                        eligibleForSecondary &&
                        distanceAheadMeters <= SPEED_CAMERA_SECONDARY_TRIGGER_METERS ->
                        TriggerStage.SECONDARY
                    else -> null
                }
            }
            else -> {
                if (state.primaryFired) {
                    null
                } else if (distanceAheadMeters <= triggerDistance(promptType, sensitivity)) {
                    TriggerStage.PRIMARY
                } else {
                    null
                }
            }
        }
    }

    private fun shouldApplyTypeCooldown(type: PromptType): Boolean {
        return type != PromptType.BUS_STOP &&
            type != PromptType.TRAFFIC_SIGNAL &&
            type != PromptType.MINI_ROUNDABOUT &&
            type != PromptType.SPEED_CAMERA &&
            type != PromptType.NO_ENTRY
    }

    private fun triggerDistance(type: PromptType, sensitivity: PromptSensitivity): Double {
        val base = when (type) {
            PromptType.ROUNDABOUT -> 250.0
            PromptType.MINI_ROUNDABOUT -> PRIMARY_ADVISORY_TRIGGER_METERS
            PromptType.SCHOOL_ZONE -> 250.0
            PromptType.ZEBRA_CROSSING -> 120.0
            PromptType.GIVE_WAY -> 110.0
            PromptType.TRAFFIC_SIGNAL -> PRIMARY_ADVISORY_TRIGGER_METERS
            PromptType.SPEED_CAMERA -> SPEED_CAMERA_PRIMARY_TRIGGER_METERS
            PromptType.BUS_LANE -> 250.0
            PromptType.BUS_STOP -> PRIMARY_ADVISORY_TRIGGER_METERS
            PromptType.NO_ENTRY -> NO_ENTRY_TRIGGER_METERS
        }
        if (type == PromptType.NO_ENTRY) return base
        val multiplier = when (sensitivity) {
            PromptSensitivity.MINIMAL -> 0.80
            PromptSensitivity.STANDARD -> 1.0
            PromptSensitivity.EXTRA_HELP -> 1.25
        }
        return base * multiplier
    }

    private fun priority(type: PromptType): Int {
        return when (type) {
            PromptType.NO_ENTRY -> 7
            PromptType.ROUNDABOUT -> 6
            PromptType.MINI_ROUNDABOUT -> 5
            PromptType.SPEED_CAMERA -> 5
            PromptType.SCHOOL_ZONE -> 4
            PromptType.ZEBRA_CROSSING -> 3
            PromptType.GIVE_WAY -> 3
            PromptType.TRAFFIC_SIGNAL -> 2
            PromptType.BUS_LANE -> 1
            PromptType.BUS_STOP -> 2
        }
    }

    private fun messageFor(type: PromptType): String {
        return when (type) {
            PromptType.NO_ENTRY -> "No entry ahead. Rerouting."
            PromptType.ROUNDABOUT -> "Advisory: roundabout ahead"
            PromptType.MINI_ROUNDABOUT -> "Advisory: mini roundabout ahead"
            PromptType.SCHOOL_ZONE -> "Advisory: school zone ahead"
            PromptType.ZEBRA_CROSSING -> "Advisory: zebra crossing ahead"
            PromptType.GIVE_WAY -> "Advisory: give way ahead"
            PromptType.TRAFFIC_SIGNAL -> "Advisory: traffic lights ahead"
            PromptType.SPEED_CAMERA -> "Advisory: speed camera ahead"
            PromptType.BUS_LANE -> "Advisory: bus lane ahead"
            PromptType.BUS_STOP -> "Advisory: bus stop ahead"
        }
    }

    private fun OsmFeatureType.toPromptType(): PromptType? {
        return when (this) {
            OsmFeatureType.ROUNDABOUT -> PromptType.ROUNDABOUT
            OsmFeatureType.MINI_ROUNDABOUT -> PromptType.MINI_ROUNDABOUT
            OsmFeatureType.SCHOOL_ZONE -> PromptType.SCHOOL_ZONE
            OsmFeatureType.ZEBRA_CROSSING -> PromptType.ZEBRA_CROSSING
            OsmFeatureType.GIVE_WAY -> PromptType.GIVE_WAY
            OsmFeatureType.TRAFFIC_SIGNAL -> PromptType.TRAFFIC_SIGNAL
            OsmFeatureType.SPEED_CAMERA -> PromptType.SPEED_CAMERA
            OsmFeatureType.BUS_LANE -> PromptType.BUS_LANE
            OsmFeatureType.BUS_STOP -> PromptType.BUS_STOP
            OsmFeatureType.NO_ENTRY -> PromptType.NO_ENTRY
        }
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    private data class Candidate(
        val feature: OsmFeature,
        val type: PromptType,
        val stage: TriggerStage,
        val distanceM: Int,
        val priority: Int
    )

    private data class FeaturePromptState(
        var primaryFired: Boolean = false,
        var secondaryFired: Boolean = false,
        var primaryFiredDistanceM: Double? = null
    )

    private enum class TriggerStage {
        PRIMARY,
        SECONDARY
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
        const val MAX_GPS_ACCURACY_M = 25f
        const val MANEUVER_TIME_SUPPRESSION_S = 6.0
        const val MANEUVER_DISTANCE_SUPPRESSION_M = 80.0
        const val TYPE_COOLDOWN_MS = 60_000L
        const val VISUAL_EXPIRY_MS = 6_000L
        const val VISUAL_MIN_CONFIDENCE_HINT = 0.60f
        const val PRIMARY_ADVISORY_TRIGGER_METERS = 120.0
        const val SECONDARY_ADVISORY_TRIGGER_METERS = 50.0
        const val SPEED_CAMERA_PRIMARY_TRIGGER_METERS = 250.0
        const val SPEED_CAMERA_SECONDARY_TRIGGER_METERS = 80.0
        const val NO_ENTRY_TRIGGER_METERS = 60.0
        const val PASS_RESET_DISTANCE_METERS = 80.0
        const val SECONDARY_MIN_SPEED_MPH = 15.0f
        const val METERS_PER_SECOND_TO_MPH = 2.2369363f
    }
}
