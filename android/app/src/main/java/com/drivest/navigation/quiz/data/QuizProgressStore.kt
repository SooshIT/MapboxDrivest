package com.drivest.navigation.quiz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quizProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drivest_quiz_progress"
)

class QuizProgressStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.quizProgressDataStore
) {

    private companion object {
        val LAST_SELECTED_LOCALE = stringPreferencesKey("last_selected_locale")
        val SOLO_BEST_SCORE = intPreferencesKey("solo_best_score")
        val SOLO_TOTAL_CORRECT = intPreferencesKey("solo_total_correct")
        val SOLO_TOTAL_ANSWERED = intPreferencesKey("solo_total_answered")
    }

    val lastLocale: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_SELECTED_LOCALE] ?: "en"
    }

    val soloBestScore: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SOLO_BEST_SCORE] ?: 0
    }

    val accuracy: Flow<Float> = dataStore.data.map { prefs ->
        val correct = prefs[SOLO_TOTAL_CORRECT] ?: 0
        val answered = prefs[SOLO_TOTAL_ANSWERED] ?: 0
        if (answered == 0) 0f else (correct.toFloat() / answered)
    }

    suspend fun recordSoloResult(score: Int, correct: Int, total: Int, locale: String) {
        dataStore.edit { prefs ->
            prefs[LAST_SELECTED_LOCALE] = locale

            val currentBest = prefs[SOLO_BEST_SCORE] ?: 0
            if (score > currentBest) {
                prefs[SOLO_BEST_SCORE] = score
            }

            val currentCorrect = prefs[SOLO_TOTAL_CORRECT] ?: 0
            prefs[SOLO_TOTAL_CORRECT] = currentCorrect + correct

            val currentAnswered = prefs[SOLO_TOTAL_ANSWERED] ?: 0
            prefs[SOLO_TOTAL_ANSWERED] = currentAnswered + total
        }
    }
}
