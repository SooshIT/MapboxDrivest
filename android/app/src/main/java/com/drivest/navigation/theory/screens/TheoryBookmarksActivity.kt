package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TheoryBookmarksActivity : AppCompatActivity() {

    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val packLoader by lazy { TheoryPackLoader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_bookmarks)

        val summaryView = findViewById<TextView>(R.id.theoryBookmarksSummary)
        val container = findViewById<LinearLayout>(R.id.theoryBookmarksListContainer)
        val startQuizButton = findViewById<MaterialButton>(R.id.theoryBookmarksStartQuizButton)

        lifecycleScope.launch {
            val progress = progressStore.progress.first()
            val pack = packLoader.load()
            val bookmarks = progress.bookmarks
            summaryView.text = getString(R.string.theory_bookmarks_summary_value, bookmarks.size)
            if (bookmarks.isEmpty()) {
                container.visibility = View.GONE
                startQuizButton.isEnabled = false
                return@launch
            }

            val bookmarkQuestions = pack.questions.filter { bookmarks.contains(it.id) }
            bookmarkQuestions.take(30).forEach { question ->
                val text = TextView(this@TheoryBookmarksActivity).apply {
                    text = "- ${question.prompt}"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@TheoryBookmarksActivity, R.color.theory_text_secondary))
                    setPadding(10, 8, 10, 8)
                }
                container.addView(text)
            }
            startQuizButton.setOnClickListener {
                startActivity(
                    Intent(this@TheoryBookmarksActivity, TheoryQuizActivity::class.java).apply {
                        putExtra(TheoryNavigation.EXTRA_QUIZ_MODE, TheoryNavigation.QUIZ_MODE_BOOKMARKS)
                        putExtra(TheoryNavigation.EXTRA_QUIZ_COUNT, 30)
                        putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "bookmarks")
                    }
                )
            }
        }
    }
}
