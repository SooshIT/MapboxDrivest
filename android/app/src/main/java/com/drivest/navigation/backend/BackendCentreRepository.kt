package com.drivest.navigation.backend

import android.content.Context
import android.util.Log
import com.drivest.navigation.BuildConfig
import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.pack.PackBbox
import com.drivest.navigation.pack.PackMetadata
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class BackendCentreRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val packStore = PackStore(context)

    suspend fun loadCentres(): List<TestCentre> = withContext(Dispatchers.IO) {
        if (BackendRateLimitBackoff.isActive()) {
            throw IOException(
                "Centres fetch paused after backend throttling. retryInMs=${BackendRateLimitBackoff.remainingMs()}"
            )
        }
        val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/centres"
        val cachedEtag = packStore.readPackEtag(PackType.CENTRES, CENTRES_KEY)
        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cachedEtag.isNullOrBlank()) {
                    header("If-None-Match", cachedEtag)
                }
            }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 304) {
                val cachedJson = packStore.readPack(PackType.CENTRES, CENTRES_KEY)
                    ?: throw IOException("Centres 304 received but cache is unavailable.")
                val cachedPack = parseCentresPack(cachedJson)
                    ?: throw IOException("Centres 304 received but cached payload is invalid.")
                Log.d(TAG, "Centres not modified, using cached payload: ${cachedPack.centres.size}")
                return@withContext cachedPack.centres
            }
            if (response.code == 429) {
                BackendRateLimitBackoff.record429()
                throw IOException("Centres fetch throttled (429). Backing off backend calls for 10 minutes.")
            }
            if (!response.isSuccessful) {
                throw IOException("Centres fetch failed with code=${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val centresPack = parseCentresPack(body)
                ?: throw IOException("Centres response parsing failed.")
            packStore.writePack(
                packType = PackType.CENTRES,
                centreId = CENTRES_KEY,
                packJson = body,
                metadata = centresPack.metadata,
                etag = response.header("ETag")
            )
            Log.d(TAG, "Fetched centres from backend: ${centresPack.centres.size}")
            centresPack.centres
        }
    }

    private fun parseCentresPack(json: String): CentresPackParsed? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val metadataJson = root.optJSONObject("metadata") ?: return null
        val bboxJson = metadataJson.optJSONObject("bbox") ?: return null
        val metadata = PackMetadata(
            version = metadataJson.optString("version", "centres-v1"),
            generatedAt = metadataJson.optString("generatedAt", ""),
            bbox = PackBbox(
                south = bboxJson.optDouble("south", 0.0),
                west = bboxJson.optDouble("west", 0.0),
                north = bboxJson.optDouble("north", 0.0),
                east = bboxJson.optDouble("east", 0.0)
            )
        )
        val centresArray = root.optJSONArray("centres") ?: JSONArray()
        val centres = mutableListOf<TestCentre>()
        for (i in 0 until centresArray.length()) {
            val item = centresArray.optJSONObject(i) ?: continue
            centres += TestCentre(
                id = item.optString("id", ""),
                name = item.optString("name", ""),
                address = item.optString("address", ""),
                lat = item.optDouble("lat", Double.NaN),
                lon = item.optDouble("lon", Double.NaN)
            )
        }
        val filtered = centres.filter { it.id.isNotBlank() && !it.lat.isNaN() && !it.lon.isNaN() }
        return CentresPackParsed(metadata = metadata, centres = filtered)
    }

    private data class CentresPackParsed(
        val metadata: PackMetadata,
        val centres: List<TestCentre>
    )

    private companion object {
        const val TAG = "BackendCentreRepo"
        const val CENTRES_KEY = "all"
    }
}
