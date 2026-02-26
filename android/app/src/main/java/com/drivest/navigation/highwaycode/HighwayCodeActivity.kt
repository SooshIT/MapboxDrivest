package com.drivest.navigation.highwaycode

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class HighwayCodeActivity : AppCompatActivity() {

    private val repository by lazy { HighwayCodeRepository(applicationContext) }
    private val random = Random(System.currentTimeMillis())

    private lateinit var sourceButton: MaterialButton
    private lateinit var questionsChip: TextView
    private lateinit var categoriesChip: TextView
    private lateinit var sessionChip: TextView
    private lateinit var modeStatsChip: TextView
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var categorySummary: TextView

    private lateinit var modeChallenge: MaterialButton
    private lateinit var modeSprint: MaterialButton
    private lateinit var modeReview: MaterialButton
    private lateinit var modeDescription: TextView

    private lateinit var questionPanel: LinearLayout
    private lateinit var questionIndex: TextView
    private lateinit var questionCategoryChip: TextView
    private lateinit var difficultyChip: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var promptText: TextView
    private lateinit var questionText: TextView
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var feedbackText: TextView
    private lateinit var explanationText: TextView
    private lateinit var sourceHintText: TextView
    private lateinit var scoreLine: TextView
    private lateinit var secondaryActionButton: MaterialButton
    private lateinit var primaryActionButton: MaterialButton
    private lateinit var emptyText: TextView

    private var pack: HighwayCodePack? = null
    private var allQuestions: List<HighwayCodeQuestion> = emptyList()
    private var categoryNameMap: Map<String, String> = emptyMap()
    private var selectedCategoryId: String? = null
    private var currentMode: QuizMode = QuizMode.CHALLENGE

    private var sessionQueue: MutableList<HighwayCodeQuestion> = mutableListOf()
    private var currentIndex: Int = 0
    private var answered: Boolean = false
    private var revealedOnly: Boolean = false
    private var correctAnswers: Int = 0
    private var questionsAttempted: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_highway_code)
        bindViews()
        setupClicks()
        updateModeStyle(QuizMode.CHALLENGE)
        loadPack()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.highwayCodeBackButton).setOnClickListener { finish() }
        sourceButton = findViewById(R.id.highwayCodeSourceButton)
        questionsChip = findViewById(R.id.highwayCodeQuestionsChip)
        categoriesChip = findViewById(R.id.highwayCodeCategoriesChip)
        sessionChip = findViewById(R.id.highwayCodeSessionChip)
        modeStatsChip = findViewById(R.id.highwayCodeModeStatsChip)
        categoryChipGroup = findViewById(R.id.highwayCodeCategoryChipGroup)
        categorySummary = findViewById(R.id.highwayCodeCategorySummary)

        modeChallenge = findViewById(R.id.highwayCodeModeChallenge)
        modeSprint = findViewById(R.id.highwayCodeModeSprint)
        modeReview = findViewById(R.id.highwayCodeModeReview)
        modeDescription = findViewById(R.id.highwayCodeModeDescription)

        questionPanel = findViewById(R.id.highwayCodeQuestionPanel)
        questionIndex = findViewById(R.id.highwayCodeQuestionIndex)
        questionCategoryChip = findViewById(R.id.highwayCodeQuestionCategoryChip)
        difficultyChip = findViewById(R.id.highwayCodeDifficultyChip)
        progressBar = findViewById(R.id.highwayCodeProgressBar)
        promptText = findViewById(R.id.highwayCodePrompt)
        questionText = findViewById(R.id.highwayCodeQuestionText)
        optionButtons = listOf(
            findViewById(R.id.highwayCodeOption1),
            findViewById(R.id.highwayCodeOption2),
            findViewById(R.id.highwayCodeOption3),
            findViewById(R.id.highwayCodeOption4)
        )
        feedbackText = findViewById(R.id.highwayCodeFeedback)
        explanationText = findViewById(R.id.highwayCodeExplanation)
        sourceHintText = findViewById(R.id.highwayCodeSourceHint)
        scoreLine = findViewById(R.id.highwayCodeScoreLine)
        secondaryActionButton = findViewById(R.id.highwayCodeSecondaryActionButton)
        primaryActionButton = findViewById(R.id.highwayCodePrimaryActionButton)
        emptyText = findViewById(R.id.highwayCodeEmptyText)
    }

    private fun setupClicks() {
        sourceButton.setOnClickListener { openOfficialSource() }

        modeChallenge.setOnClickListener { switchMode(QuizMode.CHALLENGE) }
        modeSprint.setOnClickListener { switchMode(QuizMode.SPRINT) }
        modeReview.setOnClickListener { switchMode(QuizMode.REVIEW) }

        optionButtons.forEachIndexed { index, button ->
            button.setOnClickListener { handleOptionSelected(index) }
        }
        secondaryActionButton.setOnClickListener { handleReveal() }
        primaryActionButton.setOnClickListener { handleNext() }
    }

    private fun loadPack() {
        lifecycleScope.launch {
            runCatching { repository.loadPack() }
                .onSuccess { loadedPack ->
                    pack = loadedPack
                    allQuestions = loadedPack.questions
                    categoryNameMap = loadedPack.categories.associate { it.id to it.name }
                    setupCategoryChips(loadedPack)
                    updateHeaderStats(loadedPack)
                    rebuildQueue()
                    renderCurrentQuestion()
                }
                .onFailure {
                    Toast.makeText(
                        this@HighwayCodeActivity,
                        getString(R.string.highway_code_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun setupCategoryChips(loadedPack: HighwayCodePack) {
        categoryChipGroup.removeAllViews()
        categoryChipGroup.setOnCheckedStateChangeListener(null)

        val allChip = buildCategoryChip(
            chipId = View.generateViewId(),
            label = getString(R.string.highway_code_category_all),
            categoryId = null
        )
        categoryChipGroup.addView(allChip)

        loadedPack.categories.forEach { category ->
            categoryChipGroup.addView(
                buildCategoryChip(
                    chipId = View.generateViewId(),
                    label = category.name,
                    categoryId = category.id
                )
            )
        }

        categoryChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedStateChangeListener
            selectedCategoryId = chip.tag as? String
            rebuildQueue()
            renderCurrentQuestion()
        }

        allChip.isChecked = true
    }

    private fun buildCategoryChip(chipId: Int, label: String, categoryId: String?): Chip {
        return Chip(this).apply {
            id = chipId
            text = label
            tag = categoryId
            isCheckable = true
            isClickable = true
            chipMinHeight = (34f * resources.displayMetrics.density).roundToInt().toFloat()
            setTextColor(ContextCompat.getColorStateList(context, R.color.settings_text_primary))
        }
    }

    private fun updateHeaderStats(loadedPack: HighwayCodePack) {
        questionsChip.text = getString(R.string.highway_code_chip_questions_value, loadedPack.questions.size)
        categoriesChip.text = getString(R.string.highway_code_chip_categories_value, loadedPack.categories.size)
        updateSessionChips()
    }

    private fun updateSessionChips() {
        sessionChip.text = getString(R.string.highway_code_chip_session_value, correctAnswers, questionsAttempted)
        modeStatsChip.text = getString(currentMode.labelResId)
        updateCategorySummary()
    }

    private fun updateCategorySummary() {
        val scopedCount = scopedQuestions().size
        val catLabel = selectedCategoryId?.let { categoryNameMap[it] }
            ?: getString(R.string.highway_code_category_all)
        categorySummary.text = if (selectedCategoryId == null) {
            getString(R.string.highway_code_scope_summary_default)
        } else {
            getString(R.string.highway_code_scope_summary_value, catLabel, scopedCount)
        }
    }

    private fun scopedQuestions(): List<HighwayCodeQuestion> {
        val catId = selectedCategoryId
        if (catId.isNullOrBlank()) return allQuestions
        return allQuestions.filter { it.categoryId == catId }
    }

    private fun rebuildQueue() {
        val scoped = scopedQuestions()
        sessionQueue = scoped.shuffled(random).toMutableList()
        currentIndex = 0
        answered = false
        revealedOnly = false
        updateCategorySummary()
    }

    private fun renderCurrentQuestion() {
        val questions = scopedQuestions()
        if (questions.isEmpty()) {
            questionPanel.isVisible = false
            emptyText.isVisible = true
            return
        }

        questionPanel.isVisible = true
        emptyText.isVisible = false

        if (sessionQueue.isEmpty()) {
            sessionQueue = questions.shuffled(random).toMutableList()
            currentIndex = 0
        }

        val q = sessionQueue.getOrNull(currentIndex) ?: run {
            sessionQueue = questions.shuffled(random).toMutableList()
            currentIndex = 0
            sessionQueue.firstOrNull() ?: return
        }

        answered = false
        revealedOnly = false

        val total = sessionQueue.size
        questionIndex.text = getString(R.string.highway_code_question_index_value, currentIndex + 1, total)
        progressBar.progress = if (total > 0) ((currentIndex + 1) * 100 / total) else 0

        val catName = categoryNameMap[q.categoryId] ?: q.categoryId
        questionCategoryChip.text = catName
        difficultyChip.text = getString(
            if (q.difficulty == "medium") R.string.highway_code_difficulty_medium
            else R.string.highway_code_difficulty_easy
        )

        promptText.text = q.prompt
        promptText.isVisible = q.prompt.isNotBlank()
        questionText.text = q.question

        val textPrimary = ContextCompat.getColor(this, R.color.settings_text_primary)
        optionButtons.forEachIndexed { index, button ->
            val optionText = q.options.getOrNull(index).orEmpty()
            button.text = optionText
            button.isEnabled = optionText.isNotBlank()
            button.isVisible = optionText.isNotBlank()
            button.alpha = 1f
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(textPrimary)
        }

        feedbackText.isVisible = false
        explanationText.isVisible = false
        sourceHintText.isVisible = false
        scoreLine.text = getString(R.string.highway_code_score_value, correctAnswers, questionsAttempted)

        secondaryActionButton.isEnabled = true
        secondaryActionButton.text = getString(R.string.highway_code_action_reveal)
        primaryActionButton.text = getString(R.string.highway_code_action_next)

        updateSessionChips()
    }

    private fun handleOptionSelected(selectedIndex: Int) {
        val q = sessionQueue.getOrNull(currentIndex) ?: return
        if (answered) return

        answered = true
        questionsAttempted++
        val isCorrect = selectedIndex == q.answerIndex
        if (isCorrect) correctAnswers++

        val correctColor = ContextCompat.getColor(this, R.color.theory_feedback_correct)
        val incorrectColor = ContextCompat.getColor(this, R.color.theory_feedback_incorrect)
        val white = Color.WHITE

        optionButtons.forEachIndexed { index, button ->
            button.isEnabled = false
            when {
                index == q.answerIndex -> {
                    button.backgroundTintList = ColorStateList.valueOf(correctColor)
                    button.setTextColor(white)
                    button.alpha = 1f
                }
                index == selectedIndex && !isCorrect -> {
                    button.backgroundTintList = ColorStateList.valueOf(incorrectColor)
                    button.setTextColor(white)
                    button.alpha = 0.9f
                }
                else -> {
                    button.alpha = 0.45f
                }
            }
        }

        if (isCorrect) {
            feedbackText.text = getString(R.string.highway_code_feedback_correct)
            feedbackText.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_correct))
        } else {
            feedbackText.text = getString(R.string.highway_code_feedback_incorrect)
            feedbackText.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_incorrect))
        }
        feedbackText.isVisible = true

        val showDetails = currentMode != QuizMode.SPRINT
        if (showDetails && q.explanation.isNotBlank()) {
            explanationText.text = q.explanation
            explanationText.isVisible = true
        }
        if (showDetails && q.sourceHint.isNotBlank()) {
            sourceHintText.text = getString(R.string.highway_code_source_hint_value, q.sourceHint)
            sourceHintText.isVisible = true
        }

        scoreLine.text = getString(R.string.highway_code_score_value, correctAnswers, questionsAttempted)
        secondaryActionButton.isEnabled = false
        updateSessionChips()
    }

    private fun handleReveal() {
        val q = sessionQueue.getOrNull(currentIndex) ?: return
        if (answered) return

        revealedOnly = true
        answered = true

        val correctColor = ContextCompat.getColor(this, R.color.theory_feedback_correct)
        val white = Color.WHITE

        optionButtons.forEachIndexed { index, button ->
            button.isEnabled = false
            if (index == q.answerIndex) {
                button.backgroundTintList = ColorStateList.valueOf(correctColor)
                button.setTextColor(white)
                button.alpha = 1f
            } else {
                button.alpha = 0.45f
            }
        }

        feedbackText.text = getString(R.string.highway_code_feedback_revealed)
        feedbackText.setTextColor(ContextCompat.getColor(this, R.color.settings_text_secondary))
        feedbackText.isVisible = true

        if (q.explanation.isNotBlank()) {
            explanationText.text = q.explanation
            explanationText.isVisible = true
        }
        if (q.sourceHint.isNotBlank()) {
            sourceHintText.text = getString(R.string.highway_code_source_hint_value, q.sourceHint)
            sourceHintText.isVisible = true
        }

        secondaryActionButton.isEnabled = false
    }

    private fun handleNext() {
        currentIndex++
        if (currentIndex >= sessionQueue.size) {
            sessionQueue = scopedQuestions().shuffled(random).toMutableList()
            currentIndex = 0
        }
        renderCurrentQuestion()
    }

    private fun switchMode(mode: QuizMode) {
        currentMode = mode
        updateModeStyle(mode)
        rebuildQueue()
        renderCurrentQuestion()
    }

    private fun updateModeStyle(mode: QuizMode) {
        modeDescription.text = getString(mode.descriptionResId)
        styleModeButton(modeChallenge, mode == QuizMode.CHALLENGE)
        styleModeButton(modeSprint, mode == QuizMode.SPRINT)
        styleModeButton(modeReview, mode == QuizMode.REVIEW)
        modeStatsChip.text = getString(mode.labelResId)
    }

    private fun styleModeButton(button: MaterialButton, selected: Boolean) {
        val accent = ContextCompat.getColor(this, R.color.settings_accent)
        val textPrimary = ContextCompat.getColor(this, R.color.settings_text_primary)
        val strokeColor = ContextCompat.getColor(this, R.color.app_card_stroke)
        button.backgroundTintList = ColorStateList.valueOf(if (selected) accent else Color.WHITE)
        button.strokeColor = ColorStateList.valueOf(if (selected) accent else strokeColor)
        button.strokeWidth = (if (selected) 0 else 1) * resources.displayMetrics.density.roundToInt()
        button.setTextColor(if (selected) Color.WHITE else textPrimary)
    }

    private fun openOfficialSource() {
        val url = pack?.sourceReferences?.firstOrNull() ?: OFFICIAL_SOURCE_URL
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.highway_code_source_open_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private enum class QuizMode(val labelResId: Int, val descriptionResId: Int) {
        CHALLENGE(
            R.string.highway_code_mode_challenge,
            R.string.highway_code_mode_description_challenge
        ),
        SPRINT(
            R.string.highway_code_mode_sprint,
            R.string.highway_code_mode_description_sprint
        ),
        REVIEW(
            R.string.highway_code_mode_review,
            R.string.highway_code_mode_description_review
        )
    }

    private companion object {
        const val OFFICIAL_SOURCE_URL = "https://www.gov.uk/guidance/the-highway-code"
    }
}
