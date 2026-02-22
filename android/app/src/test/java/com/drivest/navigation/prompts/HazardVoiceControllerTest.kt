package com.drivest.navigation.prompts

import com.drivest.navigation.settings.VoiceModeSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardVoiceControllerTest {

    @Test
    fun allModeSpeaksConfiguredHazardsIncludingBusStop() {
        var nowMs = 0L
        var mode = VoiceModeSetting.ALL
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { mode },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { it.type.name },
            nowProvider = { nowMs }
        )

        val allTypes = listOf(
            PromptType.ROUNDABOUT,
            PromptType.MINI_ROUNDABOUT,
            PromptType.SCHOOL_ZONE,
            PromptType.ZEBRA_CROSSING,
            PromptType.GIVE_WAY,
            PromptType.TRAFFIC_SIGNAL,
            PromptType.SPEED_CAMERA,
            PromptType.BUS_LANE,
            PromptType.BUS_STOP,
            PromptType.NO_ENTRY
        )
        allTypes.forEachIndexed { index, type ->
            nowMs = (index + 1) * 21_000L
            controller.enqueue(prompt(type, featureId = "f_$index"))
            controller.onHazardSpeechCompleted()
        }

        assertEquals(
            listOf(
                "roundabout",
                "mini roundabout",
                "school zone",
                "zebra crossing",
                "give way",
                "traffic lights",
                "speed camera",
                "bus stop",
                "no entry"
            ),
            output.spokenTexts.map { it.lowercase() }.map { spoken ->
                when {
                    spoken.contains("no entry") -> "no entry"
                    spoken.contains("mini roundabout") -> "mini roundabout"
                    spoken.contains("roundabout") -> "roundabout"
                    spoken.contains("school zone") -> "school zone"
                    spoken.contains("zebra crossing") -> "zebra crossing"
                    spoken.contains("give way") -> "give way"
                    spoken.contains("traffic lights") -> "traffic lights"
                    spoken.contains("speed camera") -> "speed camera"
                    spoken.contains("bus stop") -> "bus stop"
                    else -> spoken
                }
            }
        )
    }

    @Test
    fun alertsModeSpeaksRoundaboutAndSchoolOnly() {
        var nowMs = 0L
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALERTS },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { it.type.name },
            nowProvider = { nowMs }
        )

        listOf(
            PromptType.ROUNDABOUT,
            PromptType.MINI_ROUNDABOUT,
            PromptType.SCHOOL_ZONE,
            PromptType.ZEBRA_CROSSING,
            PromptType.GIVE_WAY,
            PromptType.TRAFFIC_SIGNAL,
            PromptType.SPEED_CAMERA,
            PromptType.BUS_STOP,
            PromptType.NO_ENTRY
        ).forEachIndexed { index, type ->
            nowMs = (index + 1) * 21_000L
            controller.enqueue(prompt(type, featureId = "alert_$index"))
            controller.onHazardSpeechCompleted()
        }

        assertEquals(
            listOf(
                "roundabout",
                "mini roundabout",
                "school zone",
                "speed camera",
                "no entry"
            ),
            output.spokenTexts.map { it.lowercase() }.map { spoken ->
                when {
                    spoken.contains("no entry") -> "no entry"
                    spoken.contains("mini roundabout") -> "mini roundabout"
                    spoken.contains("roundabout") -> "roundabout"
                    spoken.contains("school zone") -> "school zone"
                    spoken.contains("speed camera") -> "speed camera"
                    else -> spoken
                }
            }
        )
    }

    @Test
    fun alertsModeSpeechStillRespectsBudget() {
        var nowMs = 0L
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALERTS },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = {
                "This is an intentionally long spoken phrase that should be shortened by " +
                    "the speech budget enforcer before audio output."
            },
            nowProvider = { nowMs }
        )

        listOf(PromptType.ROUNDABOUT, PromptType.SCHOOL_ZONE).forEachIndexed { index, type ->
            nowMs = (index + 1) * 21_000L
            controller.enqueue(prompt(type, featureId = "budget_$index"))
            controller.onHazardSpeechCompleted()
        }

        assertTrue(output.spokenTexts.isNotEmpty())
        output.spokenTexts.forEach { spoken ->
            assertTrue(spoken.length <= 70)
            assertTrue(wordCount(spoken) <= 14)
        }
    }

    @Test
    fun muteModeSpeaksNone() {
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.MUTE },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = {
                "This very long phrase should never be spoken because mute mode blocks output."
            }
        )

        controller.enqueue(prompt(PromptType.ROUNDABOUT))
        controller.enqueue(prompt(PromptType.SCHOOL_ZONE, featureId = "school_2"))

        assertTrue(output.spokenTexts.isEmpty())
    }

    @Test
    fun suppressionNearManeuverBlocksSpeech() {
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALL },
            upcomingManeuverTimeSProvider = { 5.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { it.type.name }
        )

        controller.enqueue(prompt(PromptType.ROUNDABOUT))

        assertTrue(output.spokenTexts.isEmpty())
    }

    @Test
    fun miniRoundaboutSecondaryPromptBypassesVoiceCooldowns() {
        var nowMs = 0L
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALL },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { "${it.featureId}-${it.distanceM}" },
            nowProvider = { nowMs }
        )

        nowMs = 1_000L
        controller.enqueue(prompt(PromptType.MINI_ROUNDABOUT, featureId = "mini_1", distanceM = 120))
        controller.onHazardSpeechCompleted()

        nowMs = 11_000L // inside both overall (20s) and type (90s) cooldown windows
        controller.enqueue(prompt(PromptType.MINI_ROUNDABOUT, featureId = "mini_1", distanceM = 50))
        controller.onHazardSpeechCompleted()

        assertEquals(2, output.spokenTexts.size)
        assertTrue(output.spokenTexts[0].lowercase().contains("mini roundabout"))
        assertTrue(output.spokenTexts[1].lowercase().contains("mini roundabout"))
    }

    @Test
    fun closeMiniRoundaboutsAreNotDroppedByTypeCooldown() {
        var nowMs = 0L
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALL },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { "${it.featureId}-${it.distanceM}" },
            nowProvider = { nowMs }
        )

        nowMs = 1_000L
        controller.enqueue(prompt(PromptType.MINI_ROUNDABOUT, featureId = "mini_a", distanceM = 120))
        controller.onHazardSpeechCompleted()

        nowMs = 31_000L // below 90s per-type cooldown, above overall cooldown
        controller.enqueue(prompt(PromptType.MINI_ROUNDABOUT, featureId = "mini_b", distanceM = 120))
        controller.onHazardSpeechCompleted()

        assertEquals(2, output.spokenTexts.size)
        assertTrue(output.spokenTexts.all { it.lowercase().contains("mini roundabout") })
    }

    @Test
    fun voiceCooldownOfNinetySecondsPerTypeIsEnforced() {
        var nowMs = 0L
        val output = FakeVoiceOutput()
        val controller = HazardVoiceController(
            voiceModeProvider = { VoiceModeSetting.ALL },
            upcomingManeuverTimeSProvider = { 20.0 },
            isManeuverSpeechPlayingProvider = { false },
            voiceOutput = output,
            speechTextProvider = { it.type.name },
            nowProvider = { nowMs }
        )

        nowMs = 1_000L
        controller.enqueue(prompt(PromptType.ROUNDABOUT, featureId = "rb_1"))
        controller.onHazardSpeechCompleted()

        nowMs = 30_000L
        controller.enqueue(prompt(PromptType.ROUNDABOUT, featureId = "rb_2"))
        controller.onHazardSpeechCompleted()

        nowMs = 92_000L
        controller.enqueue(prompt(PromptType.ROUNDABOUT, featureId = "rb_3"))
        controller.onHazardSpeechCompleted()

        assertEquals(
            listOf("roundabout", "roundabout"),
            output.spokenTexts.map { spoken ->
                if (spoken.lowercase().contains("roundabout")) "roundabout" else spoken
            }
        )
    }

    private fun prompt(
        type: PromptType,
        featureId: String = "feature_1",
        distanceM: Int = 120
    ): PromptEvent {
        return PromptEvent(
            id = "$featureId:1",
            type = type,
            message = type.name,
            featureId = featureId,
            priority = 1,
            distanceM = distanceM,
            expiresAtEpochMs = 2_000L
        )
    }

    private class FakeVoiceOutput : VoiceOutput {
        val spokenTexts = mutableListOf<String>()
        var stopCount = 0

        override fun speak(text: String) {
            spokenTexts += text
        }

        override fun stop() {
            stopCount += 1
        }
    }

    private fun wordCount(text: String): Int {
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
