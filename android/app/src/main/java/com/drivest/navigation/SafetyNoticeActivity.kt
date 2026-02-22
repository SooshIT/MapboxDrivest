package com.drivest.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivitySafetyNoticeBinding

class SafetyNoticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafetyNoticeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafetyNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.safetyNoticeBackButton.setOnClickListener {
            finish()
        }
    }
}
