package com.drivest.navigation.theory.screens

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.google.android.material.button.MaterialButton

class TheoryTopicListActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_topic_list)
        val container = findViewById<LinearLayout>(R.id.theoryTopicListContainer)
        val pack = packLoader.load()
        if (pack.topics.isEmpty()) {
            Toast.makeText(this, R.string.theory_content_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pack.topics.forEach { topic ->
            val button = MaterialButton(this).apply {
                text = topic.title
                isAllCaps = false
                setOnClickListener {
                    startActivity(
                        Intent(this@TheoryTopicListActivity, TheoryTopicDetailActivity::class.java).apply {
                            putExtra(TheoryNavigation.EXTRA_TOPIC_ID, topic.id)
                            putExtra(TheoryNavigation.EXTRA_ENTRY_SOURCE, "topic_list")
                        }
                    )
                }
            }
            container.addView(button)
        }
    }
}
