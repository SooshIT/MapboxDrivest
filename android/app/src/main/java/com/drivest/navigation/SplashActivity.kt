package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.AppLanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private var hasRenderedFirstFrame = false
    private val consentRepository by lazy { ConsentRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLanguageManager.applyPersistedLanguageBlocking(this)
        setContentView(R.layout.activity_splash)
        waitForFirstFrameThenNavigate()
    }

    private fun waitForFirstFrameThenNavigate() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (hasRenderedFirstFrame) return true
                hasRenderedFirstFrame = true
                content.viewTreeObserver.removeOnPreDrawListener(this)
                lifecycleScope.launch {
                    delay(SPLASH_DURATION_MS)
                    startActivity(Intent(this@SplashActivity, resolveLaunchActivity()))
                    finish()
                }
                return true
            }
        })
    }

    private suspend fun resolveLaunchActivity(): Class<*> {
        val needsConsent = consentRepository.needsConsent.first()
        val needsAge = consentRepository.needsAge.first()
        val analyticsConsentAtMs = consentRepository.analyticsConsentAtMs.first()
        val notificationsConsentAtMs = consentRepository.notificationsConsentAtMs.first()
        return when (
            AppFlow.resolveOnboardingStep(
                needsConsent = needsConsent,
                needsAge = needsAge,
                analyticsConsentAtMs = analyticsConsentAtMs,
                notificationsConsentAtMs = notificationsConsentAtMs
            )
        ) {
            AppFlow.OnboardingStep.CONSENT -> OnboardingConsentActivity::class.java
            AppFlow.OnboardingStep.AGE -> AgeRequirementActivity::class.java
            AppFlow.OnboardingStep.ANALYTICS -> AnalyticsConsentActivity::class.java
            AppFlow.OnboardingStep.NOTIFICATIONS -> NotificationsConsentActivity::class.java
            AppFlow.OnboardingStep.COMPLETE -> HomeActivity::class.java
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 700L
    }
}
