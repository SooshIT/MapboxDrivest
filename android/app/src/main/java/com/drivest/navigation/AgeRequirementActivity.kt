package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivityAgeRequirementBinding
import com.drivest.navigation.legal.ConsentRepository
import kotlinx.coroutines.launch

class AgeRequirementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgeRequirementBinding
    private val consentRepository by lazy { ConsentRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgeRequirementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ageConfirmButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.confirmAge()
                startActivity(Intent(this@AgeRequirementActivity, AnalyticsConsentActivity::class.java))
                finish()
            }
        }

        binding.ageExitButton.setOnClickListener {
            finishAffinity()
        }
    }
}
