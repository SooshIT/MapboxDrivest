package com.drivest.navigation.prompts

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptEngineDistanceGatingTest {

    private val baseLon = 0.10000
    private val featureLat = 51.50100
    private val routePolyline = listOf(
        Point.fromLngLat(baseLon, 51.49950),
        Point.fromLngLat(baseLon, 51.50300)
    )

    @Test
    fun busStopTriggersAt120ThenAgainAt50WhenSpeedAbove15Mph() {
        val engine = PromptEngine()
        val busStop = feature("bus_1", OsmFeatureType.BUS_STOP, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 10f,
            features = listOf(busStop)
        )
        assertNotNull(firstPrompt)
        assertEquals(PromptType.BUS_STOP, firstPrompt?.type)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50060,
            speedMps = 8f, // ~17.9 mph
            features = listOf(busStop)
        )
        assertNotNull(secondPrompt)
        assertEquals(PromptType.BUS_STOP, secondPrompt?.type)
    }

    @Test
    fun trafficLightSecondPromptBlockedBelow15Mph() {
        val engine = PromptEngine()
        val trafficLight = feature("tl_1", OsmFeatureType.TRAFFIC_SIGNAL, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 10f,
            features = listOf(trafficLight)
        )
        assertNotNull(firstPrompt)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50055,
            speedMps = 5f, // ~11.2 mph
            features = listOf(trafficLight)
        )
        assertNull(secondPrompt)
    }

    @Test
    fun trafficLightTriggersAt120ThenAgainAt50WhenSpeedAbove15Mph() {
        val engine = PromptEngine()
        val trafficLight = feature("tl_speed", OsmFeatureType.TRAFFIC_SIGNAL, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 9f,
            features = listOf(trafficLight)
        )
        assertNotNull(firstPrompt)
        assertEquals(PromptType.TRAFFIC_SIGNAL, firstPrompt?.type)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50060,
            speedMps = 8f, // ~17.9 mph
            features = listOf(trafficLight)
        )
        assertNotNull(secondPrompt)
        assertEquals(PromptType.TRAFFIC_SIGNAL, secondPrompt?.type)
    }

    @Test
    fun miniRoundaboutTriggersAt120ThenAgainAt50WhenSpeedAbove15Mph() {
        val engine = PromptEngine()
        val miniRoundabout = feature("mini_1", OsmFeatureType.MINI_ROUNDABOUT, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 9f,
            features = listOf(miniRoundabout)
        )
        assertNotNull(firstPrompt)
        assertEquals(PromptType.MINI_ROUNDABOUT, firstPrompt?.type)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50060,
            speedMps = 8f, // ~17.9 mph
            features = listOf(miniRoundabout)
        )
        assertNotNull(secondPrompt)
        assertEquals(PromptType.MINI_ROUNDABOUT, secondPrompt?.type)
    }

    @Test
    fun miniRoundaboutSecondPromptBlockedBelow15Mph() {
        val engine = PromptEngine()
        val miniRoundabout = feature("mini_slow", OsmFeatureType.MINI_ROUNDABOUT, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 9f,
            features = listOf(miniRoundabout)
        )
        assertNotNull(firstPrompt)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50060,
            speedMps = 5f, // ~11.2 mph
            features = listOf(miniRoundabout)
        )
        assertNull(secondPrompt)
    }

    @Test
    fun closelySpacedMiniRoundaboutsStillPromptIndependentlyWithinTypeCooldownWindow() {
        val engine = PromptEngine()
        val firstMini = feature("mini_first", OsmFeatureType.MINI_ROUNDABOUT, 51.50100)
        val secondMini = feature("mini_second", OsmFeatureType.MINI_ROUNDABOUT, 51.50210)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50000,
            speedMps = 9f,
            features = listOf(firstMini, secondMini)
        )
        assertNotNull(firstPrompt)
        assertEquals("mini_first", firstPrompt?.featureId)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 11_000L, // still inside legacy 60s type cooldown window
            locationLat = 51.50110,
            speedMps = 9f,
            features = listOf(firstMini, secondMini)
        )
        assertNotNull(secondPrompt)
        assertEquals(PromptType.MINI_ROUNDABOUT, secondPrompt?.type)
        assertEquals("mini_second", secondPrompt?.featureId)
    }

    @Test
    fun noAdvisoryBefore120mForBusStopAndTrafficLight() {
        val engine = PromptEngine()
        val busStop = feature("bus_far", OsmFeatureType.BUS_STOP, featureLat)
        val trafficLight = feature("tl_far", OsmFeatureType.TRAFFIC_SIGNAL, featureLat)

        val busPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.49975, // >120m away
            speedMps = 10f,
            features = listOf(busStop)
        )
        val lightsPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.49975, // >120m away
            speedMps = 10f,
            features = listOf(trafficLight)
        )

        assertNull(busPrompt)
        assertNull(lightsPrompt)
    }

    @Test
    fun noEntryTriggersAt60mAndNotBefore() {
        val engine = PromptEngine()
        val noEntry = feature("no_entry_1", OsmFeatureType.NO_ENTRY, featureLat)

        val tooEarlyPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50035, // ~72m away
            speedMps = 9f,
            features = listOf(noEntry)
        )
        assertNull(tooEarlyPrompt)

        val warningPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50050, // ~55m away
            speedMps = 9f,
            features = listOf(noEntry)
        )
        assertNotNull(warningPrompt)
        assertEquals(PromptType.NO_ENTRY, warningPrompt?.type)
        assertEquals("No entry ahead. Rerouting.", warningPrompt?.message)
    }

    @Test
    fun speedCameraTriggersAt250ThenAgainAt80() {
        val engine = PromptEngine()
        val camera = feature("camera_1", OsmFeatureType.SPEED_CAMERA, 51.50230)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50007,
            speedMps = 6f,
            features = listOf(camera)
        )
        assertNotNull(firstPrompt)
        assertEquals(PromptType.SPEED_CAMERA, firstPrompt?.type)

        val secondPrompt = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50160,
            speedMps = 4f,
            features = listOf(camera)
        )
        assertNotNull(secondPrompt)
        assertEquals(PromptType.SPEED_CAMERA, secondPrompt?.type)
    }

    @Test
    fun speedCameraDoesNotTriggerBefore250() {
        val engine = PromptEngine()
        val camera = feature("camera_far", OsmFeatureType.SPEED_CAMERA, 51.50230)

        val prompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.49990,
            speedMps = 8f,
            features = listOf(camera)
        )
        assertNull(prompt)
    }

    @Test
    fun noImmediateSecondPromptWhenFirstSeenInside50m() {
        val engine = PromptEngine()
        val busStop = feature("bus_inside_50", OsmFeatureType.BUS_STOP, featureLat)

        val firstPrompt = evaluate(
            engine = engine,
            nowMs = 1_000L,
            locationLat = 51.50070, // roughly 33m before feature
            speedMps = 8f,
            features = listOf(busStop)
        )
        assertNotNull(firstPrompt)

        val immediateRetry = evaluate(
            engine = engine,
            nowMs = 2_000L,
            locationLat = 51.50072,
            speedMps = 8f,
            features = listOf(busStop)
        )
        assertNull(immediateRetry)
    }

    @Test
    fun featureResetsAfter80mPastAndCanPromptAgainOnNextPass() {
        val engine = PromptEngine()
        val busStop = feature("bus_reset", OsmFeatureType.BUS_STOP, featureLat)

        assertNotNull(
            evaluate(
                engine = engine,
                nowMs = 1_000L,
                locationLat = 51.50000,
                speedMps = 10f,
                features = listOf(busStop)
            )
        )

        // User is >80m past the feature; this pass should reset internal dedupe for this feature id.
        assertNull(
            evaluate(
                engine = engine,
                nowMs = 2_000L,
                locationLat = 51.50185,
                speedMps = 10f,
                features = listOf(busStop)
            )
        )

        // Simulate a new approach pass from before the feature.
        val reprompt = evaluate(
            engine = engine,
            nowMs = 3_000L,
            locationLat = 51.50000,
            speedMps = 10f,
            features = listOf(busStop)
        )
        assertNotNull(reprompt)
        assertEquals(PromptType.BUS_STOP, reprompt?.type)
    }

    private fun evaluate(
        engine: PromptEngine,
        nowMs: Long,
        locationLat: Double,
        speedMps: Float,
        features: List<OsmFeature>
    ): PromptEvent? {
        return engine.evaluate(
            nowMs = nowMs,
            locationLat = locationLat,
            locationLon = baseLon,
            gpsAccuracyM = 5f,
            speedMps = speedMps,
            upcomingManeuverDistanceM = null,
            upcomingManeuverTimeS = null,
            features = features,
            visualEnabled = true,
            routePolyline = routePolyline
        )
    }

    private fun feature(id: String, type: OsmFeatureType, lat: Double): OsmFeature {
        return OsmFeature(
            id = id,
            type = type,
            lat = lat,
            lon = baseLon,
            tags = emptyMap(),
            source = "test",
            confidenceHint = 1.0f
        )
    }
}
