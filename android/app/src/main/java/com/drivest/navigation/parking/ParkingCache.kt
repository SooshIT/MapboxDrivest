package com.drivest.navigation.parking

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ParkingCache(
    context: Context,
    private val ttlMs: Long = DEFAULT_TTL_MS
) {

    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    suspend fun get(key: String): List<ParkingSpotRecord>? = withContext(Dispatchers.IO) {
        val file = cacheFile(key)
        if (!file.exists()) return@withContext null

        return@withContext try {
            val payload = JSONObject(file.readText())
            val createdAtMs = payload.optLong(KEY_CREATED_AT_MS, 0L)
            if (createdAtMs <= 0L || System.currentTimeMillis() - createdAtMs > ttlMs) {
                file.delete()
                null
            } else {
                parseRecords(payload.optJSONArray(KEY_SPOTS))
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    suspend fun put(key: String, spots: List<ParkingSpotRecord>) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put(KEY_CREATED_AT_MS, System.currentTimeMillis())
            put(KEY_SPOTS, serializeRecords(spots))
        }
        cacheFile(key).writeText(payload.toString())
    }

    private fun serializeRecords(records: List<ParkingSpotRecord>): JSONArray {
        val array = JSONArray()
        for (spot in records) {
            val item = JSONObject()
                .put("id", spot.id)
                .put("title", spot.title)
                .put("lat", spot.lat)
                .put("lng", spot.lng)
                .put("source", spot.source)
                .put("feeFlag", spot.feeFlag.name)
                .put("rulesSummary", spot.rulesSummary)
                .put("confidenceScore", spot.confidenceScore)
                .put("isAccessible", spot.isAccessible)
            array.put(item)
        }
        return array
    }

    private fun parseRecords(array: JSONArray?): List<ParkingSpotRecord> {
        if (array == null) return emptyList()
        val records = mutableListOf<ParkingSpotRecord>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val feeFlagName = item.optString("feeFlag")
            val feeFlag = runCatching { ParkingFeeFlag.valueOf(feeFlagName) }
                .getOrNull() ?: ParkingFeeFlag.UNKNOWN
            val lat = item.optDouble("lat", Double.NaN)
            val lng = item.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue

            records += ParkingSpotRecord(
                id = item.optString("id", ""),
                title = item.optString("title", "Parking"),
                lat = lat,
                lng = lng,
                source = item.optString("source", "OSM"),
                feeFlag = feeFlag,
                rulesSummary = item.optString("rulesSummary", "Rules unclear. Check signage."),
                confidenceScore = item.optInt("confidenceScore", 30),
                isAccessible = item.optBoolean("isAccessible", false)
            )
        }
        return records
    }

    private fun cacheFile(key: String): File {
        val fileName = sha256Hex(key)
        return File(cacheDir, "$fileName.json")
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private companion object {
        const val DEFAULT_TTL_MS = 30L * 60L * 1000L
        const val CACHE_DIR_NAME = "parking_cache"
        const val KEY_CREATED_AT_MS = "createdAtEpochMs"
        const val KEY_SPOTS = "spots"
    }
}
