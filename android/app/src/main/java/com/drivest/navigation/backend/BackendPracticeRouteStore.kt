package com.drivest.navigation.backend

import android.content.Context
import android.util.Log
import com.drivest.navigation.BuildConfig
import com.drivest.navigation.pack.PackJsonParser
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.practice.PracticeRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class BackendPracticeRouteStore(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private val packStore = PackStore(context)

    suspend fun loadRoutesForCentre(centreId: String): List<PracticeRoute> = withContext(Dispatchers.IO) {
        if (BackendRateLimitBackoff.isActive()) {
            throw IOException(
                "Routes fetch paused after backend throttling. retryInMs=${BackendRateLimitBackoff.remainingMs()}"
            )
        }
        val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/centres/$centreId/routes"
        val cachedEtag = packStore.readPackEtag(PackType.ROUTES, centreId)
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
                val cachedJson = packStore.readPack(PackType.ROUTES, centreId)
                    ?: throw IOException("Routes 304 received but cache is unavailable for $centreId.")
                val cachedPack = PackJsonParser.parseRoutesPack(cachedJson)
                    ?: throw IOException("Routes 304 received but cached payload is invalid for $centreId.")
                Log.d(TAG, "Routes not modified, using cached payload for $centreId: ${cachedPack.routes.size}")
                return@withContext cachedPack.routes
            }
            if (response.code == 429) {
                BackendRateLimitBackoff.record429()
                throw IOException("Routes fetch throttled (429). Backing off backend calls for 10 minutes.")
            }
            if (!response.isSuccessful) {
                throw IOException("Routes fetch failed for $centreId with code=${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val pack = PackJsonParser.parseRoutesPack(body)
                ?: throw IOException("Routes response parsing failed for $centreId.")
            packStore.writePack(
                packType = PackType.ROUTES,
                centreId = centreId,
                packJson = body,
                metadata = pack.metadata,
                etag = response.header("ETag")
            )
            Log.d(TAG, "Fetched backend routes for $centreId: ${pack.routes.size}")
            pack.routes
        }
    }

    private companion object {
        const val TAG = "BackendPracticeStore"
    }
}
