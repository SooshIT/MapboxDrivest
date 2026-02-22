package com.drivest.navigation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityAboutDrivestBinding
import com.drivest.navigation.legal.LegalConstants
import com.drivest.navigation.legal.LegalIntentUtils

class AboutDrivestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutDrivestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutDrivestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.aboutAddressValue.text = LegalConstants.COMPANY_ADDRESS
        binding.aboutEmailValue.text = LegalConstants.SUPPORT_EMAIL
        binding.aboutLegalMetaValue.text = getString(
            R.string.settings_about_legal_meta,
            LegalConstants.TERMS_VERSION,
            LegalConstants.PRIVACY_VERSION,
            LegalConstants.LEGAL_LAST_UPDATED
        )

        binding.aboutBackButton.setOnClickListener {
            finish()
        }

        binding.aboutOpenTermsButton.setOnClickListener {
            openUrl(LegalConstants.TERMS_URL)
        }
        binding.aboutOpenPrivacyButton.setOnClickListener {
            openUrl(LegalConstants.PRIVACY_URL)
        }
    }

    private fun openUrl(url: String) {
        val launched = LegalIntentUtils.openExternalUrl(this, url)
        if (!launched) {
            Toast.makeText(this, getString(R.string.settings_no_browser_app), Toast.LENGTH_SHORT).show()
        }
    }
}
