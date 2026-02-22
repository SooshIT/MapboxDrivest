package com.drivest.navigation.prompts

import com.drivest.navigation.settings.VoiceModeSetting
import java.util.ArrayDeque

class HazardVoiceController(
    private val voiceModeProvider: () -> VoiceModeSetting,
    private val upcomingManeuverTimeSProvider: () -> Double?,
    private val isManeuverSpeechPlayingProvider: () -> Boolean,
    private val voiceOutput: VoiceOutput,
    private val speechTextProvider: (PromptEvent) -> String,
    private val speechBudgetEnforcer: SpeechBudgetEnforcer = SpeechBudgetEnforcer(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val queue = ArrayDeque<PromptEvent>()
    private var isHazardSpeaking = false
    private val lastSpokenAtByType = mutableMapOf<PromptType, Long>()
    private var lastSpokenAtMs: Long? = null

    fun enqueue(promptEvent: PromptEvent) {
        val nowMs = nowProvider()
        if (!canSpeakForMode(promptEvent.type)) return
        val upcomingManeuverTimeS = upcomingManeuverTimeSProvider()
        if (upcomingManeuverTimeS != null && upcomingManeuverTimeS < MANEUVER_SUPPRESSION_TIME_S) return
        if (isManeuverSpeechPlayingProvider()) return
        if (!usesPerFeatureDistanceGating(promptEvent.type)) {
            val lastByType = lastSpokenAtByType[promptEvent.type]
            if (lastByType != null && nowMs - lastByType < TYPE_VOICE_COOLDOWN_MS) return
            val lastSpokenAt = lastSpokenAtMs
            if (lastSpokenAt != null && nowMs - lastSpokenAt < OVERALL_VOICE_COOLDOWN_MS) return
        }

        queue.addLast(promptEvent)
        maybeSpeakNext()
    }

    fun clear() {
        queue.clear()
    }

    fun stopSpeaking() {
        isHazardSpeaking = false
        voiceOutput.stop()
    }

    fun onManeuverInstructionArrived() {
        if (isHazardSpeaking) {
            stopSpeaking()
        }
        clear()
    }

    fun onHazardSpeechCompleted() {
        isHazardSpeaking = false
        maybeSpeakNext()
    }

    private fun maybeSpeakNext() {
        if (isHazardSpeaking) return
        if (isManeuverSpeechPlayingProvider()) return
        if (queue.isEmpty()) return
        val next = queue.removeFirst()
        isHazardSpeaking = true
        val nowMs = nowProvider()
        lastSpokenAtByType[next.type] = nowMs
        lastSpokenAtMs = nowMs
        val budgetedText = speechBudgetEnforcer.enforce(
            text = speechTextProvider(next),
            promptType = next.type,
            distanceMeters = next.distanceM
        )
        voiceOutput.speak(budgetedText)
    }

    private fun canSpeakForMode(type: PromptType): Boolean {
        return when (voiceModeProvider()) {
            VoiceModeSetting.MUTE -> false
            VoiceModeSetting.ALERTS -> {
                type == PromptType.NO_ENTRY ||
                type == PromptType.ROUNDABOUT ||
                    type == PromptType.MINI_ROUNDABOUT ||
                    type == PromptType.SCHOOL_ZONE ||
                    type == PromptType.SPEED_CAMERA
            }
            VoiceModeSetting.ALL -> {
                type == PromptType.NO_ENTRY ||
                type == PromptType.ROUNDABOUT ||
                    type == PromptType.MINI_ROUNDABOUT ||
                    type == PromptType.SCHOOL_ZONE ||
                    type == PromptType.ZEBRA_CROSSING ||
                    type == PromptType.GIVE_WAY ||
                    type == PromptType.TRAFFIC_SIGNAL ||
                    type == PromptType.SPEED_CAMERA ||
                    type == PromptType.BUS_STOP
            }
        }
    }

    private fun usesPerFeatureDistanceGating(type: PromptType): Boolean {
        return type == PromptType.BUS_STOP ||
            type == PromptType.TRAFFIC_SIGNAL ||
            type == PromptType.SPEED_CAMERA ||
            type == PromptType.MINI_ROUNDABOUT ||
            type == PromptType.NO_ENTRY
    }

    private companion object {
        const val MANEUVER_SUPPRESSION_TIME_S = 6.0
        const val TYPE_VOICE_COOLDOWN_MS = 90_000L
        const val OVERALL_VOICE_COOLDOWN_MS = 20_000L
    }
}
