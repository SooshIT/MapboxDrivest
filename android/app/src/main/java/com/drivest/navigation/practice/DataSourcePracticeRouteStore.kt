package com.drivest.navigation.practice

import android.content.Context
import com.drivest.navigation.backend.BackendPracticeRouteStore
import com.drivest.navigation.pack.PackJsonParser
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.IOException

class DataSourcePracticeRouteStore(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val backendPracticeRouteStore: BackendPracticeRouteStore = BackendPracticeRouteStore(context),
    private val assetsPracticeRouteStore: AssetsPracticeRouteStore = AssetsPracticeRouteStore(context)
) : PracticeRouteStore {

    private val packStore = PackStore(context)

    override suspend fun loadRoutesForCentre(centreId: String): List<PracticeRoute> {
        val offlineCachedRoutes = loadOfflineReadyRoutes(centreId)
        if (offlineCachedRoutes.isNotEmpty()) {
            return offlineCachedRoutes
        }

        return when (settingsRepository.dataSourceMode.first()) {
            DataSourceMode.ASSETS_ONLY -> assetsPracticeRouteStore.loadRoutesForCentre(centreId)
            DataSourceMode.BACKEND_ONLY -> loadBackendOnly(centreId)
            DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> loadBackendThenCacheThenAssets(centreId)
        }
    }

    private suspend fun loadBackendOnly(centreId: String): List<PracticeRoute> {
        return try {
            val backendRoutes = backendPracticeRouteStore.loadRoutesForCentre(centreId)
            settingsRepository.clearBackendErrorSummary()
            backendRoutes
        } catch (error: Exception) {
            val summary = error.message ?: "Backend routes request failed for $centreId."
            settingsRepository.recordBackendErrorSummary(summary)
            throw IOException(summary, error)
        }
    }

    private suspend fun loadBackendThenCacheThenAssets(centreId: String): List<PracticeRoute> {
        try {
            val backendRoutes = backendPracticeRouteStore.loadRoutesForCentre(centreId)
            settingsRepository.clearBackendErrorSummary()
            if (backendRoutes.isNotEmpty()) {
                return backendRoutes
            }
        } catch (error: Exception) {
            val summary = error.message ?: "Backend routes request failed for $centreId."
            settingsRepository.recordBackendErrorSummary(summary)
        }

        val cachedRoutes = loadRoutesFromCache(centreId)
        if (cachedRoutes.isNotEmpty()) {
            settingsRepository.recordFallbackUsed()
            return cachedRoutes
        }

        val assetRoutes = assetsPracticeRouteStore.loadRoutesForCentre(centreId)
        if (assetRoutes.isNotEmpty()) {
            settingsRepository.recordFallbackUsed()
        }
        return assetRoutes
    }

    private fun loadRoutesFromCache(centreId: String): List<PracticeRoute> {
        val packJson = packStore.readPack(PackType.ROUTES, centreId) ?: return emptyList()
        val routesPack = PackJsonParser.parseRoutesPack(packJson) ?: return emptyList()
        if (routesPack.centreId != centreId) return emptyList()
        return routesPack.routes
    }

    private fun loadOfflineReadyRoutes(centreId: String): List<PracticeRoute> {
        if (!packStore.isOfflineAvailable(PackType.ROUTES, centreId)) return emptyList()
        return loadRoutesFromCache(centreId)
    }
}
