package com.drivest.navigation

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PracticalPassPromptDialog {
    fun show(
        activity: AppCompatActivity,
        onResult: (passed: Boolean) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.practical_pass_prompt_title)
            .setMessage(R.string.practical_pass_prompt_message)
            .setPositiveButton(R.string.practical_pass_prompt_yes) { _, _ ->
                onResult(true)
            }
            .setNegativeButton(R.string.practical_pass_prompt_not_yet) { _, _ ->
                onResult(false)
            }
            .setOnCancelListener {
                onResult(false)
            }
            .show()
    }
}
