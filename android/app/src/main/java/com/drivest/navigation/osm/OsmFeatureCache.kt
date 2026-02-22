package com.drivest.navigation.osm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class OsmFeatureCache(
    context: Context,
    private val ttlMs: Long = DEFAULT_TTL_MS
) {

    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    suspend fun get(key: String): List<OsmFeature>? = withContext(Dispatchers.IO) {
        val file = cacheFile(key)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val payload = JSONObject(file.readText())
            val createdAtMs = payload.optLong(KEY_CREATED_AT_MS, 0L)
            if (createdAtMs <= 0L || System.currentTimeMillis() - createdAtMs > ttlMs) {
                file.delete()
                null
            } else {
                parseFeatures(payload.optJSONArray(KEY_FEATURES))
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    suspend fun put(key: String, features: List<OsmFeature>) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put(KEY_CREATED_AT_MS, System.currentTimeMillis())
            put(KEY_FEATURES, serializeFeatures(features))
        }
        cacheFile(key).writeText(payload.toString())
    }

    private fun cacheFile(key: String): File {
        val fileName = sha256Hex(key)
        return File(cacheDir, "$fileName.json")
    }

    private fun serializeFeatures(features: List<OsmFeature>): JSONArray {
        val array = JSONArray()
        for (feature in features) {
            val tags = JSONObject()
            feature.tags.forEach { (k, v) -> tags.put(k, v) }

            val item = JSONObject()
                .put("id", feature.id)
                .put("type", feature.type.name)
                .put("lat", feature.lat)
                .put("lon", feature.lon)
                .put("source", feature.source)
                .put("confidenceHint", feature.confidenceHint.toDouble())
                .put("tags", tags)

            array.put(item)
        }
        return array
    }

    private fun parseFeatures(featuresArray: JSONArray?): List<OsmFeature> {
        if (featuresArray == null) return emptyList()
        val features = mutableListOf<OsmFeature>()
        for (i in 0 until featuresArray.length()) {
            val item = featuresArray.optJSONObject(i) ?: continue
            val typeName = item.optString("type")
            val type = runCatching { OsmFeatureType.valueOf(typeName) }.getOrNull() ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lon = item.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val tagsJson = item.optJSONObject("tags")
            val tags = mutableMapOf<String, String>()
            if (tagsJson != null) {
                val keys = tagsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next() ?: continue
                    tags[key] = tagsJson.optString(key, "")
                }
            }

            features += OsmFeature(
                id = item.optString("id", ""),
                type = type,
                lat = lat,
                lon = lon,
                tags = tags,
                source = item.optString("source", "cache"),
                confidenceHint = item.optDouble("confidenceHint", 0.5).toFloat()
            )
        }
        return features
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private companion object {
        const val DEFAULT_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        const val CACHE_DIR_NAME = "osm_features"
        const val KEY_CREATED_AT_MS = "createdAtEpochMs"
        const val KEY_FEATURES = "features"
    }
}
