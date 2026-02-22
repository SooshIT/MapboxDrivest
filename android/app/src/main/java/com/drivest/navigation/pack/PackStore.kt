package com.drivest.navigation.pack

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

enum class PackType(val key: String) {
    CENTRES("centres"),
    ROUTES("routes"),
    HAZARDS("hazards")
}

class PackStore(
    context: Context,
    private val ttlMs: Long = DEFAULT_TTL_MS
) {

    private val rootDir = File(context.filesDir, "pack_store").apply {
        if (!exists()) mkdirs()
    }

    fun readPack(packType: PackType, centreId: String): String? {
        return readPackEnvelope(packType, centreId)?.packJson
    }

    fun readPackEtag(packType: PackType, centreId: String): String? {
        return readPackEnvelope(packType, centreId)?.etag
    }

    fun readPackVersion(packType: PackType, centreId: String): String? {
        return readPackEnvelope(packType, centreId)?.version
    }

    private fun readPackEnvelope(packType: PackType, centreId: String): CachedPackEnvelope? {
        val safeCentreId = sanitizeId(centreId)
        val packDir = File(rootDir, packType.key)
        if (!packDir.exists()) return null

        val candidates = packDir.listFiles { file ->
            file.isFile && file.name.startsWith("${safeCentreId}_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        for (file in candidates) {
            val envelope = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (envelope == null) {
                file.delete()
                continue
            }
            val createdAt = envelope.optLong(KEY_CREATED_AT_EPOCH_MS, 0L)
            if (createdAt <= 0L || System.currentTimeMillis() - createdAt > ttlMs) {
                file.delete()
                continue
            }
            val packJson = envelope.optString(KEY_PACK_JSON, "")
            if (packJson.isNotBlank()) {
                return CachedPackEnvelope(
                    packJson = packJson,
                    etag = envelope.optString(KEY_ETAG).ifBlank { null },
                    version = envelope.optJSONObject(KEY_METADATA)
                        ?.optString("version")
                        .orEmpty()
                        .ifBlank { null }
                )
            }
        }
        return null
    }

    fun writePack(
        packType: PackType,
        centreId: String,
        packJson: String,
        metadata: PackMetadata,
        etag: String? = null,
        offlineAvailable: Boolean? = null
    ) {
        val safeCentreId = sanitizeId(centreId)
        val packDir = File(rootDir, packType.key).apply {
            if (!exists()) mkdirs()
        }
        val existingOfflineAvailable = readOfflineAvailable(packType, centreId)
        val file = File(packDir, "${safeCentreId}_${sanitizeId(metadata.version)}.json")
        val envelope = JSONObject().apply {
            put(KEY_CREATED_AT_EPOCH_MS, System.currentTimeMillis())
            put(KEY_METADATA, JSONObject().apply {
                put("version", metadata.version)
                put("generatedAt", metadata.generatedAt)
                put("bbox", JSONObject().apply {
                    put("south", metadata.bbox.south)
                    put("west", metadata.bbox.west)
                    put("north", metadata.bbox.north)
                    put("east", metadata.bbox.east)
                })
            })
            put(KEY_PACK_JSON, packJson)
            if (!etag.isNullOrBlank()) {
                put(KEY_ETAG, etag)
            }
            put(
                KEY_OFFLINE_AVAILABLE,
                offlineAvailable ?: existingOfflineAvailable
            )
        }
        file.writeText(envelope.toString())
    }

    fun markOfflineAvailable(
        packType: PackType,
        centreId: String,
        offlineAvailable: Boolean
    ) {
        val safeCentreId = sanitizeId(centreId)
        val packDir = File(rootDir, packType.key)
        if (!packDir.exists()) return

        val candidate = packDir.listFiles { file ->
            file.isFile && file.name.startsWith("${safeCentreId}_") && file.name.endsWith(".json")
        }?.maxByOrNull { it.lastModified() } ?: return

        val envelope = runCatching { JSONObject(candidate.readText()) }.getOrNull() ?: return
        envelope.put(KEY_OFFLINE_AVAILABLE, offlineAvailable)
        candidate.writeText(envelope.toString())
    }

    fun isOfflineAvailable(packType: PackType, centreId: String): Boolean {
        return readOfflineAvailable(packType, centreId)
    }

    fun isPackOlderThanDays(
        packType: PackType,
        centreId: String,
        days: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val ageMs = readPackAgeMs(packType, centreId, nowEpochMs) ?: return false
        val thresholdMs = days * 24L * 60L * 60L * 1000L
        return ageMs > thresholdMs
    }

    private fun readOfflineAvailable(packType: PackType, centreId: String): Boolean {
        val safeCentreId = sanitizeId(centreId)
        val packDir = File(rootDir, packType.key)
        if (!packDir.exists()) return false

        val candidates = packDir.listFiles { file ->
            file.isFile && file.name.startsWith("${safeCentreId}_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        for (file in candidates) {
            val envelope = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (envelope == null) {
                file.delete()
                continue
            }
            val createdAt = envelope.optLong(KEY_CREATED_AT_EPOCH_MS, 0L)
            if (createdAt <= 0L || System.currentTimeMillis() - createdAt > ttlMs) {
                file.delete()
                continue
            }
            return envelope.optBoolean(KEY_OFFLINE_AVAILABLE, false)
        }
        return false
    }

    private fun readPackAgeMs(
        packType: PackType,
        centreId: String,
        nowEpochMs: Long
    ): Long? {
        val safeCentreId = sanitizeId(centreId)
        val packDir = File(rootDir, packType.key)
        if (!packDir.exists()) return null

        val candidates = packDir.listFiles { file ->
            file.isFile && file.name.startsWith("${safeCentreId}_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        for (file in candidates) {
            val envelope = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (envelope == null) {
                file.delete()
                continue
            }
            val createdAt = envelope.optLong(KEY_CREATED_AT_EPOCH_MS, 0L)
            if (createdAt <= 0L || nowEpochMs - createdAt > ttlMs) {
                file.delete()
                continue
            }
            val metadataGeneratedAt = envelope
                .optJSONObject(KEY_METADATA)
                ?.optString("generatedAt")
                .orEmpty()
            val sourceEpoch = parseIsoTimestampEpochMs(metadataGeneratedAt) ?: createdAt
            return (nowEpochMs - sourceEpoch).coerceAtLeast(0L)
        }
        return null
    }

    private fun parseIsoTimestampEpochMs(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    private fun sanitizeId(raw: String): String {
        return raw.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    }

    private companion object {
        const val DEFAULT_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        const val KEY_CREATED_AT_EPOCH_MS = "createdAtEpochMs"
        const val KEY_METADATA = "metadata"
        const val KEY_PACK_JSON = "packJson"
        const val KEY_ETAG = "etag"
        const val KEY_OFFLINE_AVAILABLE = "offlineAvailable"
    }

    private data class CachedPackEnvelope(
        val packJson: String,
        val etag: String?,
        val version: String?
    )
}
