package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.google.android.material.button.MaterialButton

class TheoryTopicDetailActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_topic_detail)

        val topicId = intent.getStringExtra(TheoryNavigation.EXTRA_TOPIC_ID).orEmpty()
        val pack = packLoader.load()
        val topic = pack.topics.firstOrNull { it.id == topicId }
        if (topic == null) {
            Toast.makeText(this, R.string.theory_topic_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.theoryTopicTitle).text = topic.title
        findViewById<TextView>(R.id.theoryTopicDescription).text = topic.description

        val lessonsContainer = findViewById<LinearLayout>(R.id.theoryTopicLessonsContainer)
        val lessons = pack.lessons.filter { it.topicId == topic.id }
        lessons.forEach { lesson ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = lesson.title
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.theory_list_item_spacing)
                }
                setOnClickListener {
                    startActivity(
                        Intent(this@TheoryTopicDetailActivity, TheoryLessonActivity::class.java).apply {
                            putExtra(TheoryNavigation.EXTRA_LESSON_ID, lesson.id)
                            putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "topic_detail")
                        }
                    )
                }
            }
            lessonsContainer.addView(button)
        }

        findViewById<MaterialButton>(R.id.theoryTopicQuiz10Button).setOnClickListener {
            openQuiz(topic.id, 10)
        }
        findViewById<MaterialButton>(R.id.theoryTopicQuiz20Button).setOnClickListener {
            openQuiz(topic.id, 20)
        }
        findViewById<MaterialButton>(R.id.theoryTopicQuiz30Button).setOnClickListener {
            openQuiz(topic.id, 30)
        }
    }

    private fun openQuiz(topicId: String, count: Int) {
        startActivity(
            Intent(this, TheoryQuizActivity::class.java).apply {
                putExtra(TheoryNavigation.EXTRA_QUIZ_TOPIC_ID, topicId)
                putExtra(TheoryNavigation.EXTRA_QUIZ_COUNT, count)
                putExtra(TheoryNavigation.EXTRA_QUIZ_MODE, TheoryNavigation.QUIZ_MODE_TOPIC)
                putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "topic_detail")
            }
        )
    }
}
