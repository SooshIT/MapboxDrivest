package com.drivest.navigation.settings

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object AppearanceModeManager {

    fun next(current: AppearanceModeSetting): AppearanceModeSetting {
        return when (current) {
            AppearanceModeSetting.AUTO -> AppearanceModeSetting.DAY
            AppearanceModeSetting.DAY -> AppearanceModeSetting.NIGHT
            AppearanceModeSetting.NIGHT -> AppearanceModeSetting.AUTO
        }
    }

    fun applyAppCompatMode(mode: AppearanceModeSetting) {
        val target = when (mode) {
            AppearanceModeSetting.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceModeSetting.DAY -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceModeSetting.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }

    fun isNightActive(context: Context, mode: AppearanceModeSetting): Boolean {
        return when (mode) {
            AppearanceModeSetting.DAY -> false
            AppearanceModeSetting.NIGHT -> true
            AppearanceModeSetting.AUTO -> isSystemNight(context)
        }
    }

    fun isSystemNight(context: Context): Boolean {
        val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }
}
