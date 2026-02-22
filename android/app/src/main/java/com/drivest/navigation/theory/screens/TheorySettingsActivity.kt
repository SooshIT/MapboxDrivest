package com.drivest.navigation.theory.screens

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.drivest.navigation.theory.TheoryFeatureFlags

class TheorySettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TheoryFeatureFlags.isTheoryModuleEnabled()) {
            finish()
            return
        }
        setContentView(R.layout.activity_theory_settings)
    }
}
