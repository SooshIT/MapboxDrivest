package com.drivest.navigation.profile

import com.drivest.navigation.settings.PromptSensitivity
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class ModeSuggestionApplier(
    private val driverProfileRepository: DriverProfileRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend fun applySuggestionsIfNeeded(mode: DriverMode) {
        val isModeSeen = driverProfileRepository.isModeSeen(mode).first()
        if (isModeSeen) {
            return
        }

        val userSetPromptSensitivity = settingsRepository.userSetPromptSensitivity.first()
        val userSetLowStressRouting = settingsRepository.userSetLowStressRouting.first()
        val userSetHazardsEnabled = settingsRepository.userSetHazardsEnabled.first()

        when (mode) {
            DriverMode.LEARNER -> {
                if (!userSetPromptSensitivity) {
                    settingsRepository.setPromptSensitivity(PromptSensitivity.EXTRA_HELP)
                }
                if (!userSetHazardsEnabled) {
                    settingsRepository.setHazardsEnabled(true)
                }
            }
            DriverMode.NEW_DRIVER -> {
                if (!userSetLowStressRouting) {
                    settingsRepository.setLowStressRoutingEnabled(true)
                }
                if (!userSetPromptSensitivity) {
                    settingsRepository.setPromptSensitivity(PromptSensitivity.STANDARD)
                }
                if (!userSetHazardsEnabled) {
                    settingsRepository.setHazardsEnabled(true)
                }
            }
            DriverMode.STANDARD -> {
                if (!userSetPromptSensitivity) {
                    settingsRepository.setPromptSensitivity(PromptSensitivity.MINIMAL)
                }
            }
        }

        driverProfileRepository.markModeSeen(mode)
    }
}
