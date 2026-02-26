package com.drivest.navigation.quiz.engine

import com.drivest.navigation.quiz.model.QuizDifficulty
import com.drivest.navigation.quiz.model.QuizLegalSource
import com.drivest.navigation.quiz.model.QuizPack
import com.drivest.navigation.quiz.model.QuizQuestion
import kotlin.math.max
import kotlin.random.Random

// Session data classes

data class QuizSettings(
    val timeLimitSeconds: Int?,
    val difficultyWeighting: Boolean
)

data class SoloSession(
    val pack: QuizPack,
    val settings: QuizSettings,
    val questions: List<QuizQuestion>,
    var currentIndex: Int = 0,
    var score: Int = 0,
    var streak: Int = 0,
    var correctCount: Int = 0,
    var questionStartMs: Long = 0L
)

data class PartyPlayer(
    val name: String,
    var score: Int = 0,
    var eliminated: Boolean = false
)

data class PartySession(
    val pack: QuizPack,
    val settings: QuizSettings,
    val questions: List<QuizQuestion>,
    val players: List<PartyPlayer>,
    var currentPlayerIndex: Int = 0,
    var currentQuestionIndex: Int = 0,
    var currentStreak: Int = 0,
    var questionStartMs: Long = 0L
)

// Result data classes

data class SoloAnswerResult(
    val isCorrect: Boolean,
    val pointsEarned: Int,
    val newStreak: Int,
    val explanation: String,
    val legalSource: QuizLegalSource?
)

data class PartyAnswerResult(
    val isCorrect: Boolean,
    val pointsEarned: Int,
    val nextPlayerIndex: Int,
    val playerEliminated: Boolean,
    val explanation: String
)

data class SoloResult(
    val score: Int,
    val correct: Int,
    val total: Int,
    val accuracy: Float
)

// Pure game engine object

object QuizGameEngine {

    fun createSoloSession(
        pack: QuizPack,
        settings: QuizSettings,
        nowMs: Long = System.currentTimeMillis()
    ): SoloSession {
        val questions = if (settings.difficultyWeighting) {
            weightedShuffle(pack.questions, pack.difficultyWeights)
        } else {
            pack.questions.shuffled()
        }

        return SoloSession(
            pack = pack,
            settings = settings,
            questions = questions,
            questionStartMs = nowMs
        )
    }

    fun createPartySession(
        pack: QuizPack,
        playerNames: List<String>,
        settings: QuizSettings,
        nowMs: Long = System.currentTimeMillis()
    ): PartySession {
        val questions = if (settings.difficultyWeighting) {
            weightedShuffle(pack.questions, pack.difficultyWeights)
        } else {
            pack.questions.shuffled()
        }

        val players = playerNames.take(8).drop(1).let {
            // Ensure at least 2 players
            if (playerNames.size < 2) {
                playerNames.map { name -> PartyPlayer(name) }
            } else {
                playerNames.map { name -> PartyPlayer(name) }
            }
        }

        return PartySession(
            pack = pack,
            settings = settings,
            questions = questions,
            players = players,
            questionStartMs = nowMs
        )
    }

    fun submitSoloAnswer(
        session: SoloSession,
        answerIndex: Int,
        nowMs: Long = System.currentTimeMillis()
    ): SoloAnswerResult {
        val question = session.questions[session.currentIndex]
        val isCorrect = answerIndex == question.answerIndex

        if (isCorrect) {
            session.correctCount++
            session.streak++
        } else {
            session.streak = 0
        }

        val basePoints = if (isCorrect) session.pack.scoring.basePoints else 0
        var pointsEarned = basePoints

        // Streak bonus
        if (isCorrect && session.streak >= session.pack.scoring.streakBonusThreshold) {
            pointsEarned += session.pack.scoring.streakBonusPoints
        }

        // Time bonus
        session.settings.timeLimitSeconds?.let { limit ->
            val elapsedSeconds = (nowMs - session.questionStartMs) / 1000L
            val remainingSeconds = limit - elapsedSeconds
            if (isCorrect && remainingSeconds > 0) {
                val timeBonus = remainingSeconds.toInt() * session.pack.scoring.timeBonusPerSecond
                pointsEarned += timeBonus
            }
        }

        session.score += pointsEarned
        session.currentIndex++

        val legalSource = if (question.legalSourceKey.isNotEmpty()) {
            session.pack.legalSources[question.legalSourceKey]
        } else {
            null
        }

        return SoloAnswerResult(
            isCorrect = isCorrect,
            pointsEarned = pointsEarned,
            newStreak = session.streak,
            explanation = question.explanation,
            legalSource = legalSource
        )
    }

