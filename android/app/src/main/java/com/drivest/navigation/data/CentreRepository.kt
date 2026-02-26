package com.drivest.navigation.data

import android.content.Context
import com.drivest.navigation.backend.BackendCentreRepository
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException

class CentreRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backendCentreRepository: BackendCentreRepository
) {

    suspend fun loadCentres(): List<TestCentre> = withContext(Dispatchers.IO) {
        when (settingsRepository.dataSourceMode.first()) {
            DataSourceMode.ASSETS_ONLY -> loadCentresFromAssets()
            DataSourceMode.BACKEND_ONLY -> loadCentresBackendOnly()
            DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> loadCentresWithFallback()
        }
    }

    fun findByIdLocal(centreId: String): TestCentre? {
        return loadCentresFromAssets().firstOrNull { it.id == centreId }
    }

    private suspend fun loadCentresBackendOnly(): List<TestCentre> {
        return try {
            val centres = backendCentreRepository.loadCentres()
            settingsRepository.clearBackendErrorSummary()
            centres
        } catch (error: Exception) {
            val summary = error.message ?: "Backend centres request failed."
            settingsRepository.recordBackendErrorSummary(summary)
            throw IOException(summary, error)
        }
    }

    private suspend fun loadCentresWithFallback(): List<TestCentre> {
        return try {
            val centres = backendCentreRepository.loadCentres()
            if (centres.isEmpty()) {
                throw IOException("Backend centres response was empty.")
            }
            settingsRepository.clearBackendErrorSummary()
            centres
        } catch (error: Exception) {
            val summary = error.message ?: "Backend centres request failed."
            settingsRepository.recordBackendErrorSummary(summary)
            settingsRepository.recordFallbackUsed()
            loadCentresFromAssets()
        }
    }

    private fun loadCentresFromAssets(): List<TestCentre> {
        return runCatching {
            val jsonText = context.assets.open("centres.json").bufferedReader().use { it.readText() }
            val centresArray = JSONArray(jsonText)
            parseCentresArray(centresArray)
        }.getOrDefault(emptyList())
    }

    private fun parseCentresArray(centresArray: JSONArray): List<TestCentre> {
        return buildList {
            for (i in 0 until centresArray.length()) {
                val item = centresArray.optJSONObject(i) ?: continue
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                add(
                    TestCentre(
                        id = id,
                        name = item.optString("name", ""),
                        address = item.optString("address", ""),
                        lat = lat,
                        lon = lon
                    )
                )
            }
        }
    }
}
