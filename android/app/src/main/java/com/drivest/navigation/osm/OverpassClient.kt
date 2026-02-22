package com.drivest.navigation.osm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class OverpassClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(query: String): String = withContext(Dispatchers.IO) {
        val requestBody = FormBody.Builder()
            .add("data", query)
            .build()

        val request = Request.Builder()
            .url(OVERPASS_ENDPOINT)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val preview = responseBody.take(300)
                throw IOException("Overpass request failed: code=${response.code}, body=$preview")
            }
            responseBody
        }
    }

    private companion object {
        const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
    }
}
