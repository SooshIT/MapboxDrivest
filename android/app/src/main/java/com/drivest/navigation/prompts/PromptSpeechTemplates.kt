package com.drivest.navigation.prompts

import android.content.Context
import com.drivest.navigation.R

object PromptSpeechTemplates {

    fun textFor(
        context: Context,
        prompt: PromptEvent,
        speedMps: Double,
        distanceM: Int = prompt.distanceM
    ): String {
        val speedKph = (speedMps * 3.6).coerceAtLeast(0.0)
        val near = distanceM <= 80
        val fast = speedKph >= 45.0
        val miniRoundaboutNear = distanceM <= 50
        return when (prompt.type) {
            PromptType.ROUNDABOUT -> {
                when {
                    near -> context.getString(R.string.prompt_speech_roundabout_now)
                    fast -> context.getString(R.string.prompt_speech_roundabout_slow)
                    else -> context.getString(R.string.prompt_speech_roundabout)
                }
            }
            PromptType.MINI_ROUNDABOUT -> {
                if (miniRoundaboutNear) {
                    context.getString(R.string.prompt_speech_mini_roundabout_now)
                } else {
                    context.getString(R.string.prompt_speech_mini_roundabout)
                }
            }
            PromptType.SCHOOL_ZONE -> {
                when {
                    near -> context.getString(R.string.prompt_speech_school_zone_now)
                    fast -> context.getString(R.string.prompt_speech_school_zone_slow)
                    else -> context.getString(R.string.prompt_speech_school_zone)
                }
            }
            PromptType.ZEBRA_CROSSING -> {
                when {
                    near -> context.getString(R.string.prompt_speech_zebra_crossing_now)
                    fast -> context.getString(R.string.prompt_speech_zebra_crossing_slow)
                    else -> context.getString(R.string.prompt_speech_zebra_crossing)
                }
            }
            PromptType.GIVE_WAY -> {
                when {
                    near -> context.getString(R.string.prompt_speech_give_way_now)
                    fast -> context.getString(R.string.prompt_speech_give_way_slow)
                    else -> context.getString(R.string.prompt_speech_give_way)
                }
            }
            PromptType.TRAFFIC_SIGNAL -> {
                when {
                    near -> context.getString(R.string.prompt_speech_traffic_signal_now)
                    fast -> context.getString(R.string.prompt_speech_traffic_signal_slow)
                    else -> context.getString(R.string.prompt_speech_traffic_signal)
                }
            }
            PromptType.SPEED_CAMERA -> {
                context.getString(R.string.prompt_speech_speed_camera)
            }
            PromptType.BUS_LANE -> context.getString(R.string.prompt_speech_bus_lane)
            PromptType.BUS_STOP -> context.getString(R.string.prompt_speech_bus_stop)
            PromptType.NO_ENTRY -> context.getString(R.string.prompt_speech_no_entry)
        }
    }
}
