package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivityAnalyticsConsentBinding
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.launch

class AnalyticsConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsConsentBinding
    private val consentRepository by lazy { ConsentRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val reviewOnly: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_REVIEW_ONLY, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.analyticsAllowButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.setAnalyticsConsent(enabled = true)
                settingsRepository.setAnalyticsEnabled(enabled = true)
                if (reviewOnly) {
                    finish()
                } else {
                    startActivity(Intent(this@AnalyticsConsentActivity, NotificationsConsentActivity::class.java))
                    finish()
                }
            }
        }

        binding.analyticsSkipButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.setAnalyticsConsent(enabled = false)
                settingsRepository.setAnalyticsEnabled(enabled = false)
                if (reviewOnly) {
                    finish()
                } else {
                    startActivity(Intent(this@AnalyticsConsentActivity, NotificationsConsentActivity::class.java))
                    finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_REVIEW_ONLY = "analytics_consent_review_only"
    }
}
