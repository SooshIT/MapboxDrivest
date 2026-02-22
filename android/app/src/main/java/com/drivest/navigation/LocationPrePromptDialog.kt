package com.drivest.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LocationPrePromptDialog {
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun show(
        activity: AppCompatActivity,
        onAllow: () -> Unit,
        onNotNow: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.location_pre_prompt_title)
            .setMessage(R.string.location_pre_prompt_message)
            .setPositiveButton(R.string.location_pre_prompt_allow) { _, _ ->
                onAllow()
            }
            .setNegativeButton(R.string.location_pre_prompt_not_now) { _, _ ->
                onNotNow()
            }
            .setCancelable(true)
            .show()
    }
}
