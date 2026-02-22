package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.databinding.ActivityPaywallBinding
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionRestoreCoordinator
import com.drivest.navigation.subscription.billing.BillingRestoreResult
import com.drivest.navigation.subscription.billing.BillingService
import com.drivest.navigation.subscription.billing.GooglePlayBillingFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val billingFacade by lazy { GooglePlayBillingFacade(applicationContext) }
    private val billingService by lazy {
        BillingService(
            subscriptionRepository = subscriptionRepository,
            billingClientFacade = billingFacade
        )
    }
    private val restoreCoordinator by lazy {
        SubscriptionRestoreCoordinator(billingService = billingService)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val featureLabel = intent.getStringExtra(EXTRA_FEATURE_LABEL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        binding.paywallTitle.text = getString(
            if (featureLabel == null) R.string.paywall_title else R.string.paywall_title_feature,
            featureLabel ?: ""
        )
        binding.paywallMessage.text = getString(
            if (featureLabel == null) R.string.paywall_message else R.string.paywall_message_feature,
            featureLabel ?: ""
        )
        binding.paywallCloseButton.setOnClickListener { finish() }
        binding.paywallPaymentsButton.setOnClickListener {
            startActivity(Intent(this, PaymentsSubscriptionsActivity::class.java))
        }
        binding.paywallRestoreButton.setOnClickListener {
            activityScope.launch {
                when (val result = restoreCoordinator.restore()) {
                    is BillingRestoreResult.Restored -> {
                        val messageRes = if (result.active) {
                            R.string.payments_restore_success_placeholder
                        } else {
                            R.string.payments_restore_no_active
                        }
                        Toast.makeText(
                            this@PaywallActivity,
                            getString(messageRes),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BillingRestoreResult.Failed -> {
                        if (result.reason.contains("disabled", ignoreCase = true)) {
                            restoreCoordinator.showRestoreUnavailableDialog(this@PaywallActivity)
                        } else {
                            Toast.makeText(
                                this@PaywallActivity,
                                getString(R.string.payments_restore_failed_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        billingService.endConnection()
    }

    companion object {
        const val EXTRA_FEATURE_LABEL = "paywall_feature_label"
    }
}
