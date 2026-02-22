package com.drivest.navigation.settings

enum class VoiceModeSetting(val storageValue: String) {
    ALL("all"),
    ALERTS("alerts"),
    MUTE("mute");

    companion object {
        fun fromStorage(value: String?): VoiceModeSetting {
            return entries.firstOrNull { it.storageValue == value } ?: ALL
        }
    }
}

enum class PreferredUnitsSetting(val storageValue: String) {
    UK_MPH("uk_mph"),
    METRIC_KMH("metric_kmh");

    companion object {
        fun fromStorage(value: String?): PreferredUnitsSetting {
            return entries.firstOrNull { it.storageValue == value } ?: UK_MPH
        }
    }
}

enum class DataSourceMode(val storageValue: String) {
    ASSETS_ONLY("assets_only"),
    BACKEND_ONLY("backend_only"),
    BACKEND_THEN_CACHE_THEN_ASSETS("backend_then_cache_then_assets");

    companion object {
        fun fromStorage(value: String?): DataSourceMode {
            return entries.firstOrNull { it.storageValue == value } ?: BACKEND_THEN_CACHE_THEN_ASSETS
        }
    }
}

enum class PromptSensitivity(val storageValue: String) {
    MINIMAL("minimal"),
    STANDARD("standard"),
    EXTRA_HELP("extra_help");

    companion object {
        fun fromStorage(value: String?): PromptSensitivity {
            return entries.firstOrNull { it.storageValue == value } ?: STANDARD
        }
    }
}

data class SettingsFeatureFlags(
    val dataSourceMode: DataSourceMode = DataSourceMode.BACKEND_THEN_CACHE_THEN_ASSETS,
    val hazardsEnabled: Boolean = true,
    val promptSensitivity: PromptSensitivity = PromptSensitivity.STANDARD,
    val lowStressRoutingEnabled: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val notificationsPreference: Boolean = true
) {
    val visualPromptsEnabled: Boolean
        get() = hazardsEnabled

    val lowStressModeEnabled: Boolean
        get() = lowStressRoutingEnabled
}
