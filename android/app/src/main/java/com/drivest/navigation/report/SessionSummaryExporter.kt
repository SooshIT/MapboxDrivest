package com.drivest.navigation.report

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SessionSummaryExporter(
    context: Context
) {

    private val internalExportDir = File(context.filesDir, "session_exports").apply {
        if (!exists()) mkdirs()
    }
    private val externalExportDir = context.getExternalFilesDir(null)?.let { root ->
        File(root, "session_exports")
    }?.apply {
        if (!exists()) mkdirs()
    }
    private val exportDir: File = externalExportDir ?: internalExportDir

    fun export(summary: SessionSummaryPayload): File {
        val timestamp = timestampFormatter.format(Date())
        val fileName = "drive_log_${timestamp}.json"
        val file = File(exportDir, fileName)
        val payload = JSONObject().apply {
            put("centreId", summary.centreId)
            put("routeId", summary.routeId)
            put("stressIndex", summary.stressIndex)
            put("offRouteCount", summary.offRouteCount)
            put("hazardCounts", JSONObject().apply {
                put("roundabout", summary.roundaboutCount)
                put("trafficSignal", summary.trafficSignalCount)
                put("zebraCrossing", summary.zebraCount)
                put("schoolZone", summary.schoolCount)
                put("busLane", summary.busLaneCount)
            })
            put("completionFlag", summary.completionFlag)
            put("complexityScore", summary.complexityScore)
            put("confidenceScore", summary.confidenceScore)
            put("durationSeconds", summary.durationSeconds)
            put("distanceMetersDriven", summary.distanceMetersDriven)
            put("exportedAt", isoFormatter.format(Date()))
        }
        file.writeText(payload.toString(2))
        return file
    }

    fun exportDeveloperSnapshot(snapshot: JSONObject): File {
        val timestamp = timestampFormatter.format(Date())
        val fileName = "drive_log_debug_${timestamp}.json"
        val file = File(exportDir, fileName)
        val payload = JSONObject().apply {
            put("type", "developer_snapshot")
            put("exportedAt", isoFormatter.format(Date()))
            put("snapshot", snapshot)
        }
        file.writeText(payload.toString(2))
        return file
    }

    fun latestExport(): File? {
        if (!exportDir.exists()) return null
        return exportDir.listFiles()
            ?.filter { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.maxByOrNull { file -> file.lastModified() }
    }

    fun exportDirectoryPath(): String = exportDir.absolutePath

    private companion object {
        val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
