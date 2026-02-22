package com.drivest.navigation.theory

import com.drivest.navigation.BuildConfig

object TheoryFeatureFlags {
    fun isTheoryModuleEnabled(): Boolean {
        return BuildConfig.THEORY_MODULE_ENABLED || BuildConfig.DEBUG
    }
}
