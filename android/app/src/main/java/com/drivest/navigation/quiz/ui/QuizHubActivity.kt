package com.drivest.navigation.quiz.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.drivest.navigation.quiz.data.QuizRepository
import com.drivest.navigation.quiz.data.QuizProgressStore
import kotlinx.coroutines.launch

class QuizHubActivity : AppCompatActivity() {

    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var soloRadio: RadioButton
    private lateinit var partyRadio: RadioButton
    private lateinit var playerNamesSection: LinearLayout
    private lateinit var playerNamesContainer: LinearLayout
    private lateinit var languageSpinner: Spinner
    private lateinit var timerSwitch: SwitchMaterial
    private lateinit var startButton: MaterialButton
    private lateinit var addPlayerButton: MaterialButton
    private lateinit var removePlayerButton: MaterialButton

    private lateinit var quizRepository: QuizRepository
    private lateinit var progressStore: QuizProgressStore
    private val playerNames = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_hub)

        quizRepository = QuizRepository(applicationContext)
        progressStore = QuizProgressStore(applicationContext)

        initializeViews()
        setupListeners()
        populateLocaleSpinner()
    }

    private fun initializeViews() {
        modeRadioGroup = findViewById(R.id.quizModeRadioGroup)
        soloRadio = findViewById(R.id.quizModeSoloRadio)
        partyRadio = findViewById(R.id.quizModePartyRadio)
        playerNamesSection = findViewById(R.id.quizPlayerNamesSection)
        playerNamesContainer = findViewById(R.id.quizPlayerNamesContainer)
        languageSpinner = findViewById(R.id.quizLanguageSpinner)
        timerSwitch = findViewById(R.id.quizTimerSwitch)
        startButton = findViewById(R.id.quizStartButton)
        addPlayerButton = findViewById(R.id.quizAddPlayerButton)
        removePlayerButton = findViewById(R.id.quizRemovePlayerButton)
    }

    private fun setupListeners() {
        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isParty = checkedId == R.id.quizModePartyRadio
            playerNamesSection.visibility = if (isParty) LinearLayout.VISIBLE else LinearLayout.GONE

            if (isParty && playerNames.isEmpty()) {
                addInitialPlayers()
            }
        }

        addPlayerButton.setOnClickListener {
            if (playerNames.size < 8) {
                addPlayerNameField()
            }
        }

        removePlayerButton.setOnClickListener {
            if (playerNames.size > 2) {
                removePlayerNameField()
            }
        }

        startButton.setOnClickListener {
            startQuiz()
        }
    }

    private fun populateLocaleSpinner() {
        lifecycleScope.launch {
            try {
                val locales = quizRepository.listAvailableLocales(QuizNavigation.DEFAULT_PACK_ID)
                val adapter = ArrayAdapter(
                    this@QuizHubActivity,
                    android.R.layout.simple_spinner_item,
                    locales.ifEmpty { listOf("en") }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                languageSpinner.adapter = adapter
            } catch (e: Exception) {
                languageSpinner.adapter = ArrayAdapter(
                    this@QuizHubActivity,
                    android.R.layout.simple_spinner_item,
                    listOf("en")
                )
            }
        }
    }

    private fun addInitialPlayers() {
        addPlayerNameField()
        addPlayerNameField()
    }

    private fun addPlayerNameField() {
        if (playerNames.size >= 8) return

        val editText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (playerNames.isNotEmpty()) {
                    topMargin = 12
                }
            }
            hint = getString(R.string.quiz_player_name_hint)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(16, 12, 16, 12)
        }
        playerNames.add(editText)
        playerNamesContainer.addView(editText)
    }

    private fun removePlayerNameField() {
        if (playerNames.isEmpty()) return
        val lastField = playerNames.removeAt(playerNames.size - 1)
        playerNamesContainer.removeView(lastField)
    }

    private fun startQuiz() {
        val isParty = partyRadio.isChecked
        val selectedLocale = languageSpinner.selectedItem as? String ?: "en"
        val timerEnabled = timerSwitch.isChecked

        // Validate party mode
        if (isParty) {
            val names = playerNames.map { it.text.toString().trim() }
                .filter { it.isNotEmpty() }

            if (names.size < 2) {
                com.google.android.material.snackbar.Snackbar.make(
                    startButton,
                    "Please enter at least 2 player names",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
                return
            }
        }

        // Try to load the pack
        try {
            quizRepository.loadPack(QuizNavigation.DEFAULT_PACK_ID, selectedLocale)
        } catch (e: Exception) {
            val intent = Intent(this, QuizErrorActivity::class.java)
            startActivity(intent)
            return
        }

        // Save locale preference
        lifecycleScope.launch {
            progressStore.recordSoloResult(0, 0, 0, selectedLocale)
        }

        // Launch quiz play activity
        val intent = Intent(this, QuizPlayActivity::class.java).apply {
            putExtra(QuizNavigation.EXTRA_PACK_ID, QuizNavigation.DEFAULT_PACK_ID)
            putExtra(QuizNavigation.EXTRA_LOCALE, selectedLocale)
            putExtra(QuizNavigation.EXTRA_IS_PARTY, isParty)
            putExtra(QuizNavigation.EXTRA_TIMER_ENABLED, timerEnabled)

            if (isParty) {
                val names = playerNames.map { it.text.toString().trim() }
                    .filter { it.isNotEmpty() }
                putStringArrayListExtra(QuizNavigation.EXTRA_PLAYER_NAMES, ArrayList(names))
            }
        }
        startActivity(intent)
    }
}
