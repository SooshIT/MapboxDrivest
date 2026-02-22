package com.drivest.navigation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object SafetyNoticeDialog {
    fun show(
        activity: AppCompatActivity,
        onResult: (accepted: Boolean) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.safety_notice_title)
            .setMessage(R.string.safety_notice_body)
            .setPositiveButton(R.string.safety_notice_accept) { _, _ ->
                onResult(true)
            }
            .setNegativeButton(R.string.safety_notice_cancel) { _, _ ->
                onResult(false)
            }
            .setOnCancelListener {
                onResult(false)
            }
            .show()
    }
}
