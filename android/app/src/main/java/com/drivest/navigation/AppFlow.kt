package com.drivest.navigation

object AppFlow {
    const val EXTRA_APP_MODE = "extra_app_mode"
    const val EXTRA_CENTRE_ID = "extra_centre_id"
    const val EXTRA_ROUTE_ID = "extra_route_id"
    const val EXTRA_DESTINATION_LAT = "extra_destination_lat"
    const val EXTRA_DESTINATION_LON = "extra_destination_lon"
    const val EXTRA_DESTINATION_NAME = "extra_destination_name"
    const val EXTRA_DEBUG_AUTOSTART = "extra_debug_autostart"
    const val EXTRA_DEBUG_CYCLE = "extra_debug_cycle"
    const val MODE_PRACTICE = "practice"
    const val MODE_NAV = "nav"

    const val ACTION_DEBUG_COMMAND = "com.drivest.navigation.DEBUG_COMMAND"
    const val EXTRA_DEBUG_COMMAND = "extra_debug_command"
    const val DEBUG_START_NAV = "start_nav"
    const val DEBUG_STOP_NAV = "stop_nav"
    const val DEBUG_START_PRACTICE = "start_practice"
    const val DEBUG_STOP_PRACTICE = "stop_practice"

    enum class OnboardingStep {
        CONSENT,
        AGE,
        ANALYTICS,
        NOTIFICATIONS,
        COMPLETE
    }

    fun resolveOnboardingStep(
        needsConsent: Boolean,
        needsAge: Boolean,
        analyticsConsentAtMs: Long,
        notificationsConsentAtMs: Long
    ): OnboardingStep {
        return when {
            needsConsent -> OnboardingStep.CONSENT
            needsAge -> OnboardingStep.AGE
            analyticsConsentAtMs <= 0L -> OnboardingStep.ANALYTICS
            notificationsConsentAtMs <= 0L -> OnboardingStep.NOTIFICATIONS
            else -> OnboardingStep.COMPLETE
        }
    }
}
