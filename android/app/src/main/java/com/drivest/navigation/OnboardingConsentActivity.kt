package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivityOnboardingConsentBinding
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.legal.LegalConstants
import com.drivest.navigation.legal.LegalIntentUtils
import kotlinx.coroutines.launch

class OnboardingConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingConsentBinding
    private val consentRepository by lazy { ConsentRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingViewTermsButton.setOnClickListener {
            val launched = LegalIntentUtils.openExternalUrl(this, LegalConstants.TERMS_URL)
            if (!launched) {
                Toast.makeText(this, getString(R.string.settings_no_browser_app), Toast.LENGTH_SHORT).show()
            }
        }

        binding.onboardingViewPrivacyButton.setOnClickListener {
            val launched = LegalIntentUtils.openExternalUrl(this, LegalConstants.PRIVACY_URL)
            if (!launched) {
                Toast.makeText(this, getString(R.string.settings_no_browser_app), Toast.LENGTH_SHORT).show()
            }
        }

        binding.onboardingAcceptButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.acceptTermsAndPrivacy()
                startActivity(Intent(this@OnboardingConsentActivity, AgeRequirementActivity::class.java))
                finish()
            }
        }
    }
}
