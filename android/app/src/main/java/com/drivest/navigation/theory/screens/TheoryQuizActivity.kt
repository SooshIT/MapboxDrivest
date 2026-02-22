package com.drivest.navigation.theory.screens

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import com.drivest.navigation.theory.quiz.TheoryNextAction
import com.drivest.navigation.theory.quiz.TheoryQuizFlow
import com.drivest.navigation.theory.quiz.TheoryQuizQuestionSelector
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

class TheoryQuizActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }
    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val telemetryRepository by lazy {
        TelemetryRepository(
            settingsRepository = SettingsRepository(applicationContext),
            consentRepository = ConsentRepository(applicationContext)
        )
    }

    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var promptView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var summaryView: TextView
    private lateinit var optionsGroup: RadioGroup
    private lateinit var optionButtons: List<RadioButton>
    private lateinit var bookmarkButton: MaterialButton
    private lateinit var submitButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private var questions: List<TheoryQuestion> = emptyList()
    private var currentIndex: Int = 0
    private var score: Int = 0
    private var answerSubmitted: Boolean = false
    private var currentOptionIds: List<String> = emptyList()
    private var quizMode: String = TheoryNavigation.QUIZ_MODE_TOPIC
    private var quizTopicId: String? = null
    private val topicAttemptBreakdown = mutableMapOf<String, Int>()
    private val topicCorrectBreakdown = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_quiz)

        titleView = findViewById(R.id.theoryQuizTitle)
        progressView = findViewById(R.id.theoryQuizProgress)
        promptView = findViewById(R.id.theoryQuizQuestionPrompt)
        feedbackView = findViewById(R.id.theoryQuizFeedback)
        summaryView = findViewById(R.id.theoryQuizSummary)
        optionsGroup = findViewById(R.id.theoryQuizOptionsGroup)
        optionButtons = listOf(
            findViewById(R.id.theoryQuizOptionA),
            findViewById(R.id.theoryQuizOptionB),
            findViewById(R.id.theoryQuizOptionC),
            findViewById(R.id.theoryQuizOptionD)
        )
        bookmarkButton = findViewById(R.id.theoryQuizBookmarkButton)
        submitButton = findViewById(R.id.theoryQuizSubmitButton)
        nextButton = findViewById(R.id.theoryQuizNextButton)

        submitButton.setOnClickListener { submitCurrentAnswer() }
        nextButton.setOnClickListener { moveNext() }
        bookmarkButton.setOnClickListener { toggleBookmarkForCurrentQuestion() }

        lifecycleScope.launch {
            initializeQuiz()
        }
    }

    private suspend fun initializeQuiz() {
        val pack = packLoader.load()
        if (pack.questions.isEmpty()) {
            Toast.makeText(this, R.string.theory_content_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val progress = progressStore.progress.first()
        quizMode = intent.getStringExtra(TheoryNavigation.EXTRA_QUIZ_MODE)
            ?: TheoryNavigation.QUIZ_MODE_TOPIC
        quizTopicId = intent.getStringExtra(TheoryNavigation.EXTRA_QUIZ_TOPIC_ID)

        val requestedCount = intent.getIntExtra(TheoryNavigation.EXTRA_QUIZ_COUNT, 10).coerceAtLeast(1)
        questions = TheoryQuizQuestionSelector.selectQuestions(
            allQuestions = pack.questions,
            bookmarks = progress.bookmarks,
            wrongQueue = progress.wrongQueue,
            quizMode = quizMode,
            quizTopicId = quizTopicId,
            requestedCount = requestedCount
        )
        if (questions.isEmpty()) {
            showNoQuestionsState()
            return
        }

        currentIndex = 0
        score = 0
        answerSubmitted = false
        renderQuestion()
    }

    private fun showNoQuestionsState() {
        titleView.text = getString(R.string.theory_quiz_title)
        progressView.text = ""
        promptView.text = getString(R.string.theory_quiz_no_questions)
        optionsGroup.isVisible = false
        bookmarkButton.isVisible = false
        submitButton.isVisible = false
        nextButton.isVisible = false
        summaryView.isVisible = true
        summaryView.text = getString(R.string.theory_quiz_no_questions)
    }

    private fun renderQuestion() {
        if (questions.isEmpty()) {
            showNoQuestionsState()
            return
        }
        val question = questions[currentIndex]
        titleView.text = when (quizMode) {
            TheoryNavigation.QUIZ_MODE_BOOKMARKS -> getString(R.string.theory_bookmarks)
            TheoryNavigation.QUIZ_MODE_WRONG -> getString(R.string.theory_wrong_answers)
            else -> getString(R.string.theory_quiz_title)
        }
        progressView.text = getString(
            R.string.theory_quiz_progress_value,
            currentIndex + 1,
            questions.size
        )
        promptView.text = question.prompt
        feedbackView.text = ""
        feedbackView.setTextColor(ContextCompat.getColor(this, R.color.theory_text_secondary))
        summaryView.isVisible = false
        optionsGroup.clearCheck()
        answerSubmitted = false
        submitButton.isVisible = true
        submitButton.isEnabled = true
        nextButton.isVisible = false

        val paddedOptions = question.options.take(4).toMutableList()
        while (paddedOptions.size < 4) {
            paddedOptions += question.options.lastOrNull() ?: break
        }
        currentOptionIds = paddedOptions.map { it.id }
        optionButtons.forEachIndexed { index, button ->
            val option = paddedOptions.getOrNull(index)
            if (option == null) {
                button.isVisible = false
            } else {
                button.isVisible = true
                button.text = option.text
            }
        }

        lifecycleScope.launch {
            val bookmarked = progressStore.progress.first().bookmarks.contains(question.id)
            bookmarkButton.text = if (bookmarked) {
                getString(R.string.theory_bookmarked_question)
            } else {
                getString(R.string.theory_bookmark_question)
            }
        }
    }

    private fun submitCurrentAnswer() {
        if (answerSubmitted || questions.isEmpty()) return
        val checkedId = optionsGroup.checkedRadioButtonId
        if (checkedId == View.NO_ID) {
            Toast.makeText(this, R.string.theory_select_answer_first, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedIndex = optionButtons.indexOfFirst { button -> button.id == checkedId }
        if (selectedIndex < 0 || selectedIndex >= currentOptionIds.size) {
            Toast.makeText(this, R.string.theory_select_answer_first, Toast.LENGTH_SHORT).show()
            return
        }

        val question = questions[currentIndex]
        val selectedOptionId = currentOptionIds[selectedIndex]
        val isCorrect = selectedOptionId == question.correctOptionId
        if (isCorrect) {
            score += 1
        }
        topicAttemptBreakdown[question.topicId] = (topicAttemptBreakdown[question.topicId] ?: 0) + 1
        if (isCorrect) {
            topicCorrectBreakdown[question.topicId] = (topicCorrectBreakdown[question.topicId] ?: 0) + 1
        }

        lifecycleScope.launch {
            progressStore.recordQuizAnswer(question.topicId, question.id, isCorrect)
        }

        feedbackView.text = if (isCorrect) {
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_correct))
            getString(R.string.theory_feedback_correct, question.explanation)
        } else {
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_incorrect))
            getString(R.string.theory_feedback_incorrect, question.explanation)
        }
        answerSubmitted = true
        submitButton.isVisible = false
        nextButton.isVisible = true
        nextButton.text = if (currentIndex == questions.lastIndex) {
            getString(R.string.theory_finish_quiz)
        } else {
            getString(R.string.theory_next_question)
        }
    }

    private fun moveNext() {
        if (questions.isEmpty()) return
        val nextAction = TheoryQuizFlow.decideNextAction(
            answerSubmitted = answerSubmitted,
            hasSelectedAnswer = optionsGroup.checkedRadioButtonId != View.NO_ID,
            isLastQuestion = currentIndex >= questions.lastIndex
        )
        when (nextAction) {
            TheoryNextAction.BLOCKED_NO_SELECTION -> {
                Toast.makeText(this, R.string.theory_select_answer_first, Toast.LENGTH_SHORT).show()
            }
            TheoryNextAction.SUBMIT_THEN_ADVANCE -> {
                submitCurrentAnswer()
                if (answerSubmitted) {
                    if (currentIndex >= questions.lastIndex) {
                        finishQuiz()
                    } else {
                        currentIndex += 1
                        renderQuestion()
                    }
                }
            }
            TheoryNextAction.ADVANCE -> {
                currentIndex += 1
                renderQuestion()
            }
            TheoryNextAction.FINISH -> {
                finishQuiz()
            }
        }
    }

    private fun toggleBookmarkForCurrentQuestion() {
        val question = questions.getOrNull(currentIndex) ?: return
        lifecycleScope.launch {
            val bookmarked = progressStore.toggleBookmark(question.id)
            bookmarkButton.text = if (bookmarked) {
                getString(R.string.theory_bookmarked_question)
            } else {
                getString(R.string.theory_bookmark_question)
            }
        }
    }

    private fun finishQuiz() {
        optionsGroup.isVisible = false
        bookmarkButton.isVisible = false
        submitButton.isVisible = false
        nextButton.isVisible = false
        feedbackView.text = ""
        summaryView.isVisible = true
        summaryView.text = getString(
            R.string.theory_quiz_summary_value,
            score,
            questions.size
        )

        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "theory_quiz_complete",
                    payload = mapOf(
                        "score" to score,
                        "total" to questions.size,
                        "topicId" to quizTopicId.orEmpty(),
                        "mode" to quizMode,
                        "breakdown" to buildBreakdownJson().toString()
                    )
                )
            )
        }
    }

    private fun buildBreakdownJson(): JSONObject {
        val root = JSONObject()
        topicAttemptBreakdown.forEach { (topicId, attempts) ->
            root.put(
                topicId,
                JSONObject().apply {
                    put("attempts", attempts)
                    put("correct", topicCorrectBreakdown[topicId] ?: 0)
                }
            )
        }
        return root
    }
}
