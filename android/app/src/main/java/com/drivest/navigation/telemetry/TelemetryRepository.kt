package com.drivest.navigation.telemetry

import android.util.Log
import com.drivest.navigation.BuildConfig
import com.drivest.navigation.intelligence.RouteDifficultyLabel
import com.drivest.navigation.intelligence.RouteIntelligenceSummary
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.report.SessionSummaryPayload
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SessionSummaryTelemetry(
    val mode: String,
    val centreId: String?,
    val routeId: String?,
    val completed: Boolean,
    val durationSeconds: Int,
    val distanceMetersDriven: Int,
    val offRouteCount: Int,
    val averageStressIndex: Int,
    val averageConfidenceScore: Int,
    val intelligenceSummary: RouteIntelligenceSummary?,
    val organisationCode: String? = null,
    val centresPackVersion: String? = null,
    val routesPackVersion: String? = null,
    val hazardsPackVersion: String? = null
)

sealed class TelemetryEvent {
    data class App(
        val eventType: String,
        val centreId: String? = null,
        val routeId: String? = null,
        val organisationCode: String? = null,
        val promptType: String? = null,
        val suppressed: Boolean? = null,
        val stressIndex: Int? = null,
        val complexityScore: Int? = null,
        val confidenceScore: Int? = null,
        val offRouteCount: Int? = null,
        val completionFlag: Boolean? = null,
        val payload: Map<String, Any?> = emptyMap()
    ) : TelemetryEvent()

    data class InstructorSession(
        val summary: SessionSummaryPayload,
        val organisationCode: String? = null
    ) : TelemetryEvent()
}

interface TelemetryBackendClient {
    suspend fun post(path: String, payload: JSONObject): Boolean
}

class OkHttpTelemetryBackendClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrlProvider: () -> String = { BuildConfig.API_BASE_URL }
) : TelemetryBackendClient {

    override suspend fun post(path: String, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext false

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val request = Request.Builder()
            .url("$baseUrl$normalizedPath")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Telemetry request failed path=$normalizedPath code=${response.code}")
                    false
                } else {
                    true
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Telemetry request failed path=$normalizedPath error=${error.message}")
            false
        }
    }

    private companion object {
        const val TAG = "TelemetryBackend"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class TelemetryRepository(
    private val settingsRepository: SettingsRepository,
    private val consentRepository: ConsentRepository,
    private val backendClient: TelemetryBackendClient = OkHttpTelemetryBackendClient(),
    private val appVersionProvider: () -> String = { BuildConfig.VERSION_NAME }
) {
    private val telemetryPolicy = TelemetryPolicy(
        consentAnalyticsEnabled = consentRepository.analyticsConsentEnabled,
        settingsAnalyticsEnabled = settingsRepository.analyticsEnabled
    )
    val telemetryLevel: Flow<TelemetryLevel> = telemetryPolicy.level

    suspend fun sendSessionSummary(
        summary: SessionSummaryTelemetry
    ) {
        val level = telemetryLevel.first()
        val payload = buildSessionSummaryEvent(summary = summary, level = level)
        backendClient.post("/telemetry", payload)
    }

    suspend fun sendEvent(event: TelemetryEvent) {
        val level = telemetryLevel.first()
        if (level == TelemetryLevel.MINIMAL) {
            return
        }
        when (event) {
            is TelemetryEvent.App -> {
                val payload = buildAppEventPayload(event)
                backendClient.post("/telemetry", payload)
            }
            is TelemetryEvent.InstructorSession -> {
                val payload = buildInstructorSessionPayload(event.summary, event.organisationCode)
                backendClient.post("/instructor/session", payload)
            }
        }
    }

    suspend fun sendInstructorSession(
        summary: SessionSummaryPayload,
        organisationCode: String? = null
    ) {
        sendEvent(TelemetryEvent.InstructorSession(summary = summary, organisationCode = organisationCode))
    }

    private fun buildSessionSummaryEvent(
        summary: SessionSummaryTelemetry,
        level: TelemetryLevel
    ): JSONObject {
        val payloadJson = when (level) {
            TelemetryLevel.FULL -> buildFullSessionSummaryPayload(summary)
            TelemetryLevel.MINIMAL -> buildMinimalSessionSummaryPayload(summary)
        }
        return JSONObject().apply {
            put("event_type", "session_summary")
            put("ts", nowIsoTimestamp())
            put("centre_id", summary.centreId)
            put("route_id", summary.routeId)
            put("completion_flag", summary.completed)
            put("off_route_count", summary.offRouteCount)
            put("stress_index", summary.averageStressIndex)
            put("confidence_score", summary.averageConfidenceScore)
            if (level == TelemetryLevel.FULL && !summary.organisationCode.isNullOrBlank()) {
                put("organisation_id", summary.organisationCode)
            }
            put("payload_json", payloadJson)
        }
    }

    private fun buildFullSessionSummaryPayload(summary: SessionSummaryTelemetry): JSONObject {
        val intelligence = summary.intelligenceSummary ?: defaultIntelligenceSummary()
        val packVersions = buildPackVersionsPayload(summary)
        return JSONObject().apply {
            put("mode", summary.mode)
            put("completed", summary.completed)
            put("duration_s", summary.durationSeconds)
            put("distance_m", summary.distanceMetersDriven)
            put("roundabout_count", intelligence.roundaboutCount)
            put("traffic_signal_count", intelligence.trafficSignalCount)
            put("zebra_count", intelligence.zebraCount)
            put("school_count", intelligence.schoolCount)
            put("bus_lane_count", intelligence.busLaneCount)
            put("complexity_score", intelligence.complexityScore)
            put("stress_index", intelligence.stressIndex)
            put("difficulty", intelligence.difficultyLabel.name.lowercase())
            put("off_route_count", summary.offRouteCount)
            put("avg_stress_index", summary.averageStressIndex)
            put("avg_confidence_score", summary.averageConfidenceScore)
            put("app_version", appVersionProvider())
            if (packVersions != null) {
                put("pack_versions", packVersions)
            }
        }
    }

    private fun buildMinimalSessionSummaryPayload(summary: SessionSummaryTelemetry): JSONObject {
        val packVersions = buildPackVersionsPayload(summary)
        return JSONObject().apply {
            put("duration_seconds", summary.durationSeconds)
            put("distance_meters", summary.distanceMetersDriven)
            put("completion_flag", summary.completed)
            put("off_route_count", summary.offRouteCount)
            put("avg_stress_index", summary.averageStressIndex)
            put("avg_confidence_score", summary.averageConfidenceScore)
            put("app_version", appVersionProvider())
            if (packVersions != null) {
                put("pack_versions", packVersions)
            }
        }
    }

    private fun buildPackVersionsPayload(summary: SessionSummaryTelemetry): JSONObject? {
        val versions = JSONObject().apply {
            if (!summary.centresPackVersion.isNullOrBlank()) {
                put("centres", summary.centresPackVersion)
            }
            if (!summary.routesPackVersion.isNullOrBlank()) {
                put("routes", summary.routesPackVersion)
            }
            if (!summary.hazardsPackVersion.isNullOrBlank()) {
                put("hazards", summary.hazardsPackVersion)
            }
        }
        return versions.takeIf { it.length() > 0 }
    }

    private fun buildAppEventPayload(event: TelemetryEvent.App): JSONObject {
        return JSONObject().apply {
            put("event_type", event.eventType)
            put("ts", nowIsoTimestamp())
            put("centre_id", event.centreId)
            put("route_id", event.routeId)
            if (!event.organisationCode.isNullOrBlank()) {
                put("organisation_id", event.organisationCode)
            }
            if (!event.promptType.isNullOrBlank()) {
                put("prompt_type", event.promptType)
            }
            event.suppressed?.let { put("suppressed_flag", it) }
            event.stressIndex?.let { put("stress_index", it) }
            event.complexityScore?.let { put("complexity_score", it) }
            event.confidenceScore?.let { put("confidence_score", it) }
            event.offRouteCount?.let { put("off_route_count", it) }
            event.completionFlag?.let { put("completion_flag", it) }
            put("payload_json", mapToJsonObject(event.payload))
        }
    }

    private fun buildInstructorSessionPayload(
        summary: SessionSummaryPayload,
        organisationCode: String?
    ): JSONObject {
        return JSONObject().apply {
            if (!organisationCode.isNullOrBlank()) {
                put("organisationId", organisationCode)
            }
            put("centreId", summary.centreId)
            put("routeId", summary.routeId)
            put("stressIndex", summary.stressIndex)
            put("offRouteCount", summary.offRouteCount)
            put(
                "hazardCounts",
                JSONObject().apply {
                    put("roundabout", summary.roundaboutCount)
                    put("trafficSignal", summary.trafficSignalCount)
                    put("zebraCrossing", summary.zebraCount)
                    put("schoolZone", summary.schoolCount)
                    put("busLane", summary.busLaneCount)
                }
            )
            put("payload", JSONObject().apply {
                put("complexityScore", summary.complexityScore)
                put("confidenceScore", summary.confidenceScore)
                put("completionFlag", summary.completionFlag)
                put("durationSeconds", summary.durationSeconds)
                put("distanceMetersDriven", summary.distanceMetersDriven)
            })
        }
    }

    private fun nowIsoTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date())
    }

    private fun defaultIntelligenceSummary(): RouteIntelligenceSummary {
        return RouteIntelligenceSummary(
            roundaboutCount = 0,
            trafficSignalCount = 0,
            zebraCount = 0,
            schoolCount = 0,
            busLaneCount = 0,
            complexityScore = 0,
            stressIndex = 0,
            difficultyLabel = RouteDifficultyLabel.EASY
        )
    }

    private fun mapToJsonObject(values: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        values.forEach { (key, value) ->
            json.put(key, toJsonValue(value))
        }
        return json
    }

    private fun toJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject -> value
            is JSONArray -> value
            is Map<*, *> -> {
                val nested = JSONObject()
                value.forEach { (key, nestedValue) ->
                    val stringKey = key as? String ?: return@forEach
                    nested.put(stringKey, toJsonValue(nestedValue))
                }
                nested
            }
            is Iterable<*> -> {
                JSONArray().apply {
                    value.forEach { put(toJsonValue(it)) }
                }
            }
            else -> value
        }
    }

    private companion object {
        const val TAG = "TelemetryRepository"
    }
}
