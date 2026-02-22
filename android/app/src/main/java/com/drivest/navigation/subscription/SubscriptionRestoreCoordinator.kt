package com.drivest.navigation.subscription

import androidx.appcompat.app.AppCompatActivity
import com.drivest.navigation.R
import com.drivest.navigation.subscription.billing.BillingRestoreResult
import com.drivest.navigation.subscription.billing.BillingService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SubscriptionRestoreCoordinator(
    private val billingService: BillingService? = null,
    private val billingEnabledProvider: () -> Boolean = { BillingConfig.ENABLE_BILLING }
) {

    suspend fun restore(): BillingRestoreResult {
        if (!billingEnabledProvider()) {
            return BillingRestoreResult.Failed("Billing disabled")
        }
        val service = billingService ?: return BillingRestoreResult.Failed("Billing unavailable")
        return runCatching { service.restore() }
            .getOrElse { throwable ->
                BillingRestoreResult.Failed(
                    throwable.message ?: "Restore failed"
                )
            }
    }

    fun showRestoreUnavailableDialog(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.payments_restore_title)
            .setMessage(R.string.payments_restore_stub_message)
            .setPositiveButton(R.string.payments_action_ok, null)
            .show()
    }
}
