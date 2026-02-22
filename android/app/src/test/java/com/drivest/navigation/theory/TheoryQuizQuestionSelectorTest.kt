package com.drivest.navigation.theory

import com.drivest.navigation.theory.models.TheoryAnswerOption
import com.drivest.navigation.theory.models.TheoryQuestion
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.quiz.TheoryQuizQuestionSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TheoryQuizQuestionSelectorTest {

    @Test
    fun selectsTopicScopedQuestions() {
        val selected = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = buildQuestions(),
            bookmarks = emptySet(),
            wrongQueue = emptySet(),
            quizMode = TheoryNavigation.QUIZ_MODE_TOPIC,
            quizTopicId = "topic_a",
            requestedCount = 2,
            random = Random(7)
        )
        assertEquals(2, selected.size)
        assertTrue(selected.all { it.topicId == "topic_a" })
    }

    @Test
    fun selectsBookmarkedQuestionsInBookmarkMode() {
        val selected = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = buildQuestions(),
            bookmarks = setOf("q2", "q4"),
            wrongQueue = emptySet(),
            quizMode = TheoryNavigation.QUIZ_MODE_BOOKMARKS,
            quizTopicId = null,
            requestedCount = 10,
            random = Random(5)
        )
        assertEquals(setOf("q2", "q4"), selected.map { it.id }.toSet())
    }

    @Test
    fun selectsWrongQueueInWrongMode() {
        val selected = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = buildQuestions(),
            bookmarks = emptySet(),
            wrongQueue = setOf("q1", "q3"),
            quizMode = TheoryNavigation.QUIZ_MODE_WRONG,
            quizTopicId = null,
            requestedCount = 10,
            random = Random(3)
        )
        assertEquals(setOf("q1", "q3"), selected.map { it.id }.toSet())
    }

    @Test
    fun returnsEmptyWhenPoolIsEmpty() {
        val selected = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = buildQuestions(),
            bookmarks = emptySet(),
            wrongQueue = emptySet(),
            quizMode = TheoryNavigation.QUIZ_MODE_TOPIC,
            quizTopicId = "missing_topic",
            requestedCount = 10
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun topicModeIsDeterministicAndSequentialByQuestionNumber() {
        val questions = listOf(
            question(id = "topic_a_q_10", topicId = "topic_a"),
            question(id = "topic_a_q_2", topicId = "topic_a"),
            question(id = "topic_a_q_1", topicId = "topic_a"),
            question(id = "topic_a_q_3", topicId = "topic_a")
        )

        val selected = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = questions,
            bookmarks = emptySet(),
            wrongQueue = emptySet(),
            quizMode = TheoryNavigation.QUIZ_MODE_TOPIC,
            quizTopicId = "topic_a",
            requestedCount = 4,
            random = Random(999)
        )

        assertEquals(
            listOf("topic_a_q_1", "topic_a_q_2", "topic_a_q_3", "topic_a_q_10"),
            selected.map { it.id }
        )
    }

    private fun buildQuestions(): List<TheoryQuestion> {
        return listOf(
            question(id = "q1", topicId = "topic_a"),
            question(id = "q2", topicId = "topic_a"),
            question(id = "q3", topicId = "topic_b"),
            question(id = "q4", topicId = "topic_b")
        )
    }

    private fun question(id: String, topicId: String): TheoryQuestion {
        return TheoryQuestion(
            id = id,
            topicId = topicId,
            prompt = "Prompt $id",
            options = listOf(
                TheoryAnswerOption(id = "a", text = "Option A"),
                TheoryAnswerOption(id = "b", text = "Option B"),
                TheoryAnswerOption(id = "c", text = "Option C"),
                TheoryAnswerOption(id = "d", text = "Option D")
            ),
            correctOptionId = "a",
            explanation = "Explain $id",
            difficulty = "easy"
        )
    }
}
