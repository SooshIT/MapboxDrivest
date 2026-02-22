package com.drivest.navigation.prompts

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBudgetEnforcerTest {

    private val enforcer = SpeechBudgetEnforcer()

    @Test
    fun anyGeneratedPhraseStaysWithinCharAndWordBudget() {
        PromptType.values().forEachIndexed { index, type ->
            val result = enforcer.enforce(
                text = longTemplate(type),
                promptType = type,
                distanceMeters = 180 + index * 10
            )
            assertTrue("Expected <= 70 chars for $type but got ${result.length}", result.length <= 70)
            assertTrue(
                "Expected <= 14 words for $type but got ${wordCount(result)}",
                wordCount(result) <= 14
            )
        }
    }

    @Test
    fun eachHazardTypeStillContainsItsKeyword() {
        PromptType.values().forEach { type ->
            val result = enforcer.enforce(
                text = longTemplate(type),
                promptType = type,
                distanceMeters = 220
            ).lowercase()
            assertTrue(
                "Expected keyword for $type in '$result'",
                result.contains(expectedKeyword(type))
            )
        }
    }

    private fun longTemplate(type: PromptType): String {
        return "Advisory for ${type.name.lowercase()}: this sentence is intentionally very long so the " +
            "budget enforcer must shorten it deterministically while keeping the essential hazard guidance."
    }

    private fun expectedKeyword(type: PromptType): String {
        return when (type) {
            PromptType.ROUNDABOUT -> "roundabout"
            PromptType.MINI_ROUNDABOUT -> "mini roundabout"
            PromptType.SCHOOL_ZONE -> "school zone"
            PromptType.ZEBRA_CROSSING -> "zebra crossing"
            PromptType.GIVE_WAY -> "give way"
            PromptType.TRAFFIC_SIGNAL -> "traffic lights"
            PromptType.SPEED_CAMERA -> "speed camera"
            PromptType.BUS_LANE -> "bus lane"
            PromptType.BUS_STOP -> "bus stop"
            PromptType.NO_ENTRY -> "no entry"
        }
    }

    private fun wordCount(text: String): Int {
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
