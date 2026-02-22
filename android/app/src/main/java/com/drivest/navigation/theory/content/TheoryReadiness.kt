package com.drivest.navigation.theory.content

import com.drivest.navigation.theory.storage.TheoryProgress

enum class TheoryReadinessLabel {
    BUILDING,
    ALMOST_READY,
    READY
}

data class TheoryReadiness(
    val score: Int,
    val label: TheoryReadinessLabel,
    val masteredTopicsPercent: Int
)

object TheoryReadinessCalculator {

    private const val TOPIC_MASTERY_THRESHOLD = 75

    fun calculate(
        progress: TheoryProgress,
        totalTopics: Int
    ): TheoryReadiness {
        val safeTotalTopics = totalTopics.coerceAtLeast(1)
        val masteredTopicsCount = progress.topicStats.values.count { stat ->
            stat.masteryPercent >= TOPIC_MASTERY_THRESHOLD
        }
        val masteryPercent = ((masteredTopicsCount * 100f) / safeTotalTopics).toInt().coerceIn(0, 100)

        val accuracyPercent = if (progress.topicStats.isEmpty()) {
            0
        } else {
            val totalCorrect = progress.topicStats.values.sumOf { it.correct }
            val totalAttempts = progress.topicStats.values.sumOf { it.attempts }.coerceAtLeast(1)
            ((totalCorrect * 100f) / totalAttempts).toInt().coerceIn(0, 100)
        }

        val consistencyBonus = (progress.streakDays * 2).coerceAtMost(10)
        val rawScore = ((masteryPercent * 0.6f) + (accuracyPercent * 0.4f)).toInt() + consistencyBonus
        val score = rawScore.coerceIn(0, 100)
        val label = when {
            score >= 75 -> TheoryReadinessLabel.READY
            score >= 40 -> TheoryReadinessLabel.ALMOST_READY
            else -> TheoryReadinessLabel.BUILDING
        }

        return TheoryReadiness(
            score = score,
            label = label,
            masteredTopicsPercent = masteryPercent
        )
    }
}
