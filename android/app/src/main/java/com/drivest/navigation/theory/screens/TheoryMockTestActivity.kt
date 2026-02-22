package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.telemetry.TelemetryEvent
import com.drivest.navigation.telemetry.TelemetryRepository
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.models.TheoryQuestion
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class TheoryMockTestActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }
    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val telemetryRepository by lazy {
        TelemetryRepository(
            settingsRepository = SettingsRepository(applicationContext),
            consentRepository = ConsentRepository(applicationContext)
        )
    }

    private lateinit var timerView: TextView
    private lateinit var progressView: TextView
    private lateinit var promptView: TextView
    private lateinit var optionsGroup: RadioGroup
    private lateinit var optionButtons: List<RadioButton>
    private lateinit var nextButton: MaterialButton
    private lateinit var finishButton: MaterialButton

    private val timerHandler = Handler(Looper.getMainLooper())
    private val selectedAnswers = mutableMapOf<String, String>()
    private val topicAttempts = mutableMapOf<String, Int>()
    private val topicCorrect = mutableMapOf<String, Int>()
    private var questions: List<TheoryQuestion> = emptyList()
    private var currentIndex = 0
    private var currentOptionIds: List<String> = emptyList()
    private var mockFinished = false

    private var remainingMs: Long = MOCK_DURATION_MS
    private var timerStartedAtElapsedMs: Long = 0L

    private val timerTickRunnable = object : Runnable {
        override fun run() {
            if (mockFinished) return
            val elapsed = SystemClock.elapsedRealtime() - timerStartedAtElapsedMs
            val updatedRemaining = max(0L, remainingMs - elapsed)
            renderTimer(updatedRemaining)
            if (updatedRemaining <= 0L) {
                remainingMs = 0L
                finishMock()
                return
            }
            timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_mock_test)

        timerView = findViewById(R.id.theoryMockTimer)
        progressView = findViewById(R.id.theoryMockProgress)
        promptView = findViewById(R.id.theoryMockQuestionPrompt)
        optionsGroup = findViewById(R.id.theoryMockOptionsGroup)
        optionButtons = listOf(
            findViewById(R.id.theoryMockOptionA),
            findViewById(R.id.theoryMockOptionB),
            findViewById(R.id.theoryMockOptionC),
            findViewById(R.id.theoryMockOptionD)
        )
        nextButton = findViewById(R.id.theoryMockNextButton)
        finishButton = findViewById(R.id.theoryMockFinishButton)

        nextButton.setOnClickListener { submitAndMoveNext() }
        finishButton.setOnClickListener { finishMock() }

        initializeMockQuestions()
    }

    override fun onResume() {
        super.onResume()
        resumeTimer()
    }

    override fun onPause() {
        super.onPause()
        pauseTimer()
    }

    private fun initializeMockQuestions() {
        val pack = packLoader.load()
        if (pack.questions.isEmpty()) {
            Toast.makeText(this, R.string.theory_content_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        questions = pack.questions.shuffled().take(min(50, pack.questions.size))
        currentIndex = 0
        selectedAnswers.clear()
        topicAttempts.clear()
        topicCorrect.clear()
        renderQuestion()
        renderTimer(remainingMs)
    }

    private fun renderQuestion() {
        if (questions.isEmpty()) {
            promptView.text = getString(R.string.theory_quiz_no_questions)
            optionsGroup.visibility = View.GONE
            nextButton.visibility = View.GONE
            return
        }
        val question = questions[currentIndex]
        progressView.text = getString(
            R.string.theory_quiz_progress_value,
            currentIndex + 1,
            questions.size
        )
        promptView.text = question.prompt
        optionsGroup.clearCheck()

        val paddedOptions = question.options.take(4).toMutableList()
        while (paddedOptions.size < 4) {
            paddedOptions += question.options.lastOrNull() ?: break
        }
        currentOptionIds = paddedOptions.map { it.id }
        optionButtons.forEachIndexed { index, button ->
            val option = paddedOptions.getOrNull(index)
            if (option == null) {
                button.visibility = View.GONE
            } else {
                button.visibility = View.VISIBLE
                button.text = option.text
            }
        }

        val previousAnswer = selectedAnswers[question.id]
        if (previousAnswer != null) {
            val selectedIndex = currentOptionIds.indexOf(previousAnswer)
            if (selectedIndex >= 0) {
                optionsGroup.check(optionButtons[selectedIndex].id)
            }
        }
    }

    private fun submitAndMoveNext() {
        if (questions.isEmpty() || mockFinished) return
        val question = questions[currentIndex]
        val checkedId = optionsGroup.checkedRadioButtonId
        if (checkedId == View.NO_ID) {
            Toast.makeText(this, R.string.theory_select_answer_first, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedIndex = optionButtons.indexOfFirst { it.id == checkedId }
        if (selectedIndex < 0 || selectedIndex >= currentOptionIds.size) {
            Toast.makeText(this, R.string.theory_select_answer_first, Toast.LENGTH_SHORT).show()
            return
        }

        selectedAnswers[question.id] = currentOptionIds[selectedIndex]
        if (currentIndex >= questions.lastIndex) {
            finishMock()
            return
        }
        currentIndex += 1
        renderQuestion()
    }

    private fun finishMock() {
        if (mockFinished) return
        mockFinished = true
        pauseTimer()

        lifecycleScope.launch {
            var score = 0
            questions.forEach { question ->
                val selected = selectedAnswers[question.id]
                val isCorrect = selected == question.correctOptionId
                if (isCorrect) {
                    score += 1
                }
                topicAttempts[question.topicId] = (topicAttempts[question.topicId] ?: 0) + 1
                if (isCorrect) {
                    topicCorrect[question.topicId] = (topicCorrect[question.topicId] ?: 0) + 1
                }
                progressStore.recordQuizAnswer(question.topicId, question.id, isCorrect)
            }

            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "theory_mock_complete",
                    payload = mapOf(
                        "score" to score,
                        "total" to questions.size,
                        "durationSec" to ((MOCK_DURATION_MS - remainingMs) / 1000L).toInt()
                    )
                )
            )

            startActivity(
                Intent(this@TheoryMockTestActivity, TheoryMockResultsActivity::class.java).apply {
                    putExtra(TheoryNavigation.EXTRA_MOCK_SCORE, score)
                    putExtra(TheoryNavigation.EXTRA_MOCK_TOTAL, questions.size)
                    putExtra(TheoryNavigation.EXTRA_MOCK_BREAKDOWN, buildBreakdownJson().toString())
                }
            )
            finish()
        }
    }

    private fun renderTimer(timeMs: Long) {
        val totalSeconds = max(0L, timeMs / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        timerView.text = getString(R.string.theory_mock_timer_value, minutes, seconds)
    }

    private fun resumeTimer() {
        if (mockFinished || questions.isEmpty()) return
        timerStartedAtElapsedMs = SystemClock.elapsedRealtime()
        timerHandler.removeCallbacks(timerTickRunnable)
        timerHandler.post(timerTickRunnable)
    }

    private fun pauseTimer() {
        if (timerStartedAtElapsedMs > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - timerStartedAtElapsedMs
            remainingMs = max(0L, remainingMs - elapsed)
            timerStartedAtElapsedMs = 0L
        }
        timerHandler.removeCallbacks(timerTickRunnable)
    }

    private fun buildBreakdownJson(): JSONObject {
        val root = JSONObject()
        topicAttempts.forEach { (topicId, attempts) ->
            root.put(
                topicId,
                JSONObject().apply {
                    put("attempts", attempts)
                    put("correct", topicCorrect[topicId] ?: 0)
                }
            )
        }
        return root
    }

    companion object {
        private const val MOCK_DURATION_MS = 57L * 60L * 1000L
    }
}
