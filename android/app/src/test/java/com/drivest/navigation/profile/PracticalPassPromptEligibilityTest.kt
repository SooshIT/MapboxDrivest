package com.drivest.navigation.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalPassPromptEligibilityTest {

    @Test
    fun lessThanFiveCompletionsIsNotEligible() {
        assertFalse(
            PracticalPassPromptEligibility.isEligible(
                mode = DriverMode.LEARNER,
                practiceSessionsCompletedCount = 4,
                promptAlreadyShown = false
            )
        )
    }

    @Test
    fun fiveOrMoreCompletionsIsEligibleWhenNotShown() {
        assertTrue(
            PracticalPassPromptEligibility.isEligible(
                mode = DriverMode.LEARNER,
                practiceSessionsCompletedCount = 5,
                promptAlreadyShown = false
            )
        )
    }

    @Test
    fun shownPromptIsNotEligibleAgain() {
        assertFalse(
            PracticalPassPromptEligibility.isEligible(
                mode = DriverMode.LEARNER,
                practiceSessionsCompletedCount = 8,
                promptAlreadyShown = true
            )
        )
    }
}
