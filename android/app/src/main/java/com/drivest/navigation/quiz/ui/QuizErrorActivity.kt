package com.drivest.navigation.quiz.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.google.android.material.button.MaterialButton

class QuizErrorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_error)

        val retryButton = findViewById<MaterialButton>(R.id.quizErrorRetryButton)
        retryButton.setOnClickListener {
            val intent = Intent(this, QuizHubActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
