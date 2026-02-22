package com.drivest.navigation.osm

import org.json.JSONArray
import org.json.JSONObject

object OverpassParser {

    fun parse(json: String): List<OverpassElement> {
        val root = JSONObject(json)
        val elements = root.optJSONArray("elements") ?: JSONArray()
        val parsed = mutableListOf<OverpassElement>()

        for (index in 0 until elements.length()) {
            val rawElement = elements.optJSONObject(index) ?: continue
            val type = rawElement.optString("type")
            val id = rawElement.optLong("id", -1L)
            if (id <= 0L || type.isBlank()) continue

            val tags = parseTags(rawElement.optJSONObject("tags"))
            when (type) {
                "node" -> {
                    val lat = rawElement.optDoubleOrNull("lat")
                    val lon = rawElement.optDoubleOrNull("lon")
                    parsed.add(
                        OverpassElement(
                            id = id,
                            elementType = type,
                            lat = lat,
                            lon = lon,
                            centroidLat = null,
                            centroidLon = null,
                            tags = tags
                        )
                    )
                }

                "way" -> {
                    val centroid = parseCenter(rawElement) ?: computeCentroid(rawElement.optJSONArray("geometry")) ?: continue
                    parsed.add(
                        OverpassElement(
                            id = id,
                            elementType = type,
                            lat = null,
                            lon = null,
                            centroidLat = centroid.first,
                            centroidLon = centroid.second,
                            tags = tags
                        )
                    )
                }

                "relation" -> {
                    val centroid = parseCenter(rawElement) ?: computeCentroid(rawElement.optJSONArray("geometry")) ?: continue
                    parsed.add(
                        OverpassElement(
                            id = id,
                            elementType = type,
                            lat = null,
                            lon = null,
                            centroidLat = centroid.first,
                            centroidLon = centroid.second,
                            tags = tags
                        )
                    )
                }
            }
        }

        return parsed
    }

    private fun parseCenter(rawElement: JSONObject): Pair<Double, Double>? {
        val center = rawElement.optJSONObject("center") ?: return null
        val lat = center.optDoubleOrNull("lat") ?: return null
        val lon = center.optDoubleOrNull("lon") ?: return null
        return Pair(lat, lon)
    }

    private fun parseTags(rawTags: JSONObject?): Map<String, String> {
        if (rawTags == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        val keys = rawTags.keys()
        while (keys.hasNext()) {
            val key = keys.next() ?: continue
            result[key] = rawTags.optString(key, "")
        }
        return result
    }

    private fun computeCentroid(geometry: JSONArray?): Pair<Double, Double>? {
        if (geometry == null || geometry.length() == 0) return null
        var latSum = 0.0
        var lonSum = 0.0
        var count = 0

        for (i in 0 until geometry.length()) {
            val point = geometry.optJSONObject(i) ?: continue
            val lat = point.optDoubleOrNull("lat") ?: continue
            val lon = point.optDoubleOrNull("lon") ?: continue
            latSum += lat
            lonSum += lon
            count += 1
        }
        if (count == 0) return null

        return Pair(latSum / count, lonSum / count)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key)) return null
        val value = optDouble(key)
        return if (value.isNaN()) null else value
    }
}
