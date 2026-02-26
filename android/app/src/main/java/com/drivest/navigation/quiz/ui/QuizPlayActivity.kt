package com.drivest.navigation.quiz.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.drivest.navigation.quiz.data.QuizRepository
import com.drivest.navigation.quiz.engine.QuizGameEngine
import com.drivest.navigation.quiz.engine.PartySession
import com.drivest.navigation.quiz.engine.QuizSettings
import com.drivest.navigation.quiz.engine.SoloSession
import com.drivest.navigation.quiz.model.QuizPack
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class QuizPlayActivity : AppCompatActivity() {

    private lateinit var quizRepository: QuizRepository
    private var pack: QuizPack? = null
    private var soloSession: SoloSession? = null
    private var partySession: PartySession? = null
    private var isParty = false
    private var timerEnabled = false
    private var currentTimer: CountDownTimer? = null

    private lateinit var progressText: TextView
    private lateinit var timerText: TextView
    private lateinit var playerChip: Chip
    private lateinit var questionText: TextView
    private lateinit var answerButtons: List<MaterialButton>
    private lateinit var explanationCard: LinearLayout
    private lateinit var nextButton: MaterialButton

    private var answerSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_play)

        quizRepository = QuizRepository(applicationContext)

        initializeViews()
        loadExtras()
        loadQuizPack()
    }

    private fun initializeViews() {
        progressText = findViewById(R.id.quizPlayProgressText)
        timerText = findViewById(R.id.quizPlayTimerText)
        playerChip = findViewById(R.id.quizPlayPlayerChip)
        questionText = findViewById(R.id.quizPlayQuestion)
        answerButtons = listOf(
            findViewById(R.id.quizPlayAnswer0),
            findViewById(R.id.quizPlayAnswer1),
            findViewById(R.id.quizPlayAnswer2),
            findViewById(R.id.quizPlayAnswer3)
        )
        explanationCard = findViewById(R.id.quizPlayExplanationCard)
        nextButton = findViewById(R.id.quizPlayNextButton)
    }

    private fun loadExtras() {
        val packId = intent.getStringExtra(QuizNavigation.EXTRA_PACK_ID) ?: QuizNavigation.DEFAULT_PACK_ID
        val locale = intent.getStringExtra(QuizNavigation.EXTRA_LOCALE) ?: "en"
        isParty = intent.getBooleanExtra(QuizNavigation.EXTRA_IS_PARTY, false)
        timerEnabled = intent.getBooleanExtra(QuizNavigation.EXTRA_TIMER_ENABLED, false)

        if (!timerEnabled) {
            timerText.visibility = TextView.GONE
        }
    }

    private fun loadQuizPack() {
        lifecycleScope.launch {
            try {
                val packId = intent.getStringExtra(QuizNavigation.EXTRA_PACK_ID) ?: QuizNavigation.DEFAULT_PACK_ID
                val locale = intent.getStringExtra(QuizNavigation.EXTRA_LOCALE) ?: "en"
                pack = quizRepository.loadPack(packId, locale)

                createGameSession()
                displayQuestion()
            } catch (e: Exception) {
                val intent = Intent(this@QuizPlayActivity, QuizErrorActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun createGameSession() {
        pack?.let { pack ->
            val settings = QuizSettings(
                timeLimitSeconds = if (timerEnabled) QuizNavigation.DEFAULT_TIMER_SECONDS else null,
                difficultyWeighting = false
            )

            if (isParty) {
                val playerNames = intent.getStringArrayListExtra(QuizNavigation.EXTRA_PLAYER_NAMES) ?: emptyList()
                partySession = QuizGameEngine.createPartySession(pack, playerNames, settings)
            } else {
                soloSession = QuizGameEngine.createSoloSession(pack, settings)
            }
        }
    }

    private fun displayQuestion() {
        pack?.let { pack ->
            val isComplete = if (isParty) {
                QuizGameEngine.isPartyComplete(partySession!!)
            } else {
                QuizGameEngine.isSoloComplete(soloSession!!)
            }

            if (isComplete) {
                finishQuiz()
                return
            }

            val (question, currentIndex, totalQuestions) = if (isParty) {
                val session = partySession!!
                Triple(
                    session.questions[session.currentQuestionIndex],
                    session.currentQuestionIndex,
                    session.questions.size
                )
            } else {
                val session = soloSession!!
                Triple(
                    session.questions[session.currentIndex],
                    session.currentIndex,
                    session.questions.size
                )
            }

            progressText.text = getString(R.string.quiz_play_question_of, currentIndex + 1, totalQuestions)

            if (isParty) {
                playerChip.visibility = Chip.VISIBLE
                playerChip.text = partySession!!.players[partySession!!.currentPlayerIndex].name
            } else {
                playerChip.visibility = Chip.GONE
            }

            questionText.text = question.prompt

            answerButtons.forEachIndexed { index, button ->
                button.text = question.options[index]
                button.isEnabled = true
                button.setTextColor(Color.parseColor("#1F2937"))
            }

            explanationCard.visibility = LinearLayout.GONE
            nextButton.visibility = MaterialButton.GONE
            answerSubmitted = false

            setupAnswerButtons()
            startTimer()
        }
    }

    private fun setupAnswerButtons() {
        answerButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (!answerSubmitted) {
                    submitAnswer(index)
                }
            }
        }
    }

    private fun submitAnswer(answerIndex: Int) {
        answerSubmitted = true
        currentTimer?.cancel()

        val correctIndex = if (isParty) {
            partySession!!.questions[partySession!!.currentQuestionIndex].answerIndex
        } else {
            soloSession!!.questions[soloSession!!.currentIndex].answerIndex
        }

        // Highlight correct answer in green
        answerButtons[correctIndex].setBackgroundColor(Color.parseColor("#4CAF50"))
        answerButtons[correctIndex].setTextColor(Color.WHITE)

        // Highlight selected answer in red if wrong
        if (answerIndex != correctIndex) {
            answerButtons[answerIndex].setBackgroundColor(Color.parseColor("#F44336"))
            answerButtons[answerIndex].setTextColor(Color.WHITE)
        }

        // Disable all buttons
        answerButtons.forEach { it.isEnabled = false }

        // Show explanation
        val explanation = if (isParty) {
            partySession!!.questions[partySession!!.currentQuestionIndex].explanation
        } else {
            soloSession!!.questions[soloSession!!.currentIndex].explanation
        }

        val explanationView = explanationCard.findViewById<TextView>(R.id.quizPlayExplanation)
        explanationView.text = explanation
        explanationCard.visibility = LinearLayout.VISIBLE

        // Show next button
        nextButton.visibility = MaterialButton.VISIBLE
        nextButton.setOnClickListener {
            advanceToNextQuestion()
        }
    }

    private fun advanceToNextQuestion() {
        if (isParty) {
            QuizGameEngine.submitPartyAnswer(partySession!!, partySession!!.currentQuestionIndex, System.currentTimeMillis())
        } else {
            QuizGameEngine.submitSoloAnswer(soloSession!!, soloSession!!.currentIndex, System.currentTimeMillis())
        }

        displayQuestion()
    }

    private fun finishQuiz() {
        val intent = Intent(this, QuizScoreboardActivity::class.java)
        if (isParty) {
            val winner = QuizGameEngine.getPartyWinner(partySession!!)
            intent.putExtra(QuizNavigation.EXTRA_IS_PARTY, true)
            intent.putStringArrayListExtra(
                QuizNavigation.EXTRA_PLAYER_NAMES,
                ArrayList(partySession!!.players.map { "${it.name}:${it.score}:${it.eliminated}" })
            )
            winner?.let {
                intent.putExtra("winner_name", it.name)
                intent.putExtra("winner_score", it.score)
            }
        } else {
            val result = QuizGameEngine.getSoloResult(soloSession!!)
            intent.putExtra(QuizNavigation.EXTRA_IS_PARTY, false)
            intent.putExtra(QuizNavigation.EXTRA_FINAL_SCORE, result.score)
            intent.putExtra(QuizNavigation.EXTRA_FINAL_CORRECT, result.correct)
            intent.putExtra(QuizNavigation.EXTRA_FINAL_TOTAL, result.total)
            intent.putExtra("accuracy", result.accuracy)
        }
        startActivity(intent)
        finish()
    }

    private fun startTimer() {
        if (!timerEnabled) return

        currentTimer = object : CountDownTimer(
            (QuizNavigation.DEFAULT_TIMER_SECONDS * 1000L),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                timerText.text = getString(R.string.quiz_play_timer_seconds, secondsRemaining)
            }

            override fun onFinish() {
                timerText.text = getString(R.string.quiz_play_timer_seconds, 0)
                if (!answerSubmitted) {
                    submitAnswer(-1)  // Time up, treat as wrong
                }
            }
        }.start()
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle(R.string.quiz_play_quit_title)
            .setMessage(R.string.quiz_play_quit_message)
            .setPositiveButton(R.string.quiz_play_quit_confirm) { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton(R.string.quiz_play_quit_cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentTimer?.cancel()
    }
}
