package com.drivest.navigation.pack

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.practice.PracticeRoutePoint
import org.json.JSONArray
import org.json.JSONObject

object PackJsonParser {

    fun parseRoutesPack(json: String): RoutesPack? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return parseLegacyRoutesPack(root) ?: parseBackendRoutesResponse(root)
    }

    fun parseHazardsPack(json: String): HazardsPack? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val metadata = parseMetadata(root.optJSONObject("metadata")) ?: return null
        val centreId = root.optString("centreId").ifBlank { return null }
        val hazardsArray = root.optJSONArray("hazards") ?: return null

        val hazards = buildList {
            for (index in 0 until hazardsArray.length()) {
                val hazardJson = hazardsArray.optJSONObject(index) ?: continue
                val typeString = hazardJson.optString("type", "")
                val type = runCatching { OsmFeatureType.valueOf(typeString) }.getOrNull() ?: continue
                val lat = hazardJson.optDouble("lat", Double.NaN)
                val lon = hazardJson.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val tagsJson = hazardJson.optJSONObject("tags")
                val tags = mutableMapOf<String, String>()
                if (tagsJson != null) {
                    val keys = tagsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next() ?: continue
                        tags[key] = tagsJson.optString(key, "")
                    }
                }

                add(
                    OsmFeature(
                        id = hazardJson.optString("id", ""),
                        type = type,
                        lat = lat,
                        lon = lon,
                        tags = tags,
                        source = hazardJson.optString("source", "pack"),
                        confidenceHint = hazardJson.optDouble("confidenceHint", 0.5).toFloat()
                    )
                )
            }
        }

        return HazardsPack(
            metadata = metadata,
            centreId = centreId,
            hazards = hazards.filter { it.id.isNotBlank() }
        )
    }

    private fun parseMetadata(metadataJson: JSONObject?): PackMetadata? {
        metadataJson ?: return null
        val version = metadataJson.optString("version").ifBlank { return null }
        val generatedAt = metadataJson.optString("generatedAt").ifBlank { return null }
        val bboxJson = metadataJson.optJSONObject("bbox") ?: return null
        val bbox = PackBbox(
            south = bboxJson.optDouble("south", Double.NaN),
            west = bboxJson.optDouble("west", Double.NaN),
            north = bboxJson.optDouble("north", Double.NaN),
            east = bboxJson.optDouble("east", Double.NaN)
        )
        if (
            bbox.south.isNaN() ||
            bbox.west.isNaN() ||
            bbox.north.isNaN() ||
            bbox.east.isNaN()
        ) {
            return null
        }
        return PackMetadata(
            version = version,
            generatedAt = generatedAt,
            bbox = bbox
        )
    }

    private fun parseLegacyRoutesPack(root: JSONObject): RoutesPack? {
        val metadata = parseMetadata(root.optJSONObject("metadata")) ?: return null
        val centreId = root.optString("centreId").ifBlank { return null }
        val routesArray = root.optJSONArray("routes") ?: return null
        val routes = parseRoutesArray(routesArray)
        return RoutesPack(
            metadata = metadata,
            centreId = centreId,
            routes = routes
        )
    }

    private fun parseBackendRoutesResponse(root: JSONObject): RoutesPack? {
        val routesArray = when {
            root.optJSONArray("data") != null -> root.optJSONArray("data")
            root.optJSONObject("data")?.optJSONArray("items") != null ->
                root.optJSONObject("data")?.optJSONArray("items")
            else -> null
        } ?: return null

        val routes = parseRoutesArray(routesArray)
        if (routes.isEmpty()) {
            // Treat an empty backend response as a valid empty pack so callers can fall back cleanly.
            val centreId = root.optString("centreId").ifBlank {
                routesArray.optJSONObject(0)?.optString("centreId").orEmpty()
            }.ifBlank { "" }
            return RoutesPack(
                metadata = synthesizeRoutesMetadata(root, routes),
                centreId = centreId,
                routes = emptyList()
            )
        }

        val centreId = root.optString("centreId").ifBlank {
            (0 until routesArray.length())
                .asSequence()
                .mapNotNull { index -> routesArray.optJSONObject(index)?.optString("centreId") }
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }.ifBlank { return null }

        return RoutesPack(
            metadata = parseMetadata(root.optJSONObject("metadata")) ?: synthesizeRoutesMetadata(root, routes),
            centreId = centreId,
            routes = routes
        )
    }

    private fun parseRoutesArray(routesArray: JSONArray): List<PracticeRoute> {
        return buildList {
            for (index in 0 until routesArray.length()) {
                val routeJson = routesArray.optJSONObject(index) ?: continue
                val geometry = parseRouteGeometry(routeJson)
                val startPoint = geometry.firstOrNull()
                val routeId = routeJson.optString("id", "").trim()
                val routeName = routeJson.optString("name", "").trim().ifBlank {
                    "Route ${index + 1}"
                }
                val durationS = routeJson.optDouble(
                    "durationS",
                    routeJson.optDouble("durationEstS", 0.0)
                )
                add(
                    PracticeRoute(
                        id = routeId,
                        name = routeName,
                        geometry = geometry,
                        distanceM = routeJson.optDouble("distanceM", 0.0),
                        durationS = durationS,
                        startLat = routeJson.optDouble("startLat", startPoint?.lat ?: 0.0),
                        startLon = routeJson.optDouble("startLon", startPoint?.lon ?: 0.0)
                    )
                )
            }
        }.filter { route ->
            route.id.isNotBlank() && route.geometry.isNotEmpty()
        }
    }

    private fun parseRouteGeometry(routeJson: JSONObject): List<PracticeRoutePoint> {
        routeJson.optJSONArray("geometry")?.let { geometryArray ->
            val parsed = buildList {
                for (g in 0 until geometryArray.length()) {
                    val point = geometryArray.optJSONObject(g) ?: continue
                    val lat = point.optDouble("lat", Double.NaN)
                    val lon = point.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    add(PracticeRoutePoint(lat = lat, lon = lon))
                }
            }
            if (parsed.isNotEmpty()) return parsed
        }

        routeJson.optJSONArray("coordinates")?.let { coordinates ->
            val parsed = parseCoordinatePairs(coordinates)
            if (parsed.isNotEmpty()) return parsed
        }

        val polylineRaw = routeJson.optString("polyline", "").trim()
        if (polylineRaw.startsWith("[")) {
            val parsed = runCatching { JSONArray(polylineRaw) }
                .getOrNull()
                ?.let(::parseCoordinatePairs)
                .orEmpty()
            if (parsed.isNotEmpty()) return parsed
        }

        return emptyList()
    }

    private fun parseCoordinatePairs(coordinates: JSONArray): List<PracticeRoutePoint> {
        return buildList {
            for (i in 0 until coordinates.length()) {
                val pair = coordinates.optJSONArray(i) ?: continue
                if (pair.length() < 2) continue
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                add(PracticeRoutePoint(lat = lat, lon = lon))
            }
        }
    }

    private fun synthesizeRoutesMetadata(root: JSONObject, routes: List<PracticeRoute>): PackMetadata {
        val generatedAt = root.optJSONObject("meta")?.optString("generatedAt").orEmpty()
        return PackMetadata(
            version = "routes-api-v2-compat",
            generatedAt = generatedAt,
            bbox = computeRoutesBbox(routes)
        )
    }

    private fun computeRoutesBbox(routes: List<PracticeRoute>): PackBbox {
        val points = routes.flatMap { it.geometry }
        if (points.isEmpty()) {
            return PackBbox(south = 0.0, west = 0.0, north = 0.0, east = 0.0)
        }
        var south = Double.POSITIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        var north = Double.NEGATIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            if (point.lat < south) south = point.lat
            if (point.lat > north) north = point.lat
            if (point.lon < west) west = point.lon
            if (point.lon > east) east = point.lon
        }
        return PackBbox(
            south = south,
            west = west,
            north = north,
            east = east
        )
    }
}
