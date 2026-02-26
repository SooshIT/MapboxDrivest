package com.drivest.navigation.quiz

import com.drivest.navigation.quiz.engine.PartyPlayer
import com.drivest.navigation.quiz.engine.QuizGameEngine
import com.drivest.navigation.quiz.engine.QuizSettings
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

class QuizGameEngineTest {

    private fun buildTestPack(numQuestions: Int = 5): QuizPack {
        val questions = mutableListOf<QuizQuestion>()
        repeat(numQuestions) { i ->
            questions.add(
                QuizQuestion(
                    id = "q$i",
                    prompt = "Question $i?",
                    options = listOf("A", "B", "C", "D"),
                    answerIndex = i % 4,
                    explanation = "Explanation $i",
                    difficulty = when {
                        i % 3 == 0 -> QuizDifficulty.EASY
                        i % 3 == 1 -> QuizDifficulty.MEDIUM
                        else -> QuizDifficulty.HARD
                    },
                    riskLevel = QuizRiskLevel.MEDIUM,
                    legalSourceKey = ""
                )
            )
        }

        return QuizPack(
            packId = "test",
            version = "1",
            locale = "en",
            questions = questions,
            legalSources = emptyMap(),
            scoring = QuizScoring(basePoints = 10, streakBonusThreshold = 3, streakBonusPoints = 5, timeBonusPerSecond = 1),
            difficultyWeights = DifficultyWeights(easy = 1f, medium = 2f, hard = 3f),
            partyHints = PartyHints(eliminationThreshold = -20, eliminationEnabled = false)
        )
    }

    @Test
    fun soloCorrectAnswerEarnsBasePoints() {
        val pack = buildTestPack()
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        val question = session.questions[0]
        val result = QuizGameEngine.submitSoloAnswer(session, question.answerIndex)

        assertTrue(result.isCorrect)
        assertEquals(10, result.pointsEarned)
        assertEquals(10, session.score)
    }

    @Test
    fun soloWrongAnswerEarnsZeroPoints() {
        val pack = buildTestPack()
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        val question = session.questions[0]
        val wrongIndex = (question.answerIndex + 1) % 4
        val result = QuizGameEngine.submitSoloAnswer(session, wrongIndex)

        assertFalse(result.isCorrect)
        assertEquals(0, result.pointsEarned)
        assertEquals(0, session.score)
    }

    @Test
    fun soloStreakBonusApplies() {
        val pack = buildTestPack(5)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        // Answer 3 questions correctly to hit streak threshold
        val threshold = session.pack.scoring.streakBonusThreshold
        repeat(threshold) { i ->
            val question = session.questions[i]
            val result = QuizGameEngine.submitSoloAnswer(session, question.answerIndex)
            if (i == threshold - 1) {
                // On 3rd correct answer, bonus should apply
                assertTrue("Streak bonus not applied", result.pointsEarned > 10)
                assertEquals(5, result.pointsEarned - 10)  // streakBonusPoints = 5
            }
        }
    }

    @Test
    fun soloStreakResetsOnWrongAnswer() {
        val pack = buildTestPack(5)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        // Answer first question correctly
        val q0 = session.questions[0]
        QuizGameEngine.submitSoloAnswer(session, q0.answerIndex)
        assertEquals(1, session.streak)

        // Answer second question wrong
        val q1 = session.questions[1]
        val wrongIndex = (q1.answerIndex + 1) % 4
        QuizGameEngine.submitSoloAnswer(session, wrongIndex)
        assertEquals(0, session.streak)
    }

    @Test
    fun soloCompleteWhenAllQuestionsAnswered() {
        val pack = buildTestPack(3)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        assertFalse(QuizGameEngine.isSoloComplete(session))

        repeat(3) { i ->
            val q = session.questions[i]
            QuizGameEngine.submitSoloAnswer(session, q.answerIndex)
        }

        assertTrue(QuizGameEngine.isSoloComplete(session))
    }

    @Test
    fun partyTurnsRotateCorrectly() {
        val pack = buildTestPack(4)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createPartySession(pack, listOf("Alice", "Bob", "Charlie"), settings)

        assertEquals(0, session.currentPlayerIndex)

        val q0 = session.questions[0]
        val result0 = QuizGameEngine.submitPartyAnswer(session, q0.answerIndex)
        assertEquals(1, session.currentPlayerIndex)

        val q1 = session.questions[1]
        val result1 = QuizGameEngine.submitPartyAnswer(session, q1.answerIndex)
        assertEquals(2, session.currentPlayerIndex)

        val q2 = session.questions[2]
        val result2 = QuizGameEngine.submitPartyAnswer(session, q2.answerIndex)
        assertEquals(0, session.currentPlayerIndex)  // Wraps back to 0
    }

    @Test
    fun partyWinnerIsHighestScore() {
        val pack = buildTestPack(4)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createPartySession(pack, listOf("Alice", "Bob"), settings)

        // Alice answers correct
        val q0 = session.questions[0]
        QuizGameEngine.submitPartyAnswer(session, q0.answerIndex)

        // Bob answers wrong
        val q1 = session.questions[1]
        val wrongIndex = (q1.answerIndex + 1) % 4
        QuizGameEngine.submitPartyAnswer(session, wrongIndex)

        // Alice answers correct
        val q2 = session.questions[2]
        QuizGameEngine.submitPartyAnswer(session, q2.answerIndex)

        val winner = QuizGameEngine.getPartyWinner(session)
        assertEquals("Alice", winner?.name)
    }

    @Test
    fun partyCompleteWhenOnlyOnePlayerRemains() {
        val pack = buildTestPack(10)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createPartySession(pack, listOf("Alice", "Bob"), settings)

        // Manually eliminate Bob
        session.players[1].eliminated = true

        assertTrue(QuizGameEngine.isPartyComplete(session))
    }

    @Test
    fun getSoloResult() {
        val pack = buildTestPack(5)
        val settings = QuizSettings(timeLimitSeconds = null, difficultyWeighting = false)
        val session = QuizGameEngine.createSoloSession(pack, settings)

        // Answer 3 correctly, 2 wrong
        repeat(3) { i ->
            val q = session.questions[i]
            QuizGameEngine.submitSoloAnswer(session, q.answerIndex)
        }
        repeat(2) { i ->
            val q = session.questions[3 + i]
            val wrongIndex = (q.answerIndex + 1) % 4
            QuizGameEngine.submitSoloAnswer(session, wrongIndex)
        }

        val result = QuizGameEngine.getSoloResult(session)
        assertEquals(35, result.score)  // (10 + 10 + 10 + 5 bonus on streak=3)
        assertEquals(3, result.correct)
        assertEquals(5, result.total)
        assertEquals(0.6f, result.accuracy, 0.01f)
    }
}
