package com.drivest.navigation.prompts

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptEngineTest {

    private val baseLat = 51.50000
    private val baseLon = 0.10000

    @Test
    fun cooldownEnforcementByType() {
        val engine = PromptEngine()
        val now = 1_000L
        val first = feature("z_1", OsmFeatureType.ZEBRA_CROSSING, latOffset = 0.0009)
        val second = feature("z_2", OsmFeatureType.ZEBRA_CROSSING, latOffset = 0.0010)

        assertNotNull(evaluate(engine, nowMs = now, features = listOf(first)))
        assertNull(evaluate(engine, nowMs = now + 30_000L, features = listOf(second)))
        assertNotNull(evaluate(engine, nowMs = now + 61_000L, features = listOf(second)))
    }

    @Test
    fun dedupByFeatureId() {
        val engine = PromptEngine()
        val now = 10_000L
        val signal = feature("same_signal", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0010)

        assertNotNull(evaluate(engine, nowMs = now, features = listOf(signal)))
        assertNull(evaluate(engine, nowMs = now + 65_000L, features = listOf(signal)))
    }

    @Test
    fun suppressionByUpcomingManeuverTime() {
        val engine = PromptEngine()
        val signal = feature("tl_1", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0010)

        val prompt = evaluate(
            engine,
            nowMs = 5_000L,
            features = listOf(signal),
            upcomingManeuverTimeS = 5.0
        )
        assertNull(prompt)
    }

    @Test
    fun suppressionByUpcomingManeuverDistance() {
        val engine = PromptEngine()
        val signal = feature("tl_1", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0010)

        val prompt = evaluate(
            engine,
            nowMs = 5_000L,
            features = listOf(signal),
            upcomingManeuverDistanceM = 60.0
        )
        assertNull(prompt)
    }

    @Test
    fun gpsAccuracyBlocksPrompts() {
        val engine = PromptEngine()
        val signal = feature("tl_1", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0010)

        val prompt = evaluate(
            engine,
            nowMs = 5_000L,
            features = listOf(signal),
            gpsAccuracyM = 26f
        )
        assertNull(prompt)
    }

    @Test
    fun highestPrioritySelectedBeforeNearest() {
        val engine = PromptEngine()
        val busLaneNearby = feature("bus_1", OsmFeatureType.BUS_LANE, latOffset = 0.0003)
        val roundaboutFurther = feature("rb_1", OsmFeatureType.ROUNDABOUT, latOffset = 0.0018)

        val prompt = evaluate(
            engine,
            nowMs = 20_000L,
            features = listOf(busLaneNearby, roundaboutFurther)
        )
        assertNotNull(prompt)
        assertEquals(PromptType.ROUNDABOUT, prompt?.type)
        assertEquals("rb_1", prompt?.featureId)
    }

    @Test
    fun nearestFeatureSelectedWithinSamePriority() {
        val engine = PromptEngine()
        val near = feature("tl_near", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0008)
        val far = feature("tl_far", OsmFeatureType.TRAFFIC_SIGNAL, latOffset = 0.0013)

        val prompt = evaluate(
            engine,
            nowMs = 30_000L,
            features = listOf(far, near)
        )
        assertNotNull(prompt)
        assertEquals("tl_near", prompt?.featureId)
    }

    private fun evaluate(
        engine: PromptEngine,
        nowMs: Long,
        features: List<OsmFeature>,
        gpsAccuracyM: Float = 5f,
        upcomingManeuverDistanceM: Double? = null,
        upcomingManeuverTimeS: Double? = null
    ): PromptEvent? {
        return engine.evaluate(
            nowMs = nowMs,
            locationLat = baseLat,
            locationLon = baseLon,
            gpsAccuracyM = gpsAccuracyM,
            speedMps = 10f,
            upcomingManeuverDistanceM = upcomingManeuverDistanceM,
            upcomingManeuverTimeS = upcomingManeuverTimeS,
            features = features,
            visualEnabled = true
        )
    }

    private fun feature(
        id: String,
        type: OsmFeatureType,
        latOffset: Double
    ): OsmFeature {
        return OsmFeature(
            id = id,
            type = type,
            lat = baseLat + latOffset,
            lon = baseLon,
            tags = emptyMap(),
            source = "test",
            confidenceHint = 1.0f
        )
    }
}
