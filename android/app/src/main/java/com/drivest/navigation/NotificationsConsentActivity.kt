package com.drivest.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drivest.navigation.databinding.ActivityNotificationsConsentBinding
import com.drivest.navigation.legal.ConsentRepository
import com.drivest.navigation.settings.SettingsRepository
import kotlinx.coroutines.launch

class NotificationsConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsConsentBinding
    private val consentRepository by lazy { ConsentRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        settingsRepository.refreshNotificationsPermission()
        continueToHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.notificationsSkipButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.setNotificationsPreference(enabled = false)
                settingsRepository.setNotificationsPreference(enabled = false)
                settingsRepository.refreshNotificationsPermission()
                continueToHome()
            }
        }

        binding.notificationsEnableButton.setOnClickListener {
            lifecycleScope.launch {
                consentRepository.setNotificationsPreference(enabled = true)
                settingsRepository.setNotificationsPreference(enabled = true)
                requestNotificationsPermissionIfNeeded()
            }
        }
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            settingsRepository.refreshNotificationsPermission()
            continueToHome()
            return
        }
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            settingsRepository.refreshNotificationsPermission()
            continueToHome()
            return
        }
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun continueToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
