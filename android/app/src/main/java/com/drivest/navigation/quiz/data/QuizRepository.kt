package com.drivest.navigation.quiz.data

import android.content.Context
import com.drivest.navigation.quiz.model.QuizPack
import org.json.JSONObject

class QuizRepository(private val context: Context) {

    fun loadPack(packId: String, locale: String): QuizPack {
        val resolvedLocale = resolveLocale(locale)
        val fileName = "quizzes/$packId/pack_$resolvedLocale.json"

        return try {
            val jsonString = context.assets.open(fileName)
                .bufferedReader().use { it.readText() }
            val pack = QuizPack.fromJson(jsonString)
            validate(pack)
            pack
        } catch (e: Exception) {
            // Try fallback to 'en' if not already tried
            if (resolvedLocale != "en") {
                val fallbackFileName = "quizzes/$packId/pack_en.json"
                try {
                    val jsonString = context.assets.open(fallbackFileName)
                        .bufferedReader().use { it.readText() }
                    val pack = QuizPack.fromJson(jsonString)
                    validate(pack)
                    pack
                } catch (e2: Exception) {
                    throw QuizPackValidationException("Failed to load quiz pack '$packId': ${e2.message}")
                }
            } else {
                throw QuizPackValidationException("Failed to load quiz pack '$packId': ${e.message}")
            }
        }
    }

    fun listAvailableLocales(packId: String): List<String> {
        return try {
            val jsonString = context.assets.open("quizzes/$packId/locales.json")
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val localesArray = obj.optJSONArray("locales") ?: return emptyList()
            val locales = mutableListOf<String>()
            for (i in 0 until localesArray.length()) {
                locales.add(localesArray.optString(i, ""))
            }
            locales
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun resolveLocale(locale: String): String {
        // Map locale codes to language codes
        return when {
            locale.startsWith("en") -> "en"
            locale.startsWith("de") -> "de"
            locale.startsWith("pt") -> "pt"
            locale.startsWith("fr") -> "fr"
            locale.startsWith("es") -> "es"
            locale.startsWith("it") -> "it"
            locale.startsWith("nl") -> "nl"
            locale.startsWith("pl") -> "pl"
            else -> "en"  // Default fallback
        }
    }

    private fun validate(pack: QuizPack) {
        // Check for duplicate question IDs
        val idSet = mutableSetOf<String>()
        for (question in pack.questions) {
            if (question.id in idSet) {
                throw QuizPackValidationException("Duplicate question ID: ${question.id}")
            }
            idSet.add(question.id)
        }

        // Validate each question
        for (question in pack.questions) {
            if (question.options.size != 4) {
                throw QuizPackValidationException(
                    "Question '${question.id}' must have exactly 4 options, got ${question.options.size}"
                )
            }

            if (question.answerIndex !in 0..3) {
                throw QuizPackValidationException(
                    "Question '${question.id}' has invalid answerIndex: ${question.answerIndex}"
                )
            }

            if (question.legalSourceKey.isNotEmpty() && question.legalSourceKey !in pack.legalSources) {
                throw QuizPackValidationException(
                    "Question '${question.id}' references unknown legal source: ${question.legalSourceKey}"
                )
            }
        }
    }
}
