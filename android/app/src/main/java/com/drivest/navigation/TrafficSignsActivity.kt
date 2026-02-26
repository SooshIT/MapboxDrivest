package com.drivest.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.trafficsigns.TrafficSign
import com.drivest.navigation.trafficsigns.TrafficSignsBitmapLoader
import com.drivest.navigation.trafficsigns.TrafficSignsGridAdapter
import com.drivest.navigation.trafficsigns.TrafficSignsPack
import com.drivest.navigation.trafficsigns.TrafficSignsQuizQuestion
import com.drivest.navigation.trafficsigns.TrafficSignsRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class TrafficSignsActivity : AppCompatActivity() {

    private val repository by lazy { TrafficSignsRepository(applicationContext) }
    private val bitmapLoader by lazy { TrafficSignsBitmapLoader(applicationContext) }
    private val random = Random(System.currentTimeMillis())

    private lateinit var sourceButton: MaterialButton
    private lateinit var countChip: TextView
    private lateinit var categoryChip: TextView
    private lateinit var sessionScoreChip: TextView
    private lateinit var filterSummary: TextView
    private lateinit var categoryFilterGroup: ChipGroup

    private lateinit var modeFlashcardsButton: MaterialButton
    private lateinit var modeQuizButton: MaterialButton
    private lateinit var modeBrowseButton: MaterialButton

    private lateinit var flashcardsPanel: LinearLayout
    private lateinit var flashcardImage: ImageView
    private lateinit var flashcardCaption: TextView
    private lateinit var flashcardMeta: TextView
    private lateinit var flashcardMeaning: TextView
    private lateinit var flashcardRevealButton: MaterialButton
    private lateinit var flashcardKnownButton: MaterialButton
    private lateinit var flashcardNextButton: MaterialButton

    private lateinit var quizPanel: LinearLayout
    private lateinit var quizPrompt: TextView
    private lateinit var quizImage: ImageView
    private lateinit var quizOptionButtons: List<MaterialButton>
    private lateinit var quizFeedback: TextView
    private lateinit var quizNextButton: MaterialButton

    private lateinit var browsePanel: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var browseResultCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadMoreButton: MaterialButton
    private lateinit var gridAdapter: TrafficSignsGridAdapter

    private var uiMode: UiMode = UiMode.FLASHCARDS
    private var pack: TrafficSignsPack? = null
    private var allSigns: List<TrafficSign> = emptyList()
    private var categoryNameMap: Map<String, String> = emptyMap()
    private var selectedCategoryId: String? = null
    private var browseSearchQuery: String = ""
    private var filteredBrowseSigns: List<TrafficSign> = emptyList()
    private var browseVisibleCount = BROWSE_PAGE_SIZE

    private var currentFlashcardSign: TrafficSign? = null
    private var flashcardMeaningRevealed = false
    private val knownSignsSession = linkedSetOf<String>()

    private var currentQuizQuestion: TrafficSignsQuizQuestion? = null
    private var quizAnswered = false
    private var quizCorrectAnswers = 0
    private var quizQuestionsAnswered = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traffic_signs)

        bindViews()
        setupList()
        setupClicks()
        updateMode(UiMode.FLASHCARDS)
        loadPack()
    }

    private fun bindViews() {
        findViewById<ImageButton>(R.id.trafficSignsBackButton).setOnClickListener { finish() }
        sourceButton = findViewById(R.id.trafficSignsSourceButton)
        countChip = findViewById(R.id.trafficSignsCountChip)
        categoryChip = findViewById(R.id.trafficSignsCategoryChip)
        sessionScoreChip = findViewById(R.id.trafficSignsSessionScoreChip)
        filterSummary = findViewById(R.id.trafficSignsFilterSummary)
        categoryFilterGroup = findViewById(R.id.trafficSignsCategoryFilterGroup)

        modeFlashcardsButton = findViewById(R.id.trafficSignsModeFlashcards)
        modeQuizButton = findViewById(R.id.trafficSignsModeQuiz)
        modeBrowseButton = findViewById(R.id.trafficSignsModeBrowse)

        flashcardsPanel = findViewById(R.id.trafficSignsFlashcardsPanel)
        flashcardImage = findViewById(R.id.trafficSignsFlashcardImage)
        flashcardCaption = findViewById(R.id.trafficSignsFlashcardCaption)
        flashcardMeta = findViewById(R.id.trafficSignsFlashcardMeta)
        flashcardMeaning = findViewById(R.id.trafficSignsFlashcardMeaning)
        flashcardRevealButton = findViewById(R.id.trafficSignsFlashcardRevealButton)
        flashcardKnownButton = findViewById(R.id.trafficSignsFlashcardKnownButton)
        flashcardNextButton = findViewById(R.id.trafficSignsFlashcardNextButton)

        quizPanel = findViewById(R.id.trafficSignsQuizPanel)
        quizPrompt = findViewById(R.id.trafficSignsQuizPrompt)
        quizImage = findViewById(R.id.trafficSignsQuizImage)
        quizFeedback = findViewById(R.id.trafficSignsQuizFeedback)
        quizNextButton = findViewById(R.id.trafficSignsQuizNextButton)
        quizOptionButtons = listOf(
            findViewById(R.id.trafficSignsQuizOption1),
            findViewById(R.id.trafficSignsQuizOption2),
            findViewById(R.id.trafficSignsQuizOption3),
            findViewById(R.id.trafficSignsQuizOption4)
        )

        browsePanel = findViewById(R.id.trafficSignsBrowsePanel)
        searchInput = findViewById(R.id.trafficSignsSearchInput)
        browseResultCount = findViewById(R.id.trafficSignsBrowseResultCount)
        recyclerView = findViewById(R.id.trafficSignsRecyclerView)
        loadMoreButton = findViewById(R.id.trafficSignsLoadMoreButton)
    }

    private fun setupList() {
        gridAdapter = TrafficSignsGridAdapter(
            scope = lifecycleScope,
            imageLoader = bitmapLoader,
            categoryNamesById = { categoryNameMap },
            onSignClick = { sign -> showSignDetail(sign) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = gridAdapter
        recyclerView.isNestedScrollingEnabled = true
    }

    private fun setupClicks() {
        sourceButton.setOnClickListener { openOfficialSource() }

        modeFlashcardsButton.setOnClickListener { updateMode(UiMode.FLASHCARDS) }
        modeQuizButton.setOnClickListener { updateMode(UiMode.QUIZ) }
        modeBrowseButton.setOnClickListener { updateMode(UiMode.BROWSE) }

        flashcardRevealButton.setOnClickListener {
            flashcardMeaningRevealed = !flashcardMeaningRevealed
            renderFlashcard()
        }
        flashcardKnownButton.setOnClickListener {
            currentFlashcardSign?.let { sign ->
                knownSignsSession.add(sign.id)
                updateSessionChips()
                Toast.makeText(
                    this,
                    getString(R.string.traffic_signs_marked_known_toast),
                    Toast.LENGTH_SHORT
                ).show()
                showNextFlashcard()
            }
        }
        flashcardNextButton.setOnClickListener { showNextFlashcard() }

        quizOptionButtons.forEach { button ->
            button.setOnClickListener { handleQuizAnswer(button.text.toString()) }
        }
        quizNextButton.setOnClickListener { generateQuizQuestion() }

        searchInput.doAfterTextChanged { editable ->
            browseSearchQuery = editable?.toString().orEmpty()
            browseVisibleCount = BROWSE_PAGE_SIZE
            applyBrowseFilter()
        }
        loadMoreButton.setOnClickListener {
            browseVisibleCount += BROWSE_PAGE_SIZE
            renderBrowseResults()
        }
    }

    private fun loadPack() {
        lifecycleScope.launch {
            runCatching { repository.loadPack() }
                .onSuccess { loadedPack ->
                    pack = loadedPack
                    allSigns = loadedPack.signs.sortedBy { it.caption.lowercase() }
                    categoryNameMap = loadedPack.categories.associate { it.id to it.name }
                    setupCategoryChips(loadedPack)
                    updateHeaderStats(loadedPack)
                    showNextFlashcard(forceRandom = true)
                    generateQuizQuestion()
                    applyBrowseFilter()
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@TrafficSignsActivity,
                        getString(R.string.traffic_signs_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    filterSummary.text = error.message ?: getString(R.string.traffic_signs_load_failed)
                }
        }
    }

    private fun setupCategoryChips(loadedPack: TrafficSignsPack) {
        categoryFilterGroup.removeAllViews()
        categoryFilterGroup.setOnCheckedStateChangeListener(null)

        val allChip = buildCategoryChip(
            chipId = View.generateViewId(),
            label = getString(R.string.traffic_signs_category_all),
            categoryId = null
        )
        categoryFilterGroup.addView(allChip)

        loadedPack.categories.forEach { category ->
            categoryFilterGroup.addView(
                buildCategoryChip(
                    chipId = View.generateViewId(),
                    label = category.name,
                    categoryId = category.id
                )
            )
        }

        categoryFilterGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val checkedChip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedStateChangeListener
            selectedCategoryId = checkedChip.tag as? String
            browseVisibleCount = BROWSE_PAGE_SIZE
            applyBrowseFilter()
            showNextFlashcard()
            generateQuizQuestion()
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

    private fun updateHeaderStats(loadedPack: TrafficSignsPack) {
        countChip.text = getString(R.string.traffic_signs_count_value, loadedPack.signs.size)
        categoryChip.text = getString(R.string.traffic_signs_categories_value, loadedPack.categories.size)
        updateSessionChips()
    }

    private fun updateSessionChips() {
        sessionScoreChip.text = getString(
            R.string.traffic_signs_quiz_score_value,
            quizCorrectAnswers,
            quizQuestionsAnswered,
            knownSignsSession.size
        )
    }

    private fun scopedSigns(): List<TrafficSign> {
        val categoryId = selectedCategoryId
        if (categoryId.isNullOrBlank()) return allSigns
        return allSigns.filter { sign ->
            sign.primaryCategoryId == categoryId || sign.categoryIds.contains(categoryId)
        }
    }

    private fun showNextFlashcard(forceRandom: Boolean = false) {
        val candidates = scopedSigns().ifEmpty { allSigns }
        if (candidates.isEmpty()) return
        val next = if (forceRandom || currentFlashcardSign == null) {
            candidates.random(random)
        } else {
            val currentIndex = candidates.indexOfFirst { it.id == currentFlashcardSign?.id }
            if (currentIndex == -1) candidates.random(random) else candidates[(currentIndex + 1) % candidates.size]
        }
        currentFlashcardSign = next
        flashcardMeaningRevealed = false
        renderFlashcard()
    }

    private fun renderFlashcard() {
        val sign = currentFlashcardSign ?: return
        flashcardCaption.text = sign.caption.ifBlank { getString(R.string.traffic_signs_unknown_caption) }
        val categoryName = categoryNameMap[sign.primaryCategoryId] ?: sign.officialCategory
        val metaParts = mutableListOf<String>()
        if (sign.code.isNotBlank()) {
            metaParts.add(getString(R.string.traffic_signs_sign_code_value, sign.code))
        }
        if (categoryName.isNotBlank()) {
            metaParts.add(categoryName)
        }
        if (sign.shape.isNotBlank()) {
            metaParts.add(sign.shape)
        }
        flashcardMeta.text = metaParts.joinToString(" • ")
        flashcardMeaning.text = sign.description.ifBlank { sign.caption }
        flashcardMeaning.isVisible = flashcardMeaningRevealed
        flashcardRevealButton.text = getString(
            if (flashcardMeaningRevealed) R.string.traffic_signs_hide_meaning else R.string.traffic_signs_reveal_meaning
        )
        loadImage(flashcardImage, sign.imageAssetPath, 220f, 220f)
    }

    private fun generateQuizQuestion() {
        val pool = scopedSigns().ifEmpty { allSigns }
        if (pool.size < 4) {
            currentQuizQuestion = null
            quizPrompt.text = getString(R.string.traffic_signs_quiz_not_enough_signs)
            quizFeedback.isVisible = false
            quizOptionButtons.forEach {
                it.text = ""
                it.isEnabled = false
            }
            quizNextButton.isEnabled = false
            return
        }

        val sign = pool.random(random)
        val correct = sign.caption.ifBlank { sign.description }.ifBlank { getString(R.string.traffic_signs_unknown_caption) }
        val categoryDistractors = pool
            .asSequence()
            .filter { it.id != sign.id }
            .map { it.caption.ifBlank { it.description } }
            .filter { it.isNotBlank() && it != correct }
            .distinct()
            .toList()

        val globalDistractors = allSigns
            .asSequence()
            .filter { it.id != sign.id }
            .map { it.caption.ifBlank { it.description } }
            .filter { it.isNotBlank() && it != correct }
            .distinct()
            .shuffled(random)

        val optionSet = linkedSetOf(correct)
        categoryDistractors.shuffled(random).forEach {
            if (optionSet.size < 4) optionSet.add(it)
        }
        globalDistractors.forEach {
            if (optionSet.size < 4) optionSet.add(it)
        }
        if (optionSet.size < 4) {
            quizPrompt.text = getString(R.string.traffic_signs_quiz_not_enough_signs)
            return
        }

        currentQuizQuestion = TrafficSignsQuizQuestion(
            sign = sign,
            options = optionSet.toList().shuffled(random),
            correctAnswer = correct
        )
        quizAnswered = false
        quizFeedback.isVisible = false
        quizNextButton.isEnabled = true
        renderQuizQuestion()
    }

    private fun renderQuizQuestion() {
        val question = currentQuizQuestion ?: return
        quizPrompt.text = getString(R.string.traffic_signs_quiz_prompt)
        loadImage(quizImage, question.sign.imageAssetPath, 220f, 220f)
        quizOptionButtons.forEachIndexed { index, button ->
            val value = question.options.getOrNull(index).orEmpty()
            button.text = value
            button.isEnabled = !quizAnswered
            button.alpha = 1f
        }
    }

    private fun handleQuizAnswer(selectedAnswer: String) {
        val question = currentQuizQuestion ?: return
        if (quizAnswered) return
        quizAnswered = true
        quizQuestionsAnswered += 1
        val isCorrect = selectedAnswer == question.correctAnswer
        if (isCorrect) {
            quizCorrectAnswers += 1
            knownSignsSession.add(question.sign.id)
            quizFeedback.text = getString(R.string.traffic_signs_quiz_feedback_correct)
            quizFeedback.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_correct))
        } else {
            quizFeedback.text = getString(
                R.string.traffic_signs_quiz_feedback_incorrect,
                question.correctAnswer
            )
            quizFeedback.setTextColor(ContextCompat.getColor(this, R.color.theory_feedback_incorrect))
        }
        quizFeedback.isVisible = true
        quizOptionButtons.forEach { button ->
            val buttonText = button.text.toString()
            button.isEnabled = false
            if (buttonText == question.correctAnswer) {
                button.alpha = 1f
            } else if (buttonText == selectedAnswer) {
                button.alpha = 0.75f
            } else {
                button.alpha = 0.6f
            }
        }
        updateSessionChips()
    }

    private fun applyBrowseFilter() {
        val categoryId = selectedCategoryId
        val query = browseSearchQuery.trim().lowercase()
        filteredBrowseSigns = allSigns.filter { sign ->
            val categoryMatch = categoryId.isNullOrBlank() ||
                sign.primaryCategoryId == categoryId ||
                sign.categoryIds.contains(categoryId)
            if (!categoryMatch) return@filter false
            if (query.isBlank()) return@filter true
            sign.caption.lowercase().contains(query) ||
                sign.description.lowercase().contains(query) ||
                sign.code.lowercase().contains(query) ||
                sign.officialCategory.lowercase().contains(query)
        }
        val categoryText = selectedCategoryId?.let { categoryNameMap[it] } ?: getString(R.string.traffic_signs_category_all)
        filterSummary.text = getString(
            R.string.traffic_signs_filter_summary_value,
            categoryText,
            filteredBrowseSigns.size
        )
        browseResultCount.text = getString(R.string.traffic_signs_browse_results_value, filteredBrowseSigns.size)
        renderBrowseResults()
    }

    private fun renderBrowseResults() {
        val shown = filteredBrowseSigns.take(browseVisibleCount)
        gridAdapter.submitList(shown)
        loadMoreButton.isVisible = filteredBrowseSigns.size > shown.size
        if (loadMoreButton.isVisible) {
            val remaining = filteredBrowseSigns.size - shown.size
            loadMoreButton.text = getString(R.string.traffic_signs_load_more_value, remaining)
        }
    }

    private fun updateMode(mode: UiMode) {
        uiMode = mode
        flashcardsPanel.isVisible = mode == UiMode.FLASHCARDS
        quizPanel.isVisible = mode == UiMode.QUIZ
        browsePanel.isVisible = mode == UiMode.BROWSE
        styleModeButton(modeFlashcardsButton, mode == UiMode.FLASHCARDS)
        styleModeButton(modeQuizButton, mode == UiMode.QUIZ)
        styleModeButton(modeBrowseButton, mode == UiMode.BROWSE)
    }

    private fun styleModeButton(button: MaterialButton, selected: Boolean) {
        val accent = ContextCompat.getColor(this, R.color.settings_accent)
        val textPrimary = ContextCompat.getColor(this, R.color.settings_text_primary)
        val white = Color.WHITE
        button.backgroundTintList = ColorStateList.valueOf(if (selected) accent else white)
        button.strokeColor = ColorStateList.valueOf(if (selected) accent else ContextCompat.getColor(this, R.color.app_card_stroke))
        button.strokeWidth = (if (selected) 0 else 1) * resources.displayMetrics.density.roundToInt()
        button.setTextColor(if (selected) white else textPrimary)
    }

    private fun openOfficialSource() {
        val url = pack?.sourceReferences?.firstOrNull()
            ?: OFFICIAL_SOURCE_URL
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.traffic_signs_source_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSignDetail(sign: TrafficSign) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_traffic_sign_detail, null)
        val image = view.findViewById<ImageView>(R.id.trafficSignDetailImage)
        val caption = view.findViewById<TextView>(R.id.trafficSignDetailCaption)
        val meta = view.findViewById<TextView>(R.id.trafficSignDetailMeta)
        val description = view.findViewById<TextView>(R.id.trafficSignDetailDescription)

        caption.text = sign.caption.ifBlank { getString(R.string.traffic_signs_unknown_caption) }
        val categoryName = categoryNameMap[sign.primaryCategoryId] ?: sign.officialCategory
        val metaParts = mutableListOf<String>()
        if (sign.code.isNotBlank()) metaParts.add(getString(R.string.traffic_signs_sign_code_value, sign.code))
        if (categoryName.isNotBlank()) metaParts.add(categoryName)
        if (sign.officialCategories.isNotEmpty()) {
            metaParts.add(sign.officialCategories.joinToString(", "))
        }
        meta.text = metaParts.joinToString(" • ")
        description.text = sign.description.ifBlank { getString(R.string.traffic_signs_no_description) }
        loadImage(image, sign.imageAssetPath, 260f, 260f)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.traffic_signs_detail_title))
            .setView(view)
            .setPositiveButton(R.string.traffic_signs_close, null)
            .setNeutralButton(R.string.traffic_signs_use_in_quiz) { _, _ ->
                selectedCategoryId = sign.primaryCategoryId
                categoryFilterGroup.children
                    .filterIsInstance<Chip>()
                    .firstOrNull { (it.tag as? String) == sign.primaryCategoryId }
                    ?.isChecked = true
                updateMode(UiMode.QUIZ)
                generateQuizQuestion()
            }
            .show()
    }

    private fun loadImage(imageView: ImageView, assetPath: String, widthDp: Float, heightDp: Float) {
        imageView.tag = assetPath
        imageView.setImageDrawable(null)
        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        lifecycleScope.launch {
            val bitmap = bitmapLoader.load(assetPath, widthPx, heightPx)
            if (imageView.tag == assetPath) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private enum class UiMode {
        FLASHCARDS,
        QUIZ,
        BROWSE
    }

    private companion object {
        const val OFFICIAL_SOURCE_URL = "https://www.gov.uk/government/publications/know-your-traffic-signs"
        const val BROWSE_PAGE_SIZE = 24
    }
}
