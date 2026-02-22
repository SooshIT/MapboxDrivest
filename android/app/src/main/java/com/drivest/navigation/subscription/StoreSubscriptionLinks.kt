package com.drivest.navigation.subscription

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object StoreSubscriptionLinks {
    const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

    fun openManageSubscriptions(context: Context): Boolean {
        val manageUrl = "$PLAY_SUBSCRIPTIONS_URL?package=${context.packageName}"
        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(manageUrl)).apply {
            setPackage(PLAY_STORE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (playIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(playIntent)
            return true
        }

        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (appDetailsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(appDetailsIntent)
            return true
        }
        return false
    }

    private const val PLAY_STORE_PACKAGE = "com.android.vending"
}
