package com.drivest.navigation.subscription

import com.drivest.navigation.BuildConfig
import com.drivest.navigation.profile.DriverProfileRepository
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FeatureAccessManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    @Suppress("UNUSED_PARAMETER")
    private val driverProfileRepository: DriverProfileRepository? = null,
    private val freePracticeCentreAllowlist: Set<String> = emptySet(),
    private val debugFreePracticeBypassEnabled: Boolean = false
) {

    fun getTier(): Flow<SubscriptionTier> = subscriptionRepository.effectiveTier

    fun hasPracticeAccess(centreId: String?): Flow<Boolean> {
        return combine(
            subscriptionRepository.effectiveTier,
            settingsRepository.practiceCentreId
        ) { tier, practiceCentreId ->
            when (tier) {
                SubscriptionTier.FREE -> {
                    if (debugFreePracticeBypassEnabled && BuildConfig.DEBUG) {
                        return@combine true
                    }
                    !centreId.isNullOrBlank() && freePracticeCentreAllowlist.contains(centreId)
                }
                SubscriptionTier.PRACTICE_MONTHLY -> {
                    when {
                        practiceCentreId.isNullOrBlank() -> false
                        centreId.isNullOrBlank() -> true
                        else -> centreId == practiceCentreId
                    }
                }
                SubscriptionTier.GLOBAL_ANNUAL -> true
            }
        }
    }

    fun hasGlobalNavigationAccess(): Flow<Boolean> {
        return subscriptionRepository.effectiveTier.map { tier ->
            tier == SubscriptionTier.GLOBAL_ANNUAL
        }
    }

    fun hasTrainingPlanAccess(): Flow<Boolean> {
        return subscriptionRepository.effectiveTier.map { tier ->
            tier == SubscriptionTier.PRACTICE_MONTHLY || tier == SubscriptionTier.GLOBAL_ANNUAL
        }
    }

    fun hasInstructorAccess(): Flow<Boolean> {
        return subscriptionRepository.effectiveTier.map { tier ->
            tier == SubscriptionTier.GLOBAL_ANNUAL
        }
    }

    fun hasLowStressRoutingAccess(): Flow<Boolean> {
        return subscriptionRepository.effectiveTier.map { tier ->
            tier == SubscriptionTier.PRACTICE_MONTHLY || tier == SubscriptionTier.GLOBAL_ANNUAL
        }
    }

    fun hasOfflineAccess(): Flow<Boolean> {
        return subscriptionRepository.effectiveTier.map { tier ->
            tier == SubscriptionTier.PRACTICE_MONTHLY || tier == SubscriptionTier.GLOBAL_ANNUAL
        }
    }
}