    fun submitPartyAnswer(
        session: PartySession,
        answerIndex: Int,
        nowMs: Long = System.currentTimeMillis()
    ): PartyAnswerResult {
        val question = session.questions[session.currentQuestionIndex]
        val isCorrect = answerIndex == question.answerIndex
        val currentPlayer = session.players[session.currentPlayerIndex]

        if (isCorrect) {
            session.currentStreak++
        } else {
            session.currentStreak = 0
        }

        val basePoints = if (isCorrect) session.pack.scoring.basePoints else 0
        var pointsEarned = basePoints

        // Streak bonus
        if (isCorrect && session.currentStreak >= session.pack.scoring.streakBonusThreshold) {
            pointsEarned += session.pack.scoring.streakBonusPoints
        }

        // Time bonus
        session.settings.timeLimitSeconds?.let { limit ->
            val elapsedSeconds = (nowMs - session.questionStartMs) / 1000L
            val remainingSeconds = limit - elapsedSeconds
            if (isCorrect && remainingSeconds > 0) {
                val timeBonus = remainingSeconds.toInt() * session.pack.scoring.timeBonusPerSecond
                pointsEarned += timeBonus
            }
        }

        currentPlayer.score += pointsEarned

        // Check elimination
        var playerEliminated = false
        if (session.pack.partyHints.eliminationEnabled &&
            currentPlayer.score <= session.pack.partyHints.eliminationThreshold
        ) {
            currentPlayer.eliminated = true
            playerEliminated = true
        }

        // Rotate to next active player
        var nextPlayerIndex = (session.currentPlayerIndex + 1) % session.players.size
        var rotations = 0
        while (session.players[nextPlayerIndex].eliminated && rotations < session.players.size) {
            nextPlayerIndex = (nextPlayerIndex + 1) % session.players.size
            rotations++
        }

        session.currentPlayerIndex = nextPlayerIndex
        session.currentQuestionIndex++

        return PartyAnswerResult(
            isCorrect = isCorrect,
            pointsEarned = pointsEarned,
            nextPlayerIndex = nextPlayerIndex,
            playerEliminated = playerEliminated,
            explanation = question.explanation
        )
    }

    fun isSoloComplete(session: SoloSession): Boolean {
        return session.currentIndex >= session.questions.size
    }

    fun isPartyComplete(session: PartySession): Boolean {
        val activePlayers = session.players.count { !it.eliminated }
        return activePlayers <= 1 || session.currentQuestionIndex >= session.questions.size
    }

    fun getSoloResult(session: SoloSession): SoloResult {
        val total = session.questions.size
        val accuracy = if (total > 0) session.correctCount.toFloat() / total else 0f
        return SoloResult(
            score = session.score,
            correct = session.correctCount,
            total = total,
            accuracy = accuracy
        )
    }

    fun getPartyWinner(session: PartySession): PartyPlayer? {
        return session.players
            .filter { !it.eliminated }
            .maxByOrNull { it.score }
            ?: session.players.maxByOrNull { it.score }
    }

    private fun weightedShuffle(
        questions: List<QuizQuestion>,
        weights: com.drivest.navigation.quiz.model.DifficultyWeights
    ): List<QuizQuestion> {
        // Build weighted list
        val weighted = mutableListOf<QuizQuestion>()
        for (question in questions) {
            val weight = when (question.difficulty) {
                QuizDifficulty.EASY -> weights.easy.toInt()
                QuizDifficulty.MEDIUM -> weights.medium.toInt()
                QuizDifficulty.HARD -> weights.hard.toInt()
            }
            repeat(weight) {
                weighted.add(question)
            }
        }

        // Shuffle and deduplicate while preserving order
        weighted.shuffle()
        val seen = mutableSetOf<String>()
        return weighted.filter { q ->
            if (q.id in seen) false else {
                seen.add(q.id)
                true
            }
        }
    }
}
