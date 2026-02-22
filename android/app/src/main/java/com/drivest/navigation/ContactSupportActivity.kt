package com.drivest.navigation

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityContactSupportBinding
import com.drivest.navigation.legal.LegalConstants
import com.drivest.navigation.legal.LegalIntentUtils

class ContactSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactSupportEmailValue.text = LegalConstants.SUPPORT_EMAIL
        binding.contactSupportAddressValue.text = LegalConstants.COMPANY_ADDRESS

        binding.contactSupportBackButton.setOnClickListener {
            finish()
        }

        binding.contactSupportEmailButton.setOnClickListener {
            val body = buildString {
                appendLine("App version: ${BuildConfig.VERSION_NAME}")
                appendLine("Device model: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine("Describe your issue:")
            }
            val launched = LegalIntentUtils.openEmail(
                activity = this,
                to = LegalConstants.SUPPORT_EMAIL,
                subject = "Drivest Support",
                body = body
            )
            if (!launched) {
                Toast.makeText(
                    this,
                    getString(R.string.settings_no_email_app),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
