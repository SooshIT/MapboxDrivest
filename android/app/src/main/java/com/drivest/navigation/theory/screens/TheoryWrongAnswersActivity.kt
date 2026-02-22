package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TheoryWrongAnswersActivity : AppCompatActivity() {

    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val packLoader by lazy { TheoryPackLoader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_wrong_answers)

        val summaryView = findViewById<TextView>(R.id.theoryWrongAnswersSummary)
        val container = findViewById<LinearLayout>(R.id.theoryWrongAnswersListContainer)
        val startQuizButton = findViewById<MaterialButton>(R.id.theoryWrongAnswersStartQuizButton)

        lifecycleScope.launch {
            val progress = progressStore.progress.first()
            val pack = packLoader.load()
            val wrongQueue = progress.wrongQueue
            summaryView.text = getString(R.string.theory_wrong_answers_summary_value, wrongQueue.size)
            if (wrongQueue.isEmpty()) {
                container.visibility = View.GONE
                startQuizButton.isEnabled = false
                return@launch
            }
            val wrongQuestions = pack.questions.filter { wrongQueue.contains(it.id) }
            wrongQuestions.take(30).forEach { question ->
                val text = TextView(this@TheoryWrongAnswersActivity).apply {
                    text = "- ${question.prompt}"
                    textSize = 14f
                    setTextColor(0xFF374151.toInt())
                }
                container.addView(text)
            }
            startQuizButton.setOnClickListener {
                startActivity(
                    Intent(this@TheoryWrongAnswersActivity, TheoryQuizActivity::class.java).apply {
                        putExtra(TheoryNavigation.EXTRA_QUIZ_MODE, TheoryNavigation.QUIZ_MODE_WRONG)
                        putExtra(TheoryNavigation.EXTRA_QUIZ_COUNT, 30)
                        putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "wrong_answers")
                    }
                )
            }
        }
    }
}
