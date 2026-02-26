package com.drivest.navigation.prompts

enum class PromptType {
    ROUNDABOUT,
    MINI_ROUNDABOUT,
    SCHOOL_ZONE,
    ZEBRA_CROSSING,
    GIVE_WAY,
    TRAFFIC_SIGNAL,
    SPEED_CAMERA,
    BUS_LANE,
    BUS_STOP,
    NO_ENTRY
}

data class PromptEvent(
    val id: String,
    val type: PromptType,
    val message: String,
    val featureId: String,
    val priority: Int,
    val distanceM: Int,
    val expiresAtEpochMs: Long,
    val confidenceHint: Float = 1f,
    /** Non-null only for BUS_LANE prompts. true = restricted now, false = open to all traffic. */
    val busLaneRestricted: Boolean? = null
)
