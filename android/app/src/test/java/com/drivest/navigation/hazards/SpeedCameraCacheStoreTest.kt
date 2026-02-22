package com.drivest.navigation.hazards

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.mapbox.geojson.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SpeedCameraCacheStoreTest {

    @Test
    fun writeThenReadWithinTtlReturnsSpeedCameras() {
        val root = Files.createTempDirectory("speed_camera_cache_test").toFile()
        var nowMs = 1_000L
        val store = SpeedCameraCacheStore(
            cacheDir = root,
            nowProvider = { nowMs },
            ttlMs = 24L * 60L * 60L * 1000L
        )
        val route = sampleRoute()
        val camera = feature("cam_1", OsmFeatureType.SPEED_CAMERA)

        store.write(
            centreId = "colchester",
            routePoints = route,
            features = listOf(camera)
        )
        nowMs += 1_000L

        val cached = store.read(
            centreId = "colchester",
            routePoints = route
        )
        assertEquals(1, cached.size)
        assertEquals(OsmFeatureType.SPEED_CAMERA, cached.first().type)
    }

    @Test
    fun expiredCacheReturnsEmpty() {
        val root = Files.createTempDirectory("speed_camera_cache_expired").toFile()
        var nowMs = 1_000L
        val ttlMs = 24L * 60L * 60L * 1000L
        val store = SpeedCameraCacheStore(
            cacheDir = root,
            nowProvider = { nowMs },
            ttlMs = ttlMs
        )
        val route = sampleRoute()
        val camera = feature("cam_expired", OsmFeatureType.SPEED_CAMERA)

        store.write(
            centreId = "colchester",
            routePoints = route,
            features = listOf(camera)
        )
        nowMs += ttlMs + 10_000L

        val cached = store.read(
            centreId = "colchester",
            routePoints = route
        )
        assertTrue(cached.isEmpty())
    }

    @Test
    fun writeIgnoresNonCameraFeatures() {
        val root = Files.createTempDirectory("speed_camera_cache_non_camera").toFile()
        val store = SpeedCameraCacheStore(
            cacheDir = root,
            nowProvider = { 1_000L },
            ttlMs = 24L * 60L * 60L * 1000L
        )
        val route = sampleRoute()
        val trafficSignal = feature("tl_1", OsmFeatureType.TRAFFIC_SIGNAL)

        store.write(
            centreId = "colchester",
            routePoints = route,
            features = listOf(trafficSignal)
        )

        val cached = store.read(
            centreId = "colchester",
            routePoints = route
        )
        assertTrue(cached.isEmpty())
    }

    private fun sampleRoute(): List<Point> {
        return listOf(
            Point.fromLngLat(0.1000, 51.5000),
            Point.fromLngLat(0.1000, 51.5200),
            Point.fromLngLat(0.1000, 51.5400)
        )
    }

    private fun feature(id: String, type: OsmFeatureType): OsmFeature {
        return OsmFeature(
            id = id,
            type = type,
            lat = 51.5100,
            lon = 0.1000,
            tags = mapOf("highway" to "speed_camera"),
            source = "test",
            confidenceHint = 1.0f
        )
    }
}
