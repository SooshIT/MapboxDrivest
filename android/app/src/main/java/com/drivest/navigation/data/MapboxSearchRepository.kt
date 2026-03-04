package com.drivest.navigation.data

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MapboxSearchRepository(
    private val context: Context
) {

    suspend fun searchDestinations(query: String): List<DestinationSuggestion> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < MIN_QUERY_LENGTH) {
            return@withContext emptyList()
        }

        val token = context.getString(com.drivest.navigation.R.string.mapbox_access_token).trim()
        if (token.isBlank() || token == "YOUR_PUBLIC_MAPBOX_ACCESS_TOKEN") {
            return@withContext emptyList()
        }

        runCatching { performSearch(trimmedQuery, token) }
            .getOrDefault(emptyList())
    }

    @WorkerThread
    private fun performSearch(query: String, token: String): List<DestinationSuggestion> {
        val endpoint = buildString {
            append("https://api.mapbox.com/geocoding/v5/mapbox.places/")
            append(Uri.encode(query))
            append(
                ".json?autocomplete=true&fuzzyMatch=true&limit=10" +
                    "&types=address,poi,place,locality,neighborhood,postcode" +
                    "&country=gb&language=en&access_token="
            )
            append(Uri.encode(token))
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

        return try {
            if (connection.responseCode !in 200..299) {
                emptyList()
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseResponse(body)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): List<DestinationSuggestion> {
        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: return emptyList()
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val center = feature.optJSONArray("center") ?: continue
                if (center.length() < 2) continue
                val lon = center.optDouble(0)
                val lat = center.optDouble(1)
                val placeTypes = feature.optJSONArray("place_type")
                val isAddress = placeTypes?.let { types ->
                    (0 until types.length()).any { index ->
                        types.optString(index) == "address"
                    }
                } ?: false
                val addressNumber = feature.optString("address", "")
                val text = feature.optString("text", "")
                val combinedName = when {
                    isAddress && addressNumber.isNotBlank() && text.isNotBlank() ->
                        "$addressNumber $text"
                    text.isNotBlank() -> text
                    else -> feature.optString("place_name", "Destination")
                }
                add(
                    DestinationSuggestion(
                        name = combinedName,
                        placeName = feature.optString("place_name", combinedName.ifBlank { "Destination" }),
                        lat = lat,
                        lon = lon
                    )
                )
            }
        }
    }

    private companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
