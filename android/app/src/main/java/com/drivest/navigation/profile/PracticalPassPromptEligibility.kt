package com.drivest.navigation.profile

object PracticalPassPromptEligibility {
    fun isEligible(
        mode: DriverMode,
        practiceSessionsCompletedCount: Int,
        promptAlreadyShown: Boolean
    ): Boolean {
        return mode == DriverMode.LEARNER &&
            practiceSessionsCompletedCount >= MIN_PRACTICE_COMPLETIONS &&
            !promptAlreadyShown
    }

    private const val MIN_PRACTICE_COMPLETIONS = 5
}
