package com.drivest.navigation.osm

import java.util.Calendar

/**
 * Determines whether a bus lane is currently restricted to buses only, based on OSM
 * conditional access tags (e.g. "access:conditional = no @ (Mo-Fr 07:00-10:00,16:00-19:00)").
 *
 * Returns:
 *   true  → restricted right now (learner must not enter)
 *   false → open to all traffic right now
 *   null  → no conditional data found in tags (safe default: treat as restricted)
 */
object BusLaneAccessChecker {

    private val CONDITIONAL_KEYS = listOf(
        "access:conditional",
        "vehicle:conditional",
        "motor_vehicle:conditional"
    )

    private val TIME_RANGE_REGEX = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")

    private val DAY_RANGE_REGEX = Regex("""(Mo|Tu|We|Th|Fr|Sa|Su)(?:-(Mo|Tu|We|Th|Fr|Sa|Su))?""")

    private val DAY_MAP = mapOf(
        "Mo" to Calendar.MONDAY,
        "Tu" to Calendar.TUESDAY,
        "We" to Calendar.WEDNESDAY,
        "Th" to Calendar.THURSDAY,
        "Fr" to Calendar.FRIDAY,
        "Sa" to Calendar.SATURDAY,
        "Su" to Calendar.SUNDAY
    )

    /**
     * Returns true if the bus lane is restricted now, false if open, null if no data.
     * Caller should treat null as restricted (safe default for learner drivers).
     */
    fun isRestrictedNow(tags: Map<String, String>, nowMs: Long): Boolean? {
        var foundConditionalTag = false
        for (key in CONDITIONAL_KEYS) {
            val value = tags[key] ?: continue
            foundConditionalTag = true
            val result = parseConditional(value, nowMs)
            if (result != null) return result
        }
        // No conditional tag found at all
        return if (foundConditionalTag) false else null
    }

    /**
     * Parses an OSM conditional string like:
     *   "no @ (Mo-Fr 07:00-10:00,16:00-19:00)"
     *   "no @ (07:00-19:00); yes @ ()"
     * Returns true if any "no" condition is active now, false if conditions parsed but none active, null if unparseable.
     */
    private fun parseConditional(conditional: String, nowMs: Long): Boolean? {
        val parts = conditional.split(";")
        var anyParsed = false
        for (part in parts) {
            val trimmed = part.trim()
            val atIdx = trimmed.indexOf(" @ ")
            if (atIdx < 0) continue
            val restrictionValue = trimmed.substring(0, atIdx).trim().lowercase()
            if (restrictionValue != "no") continue
            val condition = trimmed.substring(atIdx + 3)
                .trim()
                .removePrefix("(")
                .removeSuffix(")")
                .trim()
            if (condition.isEmpty()) continue
            anyParsed = true
            if (isActiveNow(condition, nowMs)) return true
        }
        return if (anyParsed) false else null
    }

    /**
     * Checks whether a condition string like "Mo-Fr 07:00-10:00,16:00-19:00" applies right now.
     */
    private fun isActiveNow(condition: String, nowMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // Check day-of-week applicability
        val dayMatch = DAY_RANGE_REGEX.find(condition)
        val dayApplies = if (dayMatch != null) {
            val startDay = DAY_MAP[dayMatch.groupValues[1]] ?: return false
            val endDay = if (dayMatch.groupValues[2].isNotEmpty()) {
                DAY_MAP[dayMatch.groupValues[2]] ?: startDay
            } else {
                startDay
            }
            isDayInRange(currentDay, startDay, endDay)
        } else {
            true // no day qualifier → applies every day
        }
        if (!dayApplies) return false

        // Check time ranges — multiple can be comma-separated
        val timeRanges = TIME_RANGE_REGEX.findAll(condition)
        for (match in timeRanges) {
            val startM = match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
            val endM = match.groupValues[3].toInt() * 60 + match.groupValues[4].toInt()
            if (currentMinutes in startM until endM) return true
        }
        return false
    }

    /**
     * Handles both normal ranges (Mo-Fr = Mon..Fri) and wrap-around (Fr-Mo).
     * Calendar.SUNDAY=1, MONDAY=2, ..., SATURDAY=7.
     */
    private fun isDayInRange(currentDay: Int, startDay: Int, endDay: Int): Boolean {
        return if (startDay <= endDay) {
            currentDay in startDay..endDay
        } else {
            // Wraps around week boundary
            currentDay >= startDay || currentDay <= endDay
        }
    }
}
