package com.drivest.navigation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object AppLanguageManager {

    val supportedLanguages: List<AppLanguageSetting> = AppLanguageSetting.entries

    fun label(context: Context, setting: AppLanguageSetting): String {
        return context.getString(setting.labelResId)
    }

    fun apply(setting: AppLanguageSetting) {
        val targetLocales = LocaleListCompat.forLanguageTags(setting.bcp47Tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != targetLocales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    fun applyPersistedLanguageBlocking(context: Context) {
        val appContext = context.applicationContext
        val setting = runCatching {
            runBlocking {
                SettingsRepository(appContext).appLanguage.first()
            }
        }.getOrDefault(AppLanguageSetting.ENGLISH_UK)
        apply(setting)
    }
}
