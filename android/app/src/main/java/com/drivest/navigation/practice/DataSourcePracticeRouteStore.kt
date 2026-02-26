package com.drivest.navigation.practice

import android.content.Context
import com.drivest.navigation.backend.BackendPracticeRouteStore
import com.drivest.navigation.pack.PackJsonParser
import com.drivest.navigation.pack.PackStore
import com.drivest.navigation.pack.PackType
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class DataSourcePracticeRouteStore(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val backendPracticeRouteStore: BackendPracticeRouteStore = BackendPracticeRouteStore(context),
    private val assetsPracticeRouteStore: AssetsPracticeRouteStore = AssetsPracticeRouteStore(context)
) : PracticeRouteStore {

    private val appContext = context.applicationContext
    private val packStore = PackStore(context)

    override suspend fun loadRoutesForCentre(centreId: String): List<PracticeRoute> {
        val offlineCachedRoutes = loadOfflineReadyRoutes(centreId)
        if (offlineCachedRoutes.isNotEmpty()) {
            return offlineCachedRoutes
        }

        return when (settingsRepository.dataSourceMode.first()) {
            DataSourceMode.ASSETS_ONLY -> loadAssetRoutesWithAliases(centreId)
            DataSourceMode.BACKEND_ONLY -> loadBackendOnly(centreId)
            DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> loadBackendThenCacheThenAssets(centreId)
        }
    }

    private suspend fun loadBackendOnly(centreId: String): List<PracticeRoute> {
        return try {
            val backendRoutes = loadBackendRoutesWithAliases(centreId)
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
            val backendRoutes = loadBackendRoutesWithAliases(centreId)
            settingsRepository.clearBackendErrorSummary()
            if (backendRoutes.isNotEmpty()) {
                return backendRoutes
            }
        } catch (error: Exception) {
            val summary = error.message ?: "Backend routes request failed for $centreId."
            settingsRepository.recordBackendErrorSummary(summary)
        }

        val cachedRoutes = loadRoutesFromCacheWithAliases(centreId)
        if (cachedRoutes.isNotEmpty()) {
            settingsRepository.recordFallbackUsed()
            return cachedRoutes
        }

        val assetRoutes = loadAssetRoutesWithAliases(centreId)
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

    private fun loadRoutesFromCacheWithAliases(centreId: String): List<PracticeRoute> {
        val direct = loadRoutesFromCache(centreId)
        if (direct.isNotEmpty()) return direct
        val aliases = resolveBackendCentreIdAliases(centreId)
        for (alias in aliases) {
            if (alias == centreId) continue
            val aliasRoutes = loadRoutesFromCache(alias)
            if (aliasRoutes.isNotEmpty()) return aliasRoutes
        }
        return emptyList()
    }

    private fun loadOfflineReadyRoutes(centreId: String): List<PracticeRoute> {
        if (packStore.isOfflineAvailable(PackType.ROUTES, centreId)) {
            return loadRoutesFromCache(centreId)
        }
        val aliases = resolveBackendCentreIdAliases(centreId)
        for (alias in aliases) {
            if (alias == centreId) continue
            if (!packStore.isOfflineAvailable(PackType.ROUTES, alias)) continue
            val aliasRoutes = loadRoutesFromCache(alias)
            if (aliasRoutes.isNotEmpty()) return aliasRoutes
        }
        return emptyList()
    }

    private suspend fun loadAssetRoutesWithAliases(centreId: String): List<PracticeRoute> {
        val directRoutes = assetsPracticeRouteStore.loadRoutesForCentre(centreId)
        if (directRoutes.isNotEmpty()) return directRoutes

        val aliases = resolveAssetCentreAliases(centreId)
        for (alias in aliases) {
            if (alias == centreId) continue
            val aliasRoutes = assetsPracticeRouteStore.loadRoutesForCentre(alias)
            if (aliasRoutes.isNotEmpty()) {
                return aliasRoutes
            }
        }
        return emptyList()
    }

    private suspend fun loadBackendRoutesWithAliases(centreId: String): List<PracticeRoute> {
        val attempted = linkedSetOf<String>()
        attempted += centreId
        attempted += resolveBackendCentreIdAliases(centreId)

        var lastError: Exception? = null
        var hadSuccessfulResponse = false

        for (candidateId in attempted) {
            if (candidateId.isBlank()) continue
            try {
                val routes = backendPracticeRouteStore.loadRoutesForCentre(candidateId)
                hadSuccessfulResponse = true
                if (routes.isNotEmpty()) {
                    return routes
                }
            } catch (error: Exception) {
                lastError = error
            }
        }

        if (!hadSuccessfulResponse && lastError != null) {
            throw lastError
        }
        return emptyList()
    }

    private fun resolveBackendCentreIdAliases(centreId: String): List<String> {
        if (centreId.isBlank()) return emptyList()
        val root = readCentresCacheRoot() ?: return emptyList()
        val centresArray = root.optJSONArray("centres")
            ?: root.optJSONObject("data")?.optJSONArray("items")
            ?: JSONArray()

        val targetCandidates = linkedSetOf<String>().also { addSlugCandidatesFromRaw(centreId, it) }
        if (targetCandidates.isEmpty()) return emptyList()

        val matches = linkedSetOf<String>()
        for (index in 0 until centresArray.length()) {
            val centreJson = centresArray.optJSONObject(index) ?: continue
            val id = centreJson.optString("id", "").trim()
            if (id.isBlank() || id == centreId) continue

            val centreCandidates = linkedSetOf<String>()
            addSlugCandidatesFromRaw(id, centreCandidates)
            addSlugCandidatesFromRaw(centreJson.optString("name", ""), centreCandidates)
            addSlugCandidatesFromRaw(centreJson.optString("city", ""), centreCandidates)
            addSlugCandidatesFromRaw(centreJson.optString("address", ""), centreCandidates)
            if (centreCandidates.isEmpty()) continue

            val isMatch = targetCandidates.any { target ->
                centreCandidates.any { candidate ->
                    target == candidate || target.contains(candidate) || candidate.contains(target)
                }
            }
            if (isMatch) {
                matches += id
            }
        }
        return matches.toList()
    }

    private fun resolveAssetCentreAliases(centreId: String): List<String> {
        val availableAssetCentreIds = appContext.assets.list(ASSET_ROUTES_ROOT)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        if (availableAssetCentreIds.isEmpty()) return emptyList()

        val candidates = linkedSetOf<String>()
        addSlugCandidatesFromRaw(centreId, candidates)
        readCachedCentreTextCandidates(centreId).forEach { raw ->
            addSlugCandidatesFromRaw(raw, candidates)
        }

        val exactMatches = candidates.filter { it in availableAssetCentreIds }
        if (exactMatches.isNotEmpty()) return exactMatches

        // Fallback fuzzy match for IDs like "colchester-test-centre" when assets use "colchester".
        val fuzzyMatches = linkedSetOf<String>()
        candidates.forEach { candidate ->
            availableAssetCentreIds.forEach { assetId ->
                if (candidate.contains(assetId) || assetId.contains(candidate)) {
                    fuzzyMatches += assetId
                }
            }
        }
        return fuzzyMatches.toList()
    }

    private fun readCachedCentreTextCandidates(centreId: String): List<String> {
        val root = readCentresCacheRoot() ?: return emptyList()
        val centresArray = root.optJSONArray("centres")
            ?: root.optJSONObject("data")?.optJSONArray("items")
            ?: JSONArray()
        for (index in 0 until centresArray.length()) {
            val centreJson = centresArray.optJSONObject(index) ?: continue
            val id = centreJson.optString("id", "").trim()
            if (id != centreId) continue
            return listOf(
                centreJson.optString("name", ""),
                centreJson.optString("city", ""),
                centreJson.optString("address", "")
            ).map { it.trim() }.filter { it.isNotBlank() }
        }
        return emptyList()
    }

    private fun readCentresCacheRoot(): JSONObject? {
        val rawJson = packStore.readPack(PackType.CENTRES, CENTRES_CACHE_KEY) ?: return null
        return runCatching { JSONObject(rawJson) }.getOrNull()
    }

    private fun addSlugCandidatesFromRaw(raw: String, sink: MutableSet<String>) {
        val tokens = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        fun addFromTokens(parts: List<String>) {
            if (parts.isEmpty()) return
            sink += parts.joinToString("-")
        }

        addFromTokens(tokens)

        val suffixWords = setOf("driving", "test", "centre", "center")
        var trimmedEnd = tokens.size
        while (trimmedEnd > 0 && tokens[trimmedEnd - 1] in suffixWords) {
            trimmedEnd -= 1
        }
        addFromTokens(tokens.take(trimmedEnd))

        val testIndex = tokens.indexOf("test")
        if (testIndex > 0) {
            addFromTokens(tokens.take(testIndex))
        }
    }

    private companion object {
        const val ASSET_ROUTES_ROOT = "routes"
        const val CENTRES_CACHE_KEY = "all"
    }
}
