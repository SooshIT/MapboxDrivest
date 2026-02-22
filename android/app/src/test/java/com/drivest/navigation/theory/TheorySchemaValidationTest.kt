package com.drivest.navigation.theory

import com.drivest.navigation.theory.content.TheorySchemaValidator
import com.drivest.navigation.theory.models.TheoryAnswerOption
import com.drivest.navigation.theory.models.TheoryLesson
import com.drivest.navigation.theory.models.TheoryPack
import com.drivest.navigation.theory.models.TheoryQuestion
import com.drivest.navigation.theory.models.TheorySign
import com.drivest.navigation.theory.models.TheoryTopic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TheorySchemaValidationTest {

    @Test
    fun validPackPassesSchemaValidation() {
        val result = TheorySchemaValidator.validate(buildValidPack())
        assertTrue(result.errors.joinToString(", "), result.isValid)
    }

    @Test
    fun insufficientQuestionCountFailsValidation() {
        val pack = buildValidPack().copy(questions = buildValidPack().questions.take(10))
        val result = TheorySchemaValidator.validate(pack)
        assertFalse(result.isValid)
    }

    private fun buildValidPack(): TheoryPack {
        val topics = (1..12).map { index ->
            TheoryTopic(
                id = "topic_$index",
                title = "Topic $index",
                description = "Description $index",
                tags = listOf("tag_$index"),
                lessonIds = listOf("topic_${index}_lesson_1", "topic_${index}_lesson_2"),
                questionIds = (1..20).map { q -> "topic_${index}_q_$q" }
            )
        }

        val lessons = topics.flatMap { topic ->
            listOf(
                TheoryLesson(
                    id = "${topic.id}_lesson_1",
                    topicId = topic.id,
                    title = "${topic.title} Lesson 1",
                    content = "content",
                    keyPoints = listOf("k1", "k2"),
                    signIds = listOf("${topic.id}_sign_1")
                ),
                TheoryLesson(
                    id = "${topic.id}_lesson_2",
                    topicId = topic.id,
                    title = "${topic.title} Lesson 2",
                    content = "content",
                    keyPoints = listOf("k1", "k2"),
                    signIds = listOf("${topic.id}_sign_1")
                )
            )
        }

        val signs = topics.map { topic ->
            TheorySign(
                id = "${topic.id}_sign_1",
                topicId = topic.id,
                name = "Sign ${topic.id}",
                meaning = "Meaning",
                memoryHint = "Hint"
            )
        }

        val questions = topics.flatMap { topic ->
            (1..20).map { questionIndex ->
                TheoryQuestion(
                    id = "${topic.id}_q_$questionIndex",
                    topicId = topic.id,
                    prompt = "Question $questionIndex",
                    options = listOf(
                        TheoryAnswerOption(id = "a", text = "A"),
                        TheoryAnswerOption(id = "b", text = "B"),
                        TheoryAnswerOption(id = "c", text = "C"),
                        TheoryAnswerOption(id = "d", text = "D")
                    ),
                    correctOptionId = "a",
                    explanation = "Explanation",
                    difficulty = "easy"
                )
            }
        }

        return TheoryPack(
            version = "1.0.0",
            updatedAt = "2026-02-21",
            topics = topics,
            lessons = lessons,
            signs = signs,
            questions = questions
        )
    }
}
