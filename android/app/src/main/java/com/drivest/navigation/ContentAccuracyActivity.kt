package com.drivest.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityContentAccuracyBinding

class ContentAccuracyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContentAccuracyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContentAccuracyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contentAccuracyBackButton.setOnClickListener {
            finish()
        }
    }
}
