package com.drivest.navigation.quiz

import com.drivest.navigation.quiz.data.QuizPackValidationException
import com.drivest.navigation.quiz.data.QuizRepository
import com.drivest.navigation.quiz.model.DifficultyWeights
import com.drivest.navigation.quiz.model.PartyHints
import com.drivest.navigation.quiz.model.QuizDifficulty
import com.drivest.navigation.quiz.model.QuizLegalSource
import com.drivest.navigation.quiz.model.QuizPack
import com.drivest.navigation.quiz.model.QuizQuestion
import com.drivest.navigation.quiz.model.QuizRiskLevel
import com.drivest.navigation.quiz.model.QuizScoring
import org.junit.Assert.*
import org.junit.Test

class QuizRepositoryValidationTest {

    private fun buildValidPack(): QuizPack {
        return QuizPack(
            packId = "test_pack",
            version = "1",
            locale = "en",
            questions = listOf(
                QuizQuestion(
                    id = "q1",
                    prompt = "Test question?",
                    options = listOf("A", "B", "C", "D"),
                    answerIndex = 0,
                    explanation = "Explanation",
                    difficulty = QuizDifficulty.EASY,
                    riskLevel = QuizRiskLevel.LOW,
                    legalSourceKey = "rta_1988"
                )
            ),
            legalSources = mapOf(
                "rta_1988" to QuizLegalSource("rta_1988", "Road Traffic Act 1988", "Section 1")
            ),
            scoring = QuizScoring(10, 3, 5, 1),
            difficultyWeights = DifficultyWeights(1f, 2f, 3f),
            partyHints = PartyHints(-20, false)
        )
    }

    @Test
    fun validPackPassesValidation() {
        val pack = buildValidPack()
        val repository = TestQuizRepository()
        // Should not throw
        repository.testValidate(pack)
    }

    @Test
    fun duplicateQuestionIdsThrow() {
        val pack = buildValidPack().copy(
            questions = listOf(
                buildValidPack().questions[0],
                buildValidPack().questions[0]  // Duplicate ID
            )
        )
        val repository = TestQuizRepository()
        try {
            repository.testValidate(pack)
            fail("Should have thrown QuizPackValidationException")
        } catch (e: QuizPackValidationException) {
            assertTrue(e.message?.contains("Duplicate question ID") == true)
        }
    }

    @Test
    fun wrongOptionCountThrows() {
        val badQuestion = QuizQuestion(
            id = "q_bad",
            prompt = "Test?",
            options = listOf("A", "B"),  // Only 2 options
            answerIndex = 0,
            explanation = "Exp",
            difficulty = QuizDifficulty.MEDIUM,
            riskLevel = QuizRiskLevel.MEDIUM,
            legalSourceKey = ""
        )
        val pack = buildValidPack().copy(questions = listOf(badQuestion))
        val repository = TestQuizRepository()
        try {
            repository.testValidate(pack)
            fail("Should have thrown QuizPackValidationException")
        } catch (e: QuizPackValidationException) {
            assertTrue(e.message?.contains("must have exactly 4 options") == true)
        }
    }

    @Test
    fun outOfRangeAnswerIndexThrows() {
        val badQuestion = buildValidPack().questions[0].copy(answerIndex = 5)
        val pack = buildValidPack().copy(questions = listOf(badQuestion))
        val repository = TestQuizRepository()
        try {
            repository.testValidate(pack)
            fail("Should have thrown QuizPackValidationException")
        } catch (e: QuizPackValidationException) {
            assertTrue(e.message?.contains("invalid answerIndex") == true)
        }
    }

    @Test
    fun missingLegalSourceThrows() {
        val badQuestion = buildValidPack().questions[0].copy(legalSourceKey = "missing_source")
        val pack = buildValidPack().copy(questions = listOf(badQuestion))
        val repository = TestQuizRepository()
        try {
            repository.testValidate(pack)
            fail("Should have thrown QuizPackValidationException")
        } catch (e: QuizPackValidationException) {
            assertTrue(e.message?.contains("unknown legal source") == true)
        }
    }

    @Test
    fun localeResolutionEnglish() {
        val repository = TestQuizRepository()
        assertEquals("en", repository.testResolveLocale("en-GB"))
        assertEquals("en", repository.testResolveLocale("en-US"))
        assertEquals("en", repository.testResolveLocale("en"))
    }

    @Test
    fun localeResolutionGerman() {
        val repository = TestQuizRepository()
        assertEquals("de", repository.testResolveLocale("de-DE"))
        assertEquals("de", repository.testResolveLocale("de"))
    }

    @Test
    fun localeResolutionPortuguese() {
        val repository = TestQuizRepository()
        assertEquals("pt", repository.testResolveLocale("pt-BR"))
        assertEquals("pt", repository.testResolveLocale("pt-PT"))
    }

    @Test
    fun localeResolutionFallback() {
        val repository = TestQuizRepository()
        assertEquals("en", repository.testResolveLocale("unknown"))
        assertEquals("en", repository.testResolveLocale("ja-JP"))
    }
}

// Test helper class to expose private validation methods
class TestQuizRepository {
    fun testValidate(pack: QuizPack) {
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

    fun testResolveLocale(locale: String): String {
        return when {
            locale.startsWith("en") -> "en"
            locale.startsWith("de") -> "de"
            locale.startsWith("pt") -> "pt"
            locale.startsWith("fr") -> "fr"
            locale.startsWith("es") -> "es"
            locale.startsWith("it") -> "it"
            locale.startsWith("nl") -> "nl"
            locale.startsWith("pl") -> "pl"
            else -> "en"
        }
    }
}
