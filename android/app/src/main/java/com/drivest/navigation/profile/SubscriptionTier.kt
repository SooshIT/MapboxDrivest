package com.drivest.navigation.profile

enum class SubscriptionTier(val storageValue: String) {
    LEARNER("learner"),
    ANNUAL("annual");

    companion object {
        fun fromStorage(value: String?): SubscriptionTier {
            return entries.firstOrNull { it.storageValue == value } ?: LEARNER
        }
    }
}

