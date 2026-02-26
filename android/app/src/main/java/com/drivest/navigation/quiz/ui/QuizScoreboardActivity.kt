package com.drivest.navigation.quiz.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.R
import com.drivest.navigation.quiz.data.QuizProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class QuizScoreboardActivity : AppCompatActivity() {

    private lateinit var progressStore: QuizProgressStore

    private lateinit var titleView: TextView
    private lateinit var soloCard: LinearLayout
    private lateinit var scoreView: TextView
    private lateinit var accuracyView: TextView
    private lateinit var correctView: TextView
    private lateinit var newBestBadge: TextView
    private lateinit var partyList: RecyclerView
    private lateinit var winnerCard: LinearLayout
    private lateinit var winnerName: TextView
    private lateinit var winnerScore: TextView
    private lateinit var playAgainButton: MaterialButton
    private lateinit var homeButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_scoreboard)

        progressStore = QuizProgressStore(applicationContext)
        initializeViews()

        val isParty = intent.getBooleanExtra(QuizNavigation.EXTRA_IS_PARTY, false)
        if (isParty) {
            displayPartyResults()
        } else {
            displaySoloResults()
        }

        setupButtons()
    }

    private fun initializeViews() {
        titleView = findViewById(R.id.quizScoreboardTitle)
        soloCard = findViewById(R.id.quizScoreboardSoloCard)
        scoreView = findViewById(R.id.quizScoreboardScore)
        accuracyView = findViewById(R.id.quizScoreboardAccuracy)
        correctView = findViewById(R.id.quizScoreboardCorrect)
        newBestBadge = findViewById(R.id.quizScoreboardNewBestBadge)
        partyList = findViewById(R.id.quizScoreboardPartyList)
        winnerCard = findViewById(R.id.quizScoreboardWinnerCard)
        winnerName = findViewById(R.id.quizScoreboardWinnerName)
        winnerScore = findViewById(R.id.quizScoreboardWinnerScore)
        playAgainButton = findViewById(R.id.quizScoreboardPlayAgainButton)
        homeButton = findViewById(R.id.quizScoreboardHomeButton)
    }

    private fun displaySoloResults() {
        titleView.setText(R.string.quiz_score_title_solo)
        soloCard.visibility = LinearLayout.VISIBLE
        partyList.visibility = RecyclerView.GONE
        winnerCard.visibility = LinearLayout.GONE

        val score = intent.getIntExtra(QuizNavigation.EXTRA_FINAL_SCORE, 0)
        val correct = intent.getIntExtra(QuizNavigation.EXTRA_FINAL_CORRECT, 0)
        val total = intent.getIntExtra(QuizNavigation.EXTRA_FINAL_TOTAL, 0)
        val accuracy = intent.getFloatExtra("accuracy", 0f)

        scoreView.text = score.toString()
        accuracyView.text = String.format("%.0f%%", accuracy * 100)
        correctView.text = "$correct / $total"

        // Check if new best score
        lifecycleScope.launch {
            progressStore.soloBestScore.collect { bestScore ->
                if (score > bestScore) {
                    newBestBadge.visibility = TextView.VISIBLE
                }
            }
        }

        // Record result
        lifecycleScope.launch {
            progressStore.recordSoloResult(score, correct, total, "en")
        }
    }

    private fun displayPartyResults() {
        titleView.setText(R.string.quiz_score_title_party)
        soloCard.visibility = LinearLayout.GONE
        partyList.visibility = RecyclerView.VISIBLE
        winnerCard.visibility = LinearLayout.VISIBLE

        val playerData = intent.getStringArrayListExtra(QuizNavigation.EXTRA_PLAYER_NAMES) ?: emptyList()
        val players = playerData.map { data ->
            val parts = data.split(":")
            Triple(parts.getOrNull(0) ?: "", parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toBoolean() ?: false)
        }.sortedByDescending { it.second }

        partyList.layoutManager = LinearLayoutManager(this)
        partyList.adapter = PartyScoreboardAdapter(players)

        val winnerName = intent.getStringExtra("winner_name") ?: "Unknown"
        val winnerScore = intent.getIntExtra("winner_score", 0)

        this.winnerName.text = winnerName
        this.winnerScore.text = getString(R.string.quiz_score_label) + ": $winnerScore"
    }

    private fun setupButtons() {
        playAgainButton.setOnClickListener {
            val intent = Intent(this, QuizHubActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, com.drivest.navigation.HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}

// Adapter for party scoreboard
class PartyScoreboardAdapter(
    private val players: List<Triple<String, Int, Boolean>>
) : RecyclerView.Adapter<PartyScoreboardAdapter.PlayerViewHolder>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PlayerViewHolder {
        val view = android.widget.LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_theory_panel)
            elevation = 3f
            setPadding(16, 16, 16, 16)
        }
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val (name, score, eliminated) = players[position]
        holder.bind(name, score, eliminated)
    }

    override fun getItemCount() = players.size

    class PlayerViewHolder(private val view: android.widget.LinearLayout) :
        RecyclerView.ViewHolder(view) {

        fun bind(name: String, score: Int, eliminated: Boolean) {
            view.removeAllViews()

            val rankText = android.widget.TextView(view.context).apply {
                text = "#${adapterPosition + 1} $name"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#1F2937"))
            }
            view.addView(rankText)

            val scoreText = android.widget.TextView(view.context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
                text = "Score: $score"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
            }
            view.addView(scoreText)

            if (eliminated) {
                val statusText = android.widget.TextView(view.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6 }
                    text = "Eliminated"
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#F44336"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                view.addView(statusText)
            }
        }
    }
}
