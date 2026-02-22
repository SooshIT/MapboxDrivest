package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivityDebugSessionBinding
import com.drivest.navigation.debug.PracticeOffRouteDebugStore
import com.drivest.navigation.settings.DataSourceMode
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugSessionBinding
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeDiagnostics()

        binding.debugStartNavButton.setOnClickListener {
            launchMainForDebug(mode = AppFlow.MODE_NAV)
        }
        binding.debugStopNavButton.setOnClickListener {
            sendDebugCommand(AppFlow.DEBUG_STOP_NAV)
        }
        binding.debugStartPracticeButton.setOnClickListener {
            launchMainForDebug(mode = AppFlow.MODE_PRACTICE)
        }
        binding.debugStopPracticeButton.setOnClickListener {
            sendDebugCommand(AppFlow.DEBUG_STOP_PRACTICE)
        }
        binding.debugRunCyclesButton.setOnClickListener {
            runTwentyCycles()
        }
    }

    private fun launchMainForDebug(mode: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(AppFlow.EXTRA_APP_MODE, mode)
            putExtra(AppFlow.EXTRA_CENTRE_ID, "colchester")
            putExtra(AppFlow.EXTRA_DEBUG_AUTOSTART, true)
            if (mode == AppFlow.MODE_NAV) {
                putExtra(AppFlow.EXTRA_DESTINATION_LAT, 51.8878)
                putExtra(AppFlow.EXTRA_DESTINATION_LON, 0.9090)
                putExtra(AppFlow.EXTRA_DESTINATION_NAME, "Debug Destination")
            } else {
                putExtra(AppFlow.EXTRA_ROUTE_ID, "colchester-hythe-town-loop")
            }
        }
        startActivity(intent)
    }

    private fun sendDebugCommand(command: String) {
        sendBroadcast(Intent(AppFlow.ACTION_DEBUG_COMMAND).putExtra(AppFlow.EXTRA_DEBUG_COMMAND, command))
    }

    private fun runTwentyCycles() {
        Toast.makeText(this, "Running 20 lifecycle cycles. Watch logcat for ObserverRegistry.", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            launchMainForDebug(mode = AppFlow.MODE_NAV)
            delay(2000)
            repeat(20) {
                sendDebugCommand(AppFlow.DEBUG_START_NAV)
                delay(1800)
                sendDebugCommand(AppFlow.DEBUG_START_NAV)
                delay(1800)
                sendDebugCommand(AppFlow.DEBUG_STOP_NAV)
                delay(1200)
            }
            Toast.makeText(this@DebugSessionActivity, "20 cycles dispatched.", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeDiagnostics() {
        lifecycleScope.launch {
            combine(
                settingsRepository.dataSourceMode,
                settingsRepository.lastFallbackUsedEpochMs,
                settingsRepository.lastBackendErrorSummary,
                PracticeOffRouteDebugStore.state
            ) { mode, lastFallbackEpochMs, backendErrorSummary, offRouteDebugState ->
                DiagnosticSnapshot(
                    mode = mode,
                    lastFallbackEpochMs = lastFallbackEpochMs,
                    backendErrorSummary = backendErrorSummary,
                    rawDistanceM = offRouteDebugState.rawDistanceM,
                    smoothedDistanceM = offRouteDebugState.smoothedDistanceM,
                    offRouteState = offRouteDebugState.offRouteState
                )
            }.collect { snapshot ->
                val mode = snapshot.mode
                val lastFallbackEpochMs = snapshot.lastFallbackEpochMs
                val backendErrorSummary = snapshot.backendErrorSummary
                binding.debugDataSourceModeValue.text = mode.toDisplayLabel()
                binding.debugLastFallbackValue.text =
                    if (lastFallbackEpochMs > 0L) {
                        timestampFormat.format(Date(lastFallbackEpochMs))
                    } else {
                        "Never"
                    }
                binding.debugBackendErrorValue.text =
                    backendErrorSummary.ifBlank { "None" }
                binding.debugRawDistanceValue.text = formatMeters(snapshot.rawDistanceM)
                binding.debugSmoothedDistanceValue.text = formatMeters(snapshot.smoothedDistanceM)
                binding.debugOffRouteStateValue.text = snapshot.offRouteState
            }
        }
    }

    private fun formatMeters(value: Double?): String {
        return if (value == null || !value.isFinite()) {
            "-"
        } else {
            String.format(Locale.UK, "%.1f m", value)
        }
    }

    private fun DataSourceMode.toDisplayLabel(): String {
        return when (this) {
            DataSourceMode.ASSETS_ONLY -> "ASSETS_ONLY"
            DataSourceMode.BACKEND_ONLY -> "BACKEND_ONLY"
            DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS -> "BACKEND_THEN_CACHE_THEN_ASSETS"
        }
    }

    private data class DiagnosticSnapshot(
        val mode: DataSourceMode,
        val lastFallbackEpochMs: Long,
        val backendErrorSummary: String,
        val rawDistanceM: Double?,
        val smoothedDistanceM: Double?,
        val offRouteState: String
    )
}
