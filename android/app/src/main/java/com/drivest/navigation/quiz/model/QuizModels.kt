package com.drivest.navigation.quiz.model

import org.json.JSONArray
import org.json.JSONObject

enum class QuizDifficulty {
    EASY, MEDIUM, HARD;

    companion object {
        fun fromString(s: String): QuizDifficulty =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: MEDIUM
    }
}

enum class QuizRiskLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromString(s: String): QuizRiskLevel =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: MEDIUM
    }
}

data class QuizLegalSource(
    val key: String,
    val title: String,
    val section: String
) {
    companion object {
        fun fromJson(obj: JSONObject): QuizLegalSource {
            return QuizLegalSource(
                key = obj.optString("key", ""),
                title = obj.optString("title", ""),
                section = obj.optString("section", "")
            )
        }
    }
}

data class QuizQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
    val difficulty: QuizDifficulty,
    val riskLevel: QuizRiskLevel,
    val legalSourceKey: String
) {
    companion object {
        fun fromJson(obj: JSONObject): QuizQuestion {
            val optionsArray = obj.optJSONArray("options") ?: JSONArray()
            val options = mutableListOf<String>()
            for (i in 0 until optionsArray.length()) {
                options.add(optionsArray.optString(i, ""))
            }

            return QuizQuestion(
                id = obj.optString("id", ""),
                prompt = obj.optString("prompt", ""),
                options = options,
                answerIndex = obj.optInt("answerIndex", 0),
                explanation = obj.optString("explanation", ""),
                difficulty = QuizDifficulty.fromString(obj.optString("difficulty", "medium")),
                riskLevel = QuizRiskLevel.fromString(obj.optString("riskLevel", "medium")),
                legalSourceKey = obj.optString("legalSourceKey", "")
            )
        }
    }
}

data class QuizScoring(
    val basePoints: Int,
    val streakBonusThreshold: Int,
    val streakBonusPoints: Int,
    val timeBonusPerSecond: Int
) {
    companion object {
        fun fromJson(obj: JSONObject): QuizScoring {
            return QuizScoring(
                basePoints = obj.optInt("basePoints", 10),
                streakBonusThreshold = obj.optInt("streakBonusThreshold", 3),
                streakBonusPoints = obj.optInt("streakBonusPoints", 5),
                timeBonusPerSecond = obj.optInt("timeBonusPerSecond", 1)
            )
        }
    }
}

data class DifficultyWeights(
    val easy: Float,
    val medium: Float,
    val hard: Float
) {
    companion object {
        fun fromJson(obj: JSONObject): DifficultyWeights {
            return DifficultyWeights(
                easy = obj.optDouble("easy", 1.0).toFloat(),
                medium = obj.optDouble("medium", 2.0).toFloat(),
                hard = obj.optDouble("hard", 3.0).toFloat()
            )
        }
    }
}

data class PartyHints(
    val eliminationThreshold: Int,
    val eliminationEnabled: Boolean
) {
    companion object {
        fun fromJson(obj: JSONObject): PartyHints {
            return PartyHints(
                eliminationThreshold = obj.optInt("eliminationThreshold", -20),
                eliminationEnabled = obj.optBoolean("eliminationEnabled", false)
            )
        }
    }
}

data class QuizPack(
    val packId: String,
    val version: String,
    val locale: String,
    val questions: List<QuizQuestion>,
    val legalSources: Map<String, QuizLegalSource>,
    val scoring: QuizScoring,
    val difficultyWeights: DifficultyWeights,
    val partyHints: PartyHints
) {
    companion object {
        fun fromJson(jsonString: String): QuizPack {
            val obj = JSONObject(jsonString)

            val questionsArray = obj.optJSONArray("questions") ?: JSONArray()
            val questions = mutableListOf<QuizQuestion>()
            for (i in 0 until questionsArray.length()) {
                questions.add(QuizQuestion.fromJson(questionsArray.getJSONObject(i)))
            }

            val legalSourcesObj = obj.optJSONObject("legalSources") ?: JSONObject()
            val legalSources = mutableMapOf<String, QuizLegalSource>()
            legalSourcesObj.keys().forEach { key ->
                legalSources[key] = QuizLegalSource.fromJson(legalSourcesObj.getJSONObject(key))
            }

            return QuizPack(
                packId = obj.optString("packId", ""),
                version = obj.optString("version", "1"),
                locale = obj.optString("locale", "en"),
                questions = questions,
                legalSources = legalSources,
                scoring = QuizScoring.fromJson(obj.optJSONObject("scoring") ?: JSONObject()),
                difficultyWeights = DifficultyWeights.fromJson(obj.optJSONObject("difficultyWeights") ?: JSONObject()),
                partyHints = PartyHints.fromJson(obj.optJSONObject("partyHints") ?: JSONObject())
            )
        }
    }
}
