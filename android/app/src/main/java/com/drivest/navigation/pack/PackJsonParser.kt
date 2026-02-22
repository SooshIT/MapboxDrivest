package com.drivest.navigation.pack

import com.drivest.navigation.osm.OsmFeature
import com.drivest.navigation.osm.OsmFeatureType
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.practice.PracticeRoutePoint
import org.json.JSONObject

object PackJsonParser {

    fun parseRoutesPack(json: String): RoutesPack? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val metadata = parseMetadata(root.optJSONObject("metadata")) ?: return null
        val centreId = root.optString("centreId").ifBlank { return null }
        val routesArray = root.optJSONArray("routes") ?: return null

        val routes = buildList {
            for (index in 0 until routesArray.length()) {
                val routeJson = routesArray.optJSONObject(index) ?: continue
                val geometryArray = routeJson.optJSONArray("geometry")
                val geometry = buildList {
                    if (geometryArray != null) {
                        for (g in 0 until geometryArray.length()) {
                            val point = geometryArray.optJSONObject(g) ?: continue
                            add(
                                PracticeRoutePoint(
                                    lat = point.optDouble("lat", 0.0),
                                    lon = point.optDouble("lon", 0.0)
                                )
                            )
                        }
                    }
                }
                add(
                    PracticeRoute(
                        id = routeJson.optString("id", ""),
                        name = routeJson.optString("name", ""),
                        geometry = geometry,
                        distanceM = routeJson.optDouble("distanceM", 0.0),
                        durationS = routeJson.optDouble("durationS", 0.0),
                        startLat = routeJson.optDouble("startLat", geometry.firstOrNull()?.lat ?: 0.0),
                        startLon = routeJson.optDouble("startLon", geometry.firstOrNull()?.lon ?: 0.0)
                    )
                )
            }
        }

        return RoutesPack(
            metadata = metadata,
            centreId = centreId,
            routes = routes.filter { it.id.isNotBlank() }
        )
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
}
