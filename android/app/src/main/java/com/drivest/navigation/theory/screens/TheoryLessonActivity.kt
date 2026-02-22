package com.drivest.navigation.theory.screens

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.R
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.telemetry.TelemetryEvent
import com.drivest.navigation.telemetry.TelemetryRepository
import com.drivest.navigation.theory.TheoryFeatureFlags
import com.drivest.navigation.theory.content.TheoryPackLoader
import com.drivest.navigation.theory.navigation.TheoryNavigation
import com.drivest.navigation.theory.storage.TheoryProgressStore
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class TheoryLessonActivity : AppCompatActivity() {

    private val packLoader by lazy { TheoryPackLoader(applicationContext) }
    private val progressStore by lazy { TheoryProgressStore(applicationContext) }
    private val telemetryRepository by lazy {
        TelemetryRepository(
            settingsRepository = SettingsRepository(applicationContext),
            consentRepository = ConsentRepository(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_lesson)

        val lessonId = intent.getStringExtra(TheoryNavigation.EXTRA_LESSON_ID).orEmpty()
        val pack = packLoader.load()
        val lesson = pack.lessons.firstOrNull { it.id == lessonId }
        if (lesson == null) {
            Toast.makeText(this, R.string.theory_lesson_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.theoryLessonTitle).text = lesson.title
        findViewById<TextView>(R.id.theoryLessonContent).text = lesson.content
        findViewById<TextView>(R.id.theoryLessonKeyPoints).text =
            lesson.keyPoints.joinToString("\n") { "- $it" }

        lifecycleScope.launch {
            telemetryRepository.sendEvent(
                TelemetryEvent.App(
                    eventType = "theory_lesson_start",
                    payload = mapOf("lessonId" to lesson.id, "topicId" to lesson.topicId)
                )
            )
        }

        findViewById<MaterialButton>(R.id.theoryLessonCompleteButton).setOnClickListener {
            lifecycleScope.launch {
                progressStore.markLessonCompleted(lesson.id)
                telemetryRepository.sendEvent(
                    TelemetryEvent.App(
                        eventType = "theory_lesson_complete",
                        payload = mapOf("lessonId" to lesson.id, "topicId" to lesson.topicId)
                    )
                )
                Toast.makeText(
                    this@TheoryLessonActivity,
                    R.string.theory_lesson_marked_complete,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
