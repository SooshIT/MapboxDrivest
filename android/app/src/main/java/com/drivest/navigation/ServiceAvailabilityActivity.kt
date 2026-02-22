package com.drivest.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityServiceAvailabilityBinding

class ServiceAvailabilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceAvailabilityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceAvailabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.serviceAvailabilityBackButton.setOnClickListener {
            finish()
        }
    }
}
