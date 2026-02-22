package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class TheoryMockResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_mock_results)

        val score = intent.getIntExtra(TheoryNavigation.EXTRA_MOCK_SCORE, 0)
        val total = intent.getIntExtra(TheoryNavigation.EXTRA_MOCK_TOTAL, 0)
        val breakdownRaw = intent.getStringExtra(TheoryNavigation.EXTRA_MOCK_BREAKDOWN).orEmpty()

        findViewById<TextView>(R.id.theoryMockResultsScore).text = getString(
            R.string.theory_mock_score_value,
            score,
            total
        )
        findViewById<TextView>(R.id.theoryMockResultsOutcome).text = if (score >= 43) {
            getString(R.string.theory_mock_outcome_pass)
        } else {
            getString(R.string.theory_mock_outcome_revise)
        }
        findViewById<TextView>(R.id.theoryMockResultsBreakdown).text = buildBreakdownText(breakdownRaw)

        findViewById<MaterialButton>(R.id.theoryMockResultsReviewWrongButton).setOnClickListener {
            startActivity(Intent(this, TheoryWrongAnswersActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.theoryMockResultsDoneButton).setOnClickListener {
            finish()
        }
    }

    private fun buildBreakdownText(raw: String): String {
        if (raw.isBlank()) return getString(R.string.theory_mock_breakdown_empty)
        return runCatching {
            val root = JSONObject(raw)
            val lines = mutableListOf<String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val topicId = keys.next()
                val item = root.optJSONObject(topicId) ?: continue
                val attempts = item.optInt("attempts", 0)
                val correct = item.optInt("correct", 0)
                lines += "$topicId: $correct/$attempts"
            }
            if (lines.isEmpty()) {
                getString(R.string.theory_mock_breakdown_empty)
            } else {
                lines.joinToString("\n")
            }
        }.getOrElse {
            getString(R.string.theory_mock_breakdown_empty)
        }
    }
}
