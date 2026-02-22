package com.drivest.navigation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityDataRightsBinding
import com.drivest.navigation.legal.LegalConstants
import com.drivest.navigation.legal.LegalIntentUtils

class DataRightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataRightsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataRightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dataRightsBackButton.setOnClickListener {
            finish()
        }

        binding.dataRightsAccessButton.setOnClickListener {
            sendDataRequestEmail(subject = "Data Request - Access")
        }
        binding.dataRightsDeletionButton.setOnClickListener {
            sendDataRequestEmail(subject = "Data Request - Deletion")
        }
    }

    private fun sendDataRequestEmail(subject: String) {
        val body = buildString {
            appendLine("App version: ${BuildConfig.VERSION_NAME}")
            appendLine("Registered email (if any):")
            appendLine("Request details:")
        }
        val launched = LegalIntentUtils.openEmail(
            activity = this,
            to = LegalConstants.SUPPORT_EMAIL,
            subject = subject,
            body = body
        )
        if (!launched) {
            Toast.makeText(this, getString(R.string.settings_no_email_app), Toast.LENGTH_SHORT).show()
        }
    }
}
