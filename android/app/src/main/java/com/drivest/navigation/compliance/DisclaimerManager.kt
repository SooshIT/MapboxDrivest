package com.drivest.navigation.compliance

import android.content.Context

class DisclaimerManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun isCurrentVersionAccepted(): Boolean {
        return preferences.getString(KEY_ACCEPTED_VERSION, null) == CURRENT_DISCLAIMER_VERSION
    }

    fun acceptCurrentVersion() {
        preferences.edit()
            .putString(KEY_ACCEPTED_VERSION, CURRENT_DISCLAIMER_VERSION)
            .apply()
    }

    fun currentVersion(): String = CURRENT_DISCLAIMER_VERSION

    companion object {
        private const val PREFERENCES_FILE = "drivest_disclaimer"
        private const val KEY_ACCEPTED_VERSION = "accepted_version"
        const val CURRENT_DISCLAIMER_VERSION = "2026-02-risk-advisory-v1"
    }
}

