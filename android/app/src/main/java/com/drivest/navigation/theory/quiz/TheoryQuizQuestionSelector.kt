package com.drivest.navigation.theory.quiz

import com.drivest.navigation.theory.models.TheoryQuestion
import com.drivest.navigation.theory.navigation.TheoryNavigation
import kotlin.math.min
import kotlin.random.Random

object TheoryQuizQuestionSelector {
    fun selectQuestions(
        allQuestions: List<TheoryQuestion>,
        bookmarks: Set<String>,
        wrongQueue: Set<String>,
        quizMode: String,
        quizTopicId: String?,
        requestedCount: Int,
        random: Random = Random.Default
    ): List<TheoryQuestion> {
        if (allQuestions.isEmpty()) return emptyList()
        val safeCount = requestedCount.coerceAtLeast(1)
        val pool = when (quizMode) {
            TheoryNavigation.QUIZ_MODE_BOOKMARKS -> {
                allQuestions.filter { question -> bookmarks.contains(question.id) }
            }
            TheoryNavigation.QUIZ_MODE_WRONG -> {
                allQuestions.filter { question -> wrongQueue.contains(question.id) }
            }
            else -> {
                val topicId = quizTopicId.orEmpty()
                if (topicId.isBlank()) {
                    allQuestions
                } else {
                    allQuestions.filter { question -> question.topicId == topicId }
                }
            }
        }
        if (pool.isEmpty()) return emptyList()

        // Topic quizzes should feel like a progression (q1 -> q2 -> q3...), not random jumps.
        val orderedPool = pool.sortedWith(
            compareBy<TheoryQuestion>(
                { questionOrderIndex(it.id) },
                { it.id }
            )
        )

        return when (quizMode) {
            TheoryNavigation.QUIZ_MODE_TOPIC -> orderedPool.take(min(safeCount, orderedPool.size))
            else -> orderedPool.shuffled(random).take(min(safeCount, orderedPool.size))
        }
    }

    private fun questionOrderIndex(questionId: String): Int {
        val numberedSuffix = numberPattern.find(questionId)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (numberedSuffix != null) return numberedSuffix
        val trailingDigits = trailingDigitsPattern.find(questionId)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return trailingDigits ?: Int.MAX_VALUE
    }

    private val numberPattern = Regex("_q_(\\d+)$")
    private val trailingDigitsPattern = Regex("(\\d+)$")
}
