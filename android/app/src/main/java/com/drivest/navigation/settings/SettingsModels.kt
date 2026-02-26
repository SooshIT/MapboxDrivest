package com.drivest.navigation.settings

import com.drivest.navigation.R

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

enum class AppearanceModeSetting(val storageValue: String) {
    AUTO("auto"),
    DAY("day"),
    NIGHT("night");

    companion object {
        fun fromStorage(value: String?): AppearanceModeSetting {
            return entries.firstOrNull { it.storageValue == value } ?: AUTO
        }
    }
}

enum class AppLanguageSetting(
    val storageValue: String,
    val bcp47Tag: String,
    val labelResId: Int
) {
    ENGLISH_UK("en-GB", "en-GB", R.string.settings_language_english_uk),
    FRENCH("fr", "fr", R.string.settings_language_french),
    GERMAN("de", "de", R.string.settings_language_german),
    SPANISH("es", "es", R.string.settings_language_spanish),
    ITALIAN("it", "it", R.string.settings_language_italian),
    DUTCH("nl", "nl", R.string.settings_language_dutch),
    PORTUGUESE_PORTUGAL("pt-PT", "pt-PT", R.string.settings_language_portuguese_portugal),
    POLISH("pl", "pl", R.string.settings_language_polish);

    companion object {
        fun fromStorage(value: String?): AppLanguageSetting {
            return entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: ENGLISH_UK
        }
    }
}

enum class SpeedLimitDisplaySetting(val storageValue: String) {
    ALWAYS("always"),
    ONLY_WHEN_SPEEDING("only_when_speeding"),
    NEVER("never");

    companion object {
        fun fromStorage(value: String?): SpeedLimitDisplaySetting {
            return entries.firstOrNull { it.storageValue == value } ?: ALWAYS
        }
    }
}

enum class SpeedingThresholdSetting(val storageValue: String) {
    AT_LIMIT("at_limit"),
    PLUS_SMALL("plus_small"),
    PLUS_LARGE("plus_large");

    companion object {
        fun fromStorage(value: String?): SpeedingThresholdSetting {
            return entries.firstOrNull { it.storageValue == value } ?: AT_LIMIT
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
