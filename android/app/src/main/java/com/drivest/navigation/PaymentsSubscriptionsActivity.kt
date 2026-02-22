package com.drivest.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivest.navigation.databinding.ActivityPaymentsSubscriptionsBinding
import com.drivest.navigation.settings.SettingsRepository
import com.drivest.navigation.subscription.BillingConfig
import com.drivest.navigation.subscription.StoreSubscriptionLinks
import com.drivest.navigation.subscription.SubscriptionRepository
import com.drivest.navigation.subscription.SubscriptionRestoreCoordinator
import com.drivest.navigation.subscription.SubscriptionState
import com.drivest.navigation.subscription.SubscriptionTier
import com.drivest.navigation.subscription.requiresPracticeCentreSelection
import com.drivest.navigation.subscription.billing.BillingProduct
import com.drivest.navigation.subscription.billing.BillingPurchaseResult
import com.drivest.navigation.subscription.billing.BillingRestoreResult
import com.drivest.navigation.subscription.billing.BillingService
import com.drivest.navigation.subscription.billing.GooglePlayBillingFacade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentsSubscriptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentsSubscriptionsBinding
    private val subscriptionRepository by lazy { SubscriptionRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
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
    private val expiryDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.UK)
    private val practiceCentreSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(
                this,
                getString(R.string.payments_practice_centre_selected),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentsSubscriptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindActions()
        observeCurrentPlan()
        loadAvailableProducts()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingService.endConnection()
    }

    private fun bindActions() {
        binding.paymentsBackButton.setOnClickListener { finish() }

        binding.viewSubscriptionOptionsButton.setOnClickListener {
            startActivity(
                Intent(this, PaywallActivity::class.java).putExtra(
                    PaywallActivity.EXTRA_FEATURE_LABEL,
                    getString(R.string.payments_view_options_feature_label)
                )
            )
        }

        binding.subscribePracticeButton.setOnClickListener {
            purchaseProduct(BillingConfig.SKU_PRACTICE_MONTHLY)
        }

        binding.subscribeGlobalButton.setOnClickListener {
            purchaseProduct(BillingConfig.SKU_GLOBAL_ANNUAL)
        }

        binding.restorePurchasesButton.setOnClickListener {
            lifecycleScope.launch {
                when (val restored = restoreCoordinator.restore()) {
                    is BillingRestoreResult.Restored -> {
                        val messageRes = if (restored.active) {
                            R.string.payments_restore_success_placeholder
                        } else {
                            R.string.payments_restore_no_active
                        }
                        Toast.makeText(
                            this@PaymentsSubscriptionsActivity,
                            getString(messageRes),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BillingRestoreResult.Failed -> {
                        if (restored.reason.contains("disabled", ignoreCase = true)) {
                            restoreCoordinator.showRestoreUnavailableDialog(this@PaymentsSubscriptionsActivity)
                        } else {
                            Toast.makeText(
                                this@PaymentsSubscriptionsActivity,
                                getString(R.string.payments_restore_failed_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        binding.manageSubscriptionButton.setOnClickListener {
            val opened = StoreSubscriptionLinks.openManageSubscriptions(this)
            if (!opened) {
                Toast.makeText(
                    this,
                    getString(R.string.payments_manage_open_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadAvailableProducts() {
        if (!BillingConfig.ENABLE_BILLING) {
            binding.plansLoadingValue.isVisible = false
            setPurchaseButtonsEnabled(false)
            return
        }

        binding.plansLoadingValue.text = getString(R.string.payments_loading_plans)
        binding.plansLoadingValue.isVisible = true
        lifecycleScope.launch {
            val products = runCatching { billingService.getAvailableProducts() }
                .getOrElse { emptyList() }
            if (products.isNotEmpty()) {
                renderDynamicPlanPrices(products)
                binding.plansLoadingValue.isVisible = false
            } else {
                binding.plansLoadingValue.text = getString(R.string.payments_loading_failed_using_static)
                binding.plansLoadingValue.isVisible = true
            }
            setPurchaseButtonsEnabled(BillingConfig.ENABLE_BILLING)
        }
    }

    private fun observeCurrentPlan() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                subscriptionRepository.subscriptionState.collect { state ->
                    renderCurrentPlan(state)
                }
            }
        }
    }

    private fun renderCurrentPlan(state: SubscriptionState) {
        val nowMs = System.currentTimeMillis()
        val effectiveTier = state.effectiveTier(nowMs)
        binding.currentPlanTierValue.text = getString(
            R.string.payments_current_plan_tier_value,
            tierLabel(effectiveTier)
        )
        binding.currentPlanExpiryValue.text = if (state.isActive(nowMs) && state.expiryMs > 0L) {
            getString(
                R.string.payments_current_plan_expiry_value,
                expiryDateFormat.format(Date(state.expiryMs))
            )
        } else {
            getString(R.string.payments_current_plan_free_value)
        }
    }

    private fun renderDynamicPlanPrices(products: List<BillingProduct>) {
        val practicePrice = products
            .firstOrNull { it.productId == BillingConfig.SKU_PRACTICE_MONTHLY }
            ?.price
            .orEmpty()
        val globalPrice = products
            .firstOrNull { it.productId == BillingConfig.SKU_GLOBAL_ANNUAL }
            ?.price
            .orEmpty()

        if (practicePrice.isNotBlank()) {
            binding.practicePlanPrice.text = practicePrice
        }
        if (globalPrice.isNotBlank()) {
            binding.globalPlanPrice.text = globalPrice
        }
    }

    private fun setPurchaseButtonsEnabled(enabled: Boolean) {
        binding.subscribePracticeButton.isEnabled = enabled
        binding.subscribeGlobalButton.isEnabled = enabled
    }

    private fun purchaseProduct(productId: String) {
        if (!BillingConfig.ENABLE_BILLING) {
            Toast.makeText(this, getString(R.string.payments_billing_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = billingService.purchase(this@PaymentsSubscriptionsActivity, productId)
            when (result) {
                is BillingPurchaseResult.Success -> {
                    val purchasedTier = BillingConfig.tierForProduct(productId)
                    if (
                        purchasedTier == SubscriptionTier.PRACTICE_MONTHLY &&
                        requiresPracticeCentreSelection(
                            tier = purchasedTier,
                            practiceCentreId = settingsRepository.practiceCentreId.first()
                        )
                    ) {
                        Toast.makeText(
                            this@PaymentsSubscriptionsActivity,
                            getString(R.string.payments_select_practice_centre_required),
                            Toast.LENGTH_SHORT
                        ).show()
                        launchPracticeCentreSelection()
                    }
                    Toast.makeText(
                        this@PaymentsSubscriptionsActivity,
                        getString(R.string.payments_purchase_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                BillingPurchaseResult.Cancelled -> {
                    Toast.makeText(
                        this@PaymentsSubscriptionsActivity,
                        getString(R.string.payments_purchase_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is BillingPurchaseResult.Failed -> {
                    Toast.makeText(
                        this@PaymentsSubscriptionsActivity,
                        getString(R.string.payments_purchase_failed_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun launchPracticeCentreSelection() {
        val selectionIntent = Intent(this, CentrePickerActivity::class.java).apply {
            putExtra(CentrePickerActivity.EXTRA_SELECT_PRACTICE_CENTRE_ONLY, true)
        }
        practiceCentreSelectionLauncher.launch(selectionIntent)
    }

    private fun tierLabel(tier: SubscriptionTier): String {
        return when (tier) {
            SubscriptionTier.FREE -> getString(R.string.payments_tier_free)
            SubscriptionTier.PRACTICE_MONTHLY -> getString(R.string.payments_tier_practice_monthly)
            SubscriptionTier.GLOBAL_ANNUAL -> getString(R.string.payments_tier_global_annual)
        }
    }
}
