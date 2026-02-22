package com.drivest.navigation.prompts

import java.util.Locale

class SpeechBudgetEnforcer(
    private val maxChars: Int = MAX_CHARS,
    private val maxWords: Int = MAX_WORDS
) {

    fun enforce(
        text: String,
        promptType: PromptType,
        distanceMeters: Int?
    ): String {
        val keyword = keywordFor(promptType)
        val distancePhrase = distancePhrase(distanceMeters)
        val actionHint = actionHint(promptType)
        val normalizedInput = normalize(text)

        val candidates = buildList {
            if (normalizedInput.isNotBlank()) {
                add(normalizedInput)
            }
            add(compose(keyword, distancePhrase, actionHint))
            add(compose(keyword, distancePhrase, null))
            add(compose(keyword, null, null))
        }.distinct()

        candidates.forEach { candidate ->
            val prepared = normalize(candidate)
            if (!containsKeyword(prepared, keyword)) return@forEach
            if (withinBudget(prepared)) return ensureTerminalPunctuation(prepared)
        }

        val fallback = compose(keyword, distancePhrase, actionHint)
        return hardTrimWithSuffix(fallback)
    }

    private fun compose(
        keyword: String,
        distancePhrase: String?,
        actionHint: String?
    ): String {
        val firstClause = when (distancePhrase) {
            null -> keyword
            "ahead" -> "$keyword ahead"
            "now" -> "$keyword now"
            else -> "$keyword $distancePhrase"
        }
        return if (actionHint.isNullOrBlank()) {
            "$firstClause."
        } else {
            "$firstClause. $actionHint"
        }
    }

    private fun hardTrimWithSuffix(text: String): String {
        var shortened = trimWords(normalize(text), maxWords)
        if (shortened.length > maxChars) {
            val allowedPrefix = (maxChars - TRUNCATION_SUFFIX.length).coerceAtLeast(0)
            shortened = shortened
                .take(allowedPrefix)
                .trimEnd()
                .let {
                    if (it.isEmpty()) TRUNCATION_SUFFIX.take(maxChars) else it + TRUNCATION_SUFFIX
                }
        }
        if (wordCount(shortened) > maxWords) {
            shortened = trimWords(shortened, maxWords)
        }
        if (shortened.length > maxChars) {
            shortened = shortened.take(maxChars).trimEnd()
        }
        return ensureTerminalPunctuation(shortened)
    }

    private fun withinBudget(text: String): Boolean {
        return text.length <= maxChars && wordCount(text) <= maxWords
    }

    private fun wordCount(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
    }

    private fun trimWords(text: String, maxAllowedWords: Int): String {
        if (text.isBlank()) return text
        val words = text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        return words.take(maxAllowedWords).joinToString(" ")
    }

    private fun normalize(text: String): String {
        return text
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        return text.lowercase(Locale.US).contains(keyword.lowercase(Locale.US))
    }

    private fun ensureTerminalPunctuation(text: String): String {
        if (text.isBlank()) return text
        if (text.last() == '.' || text.last() == '!' || text.last() == '?') return text
        if (text.length + 1 > maxChars) return text
        return "$text."
    }

    private fun keywordFor(type: PromptType): String {
        return when (type) {
            PromptType.ROUNDABOUT -> "Roundabout"
            PromptType.MINI_ROUNDABOUT -> "Mini roundabout"
            PromptType.SCHOOL_ZONE -> "School zone"
            PromptType.ZEBRA_CROSSING -> "Zebra crossing"
            PromptType.GIVE_WAY -> "Give way"
            PromptType.TRAFFIC_SIGNAL -> "Traffic lights"
            PromptType.SPEED_CAMERA -> "Speed camera"
            PromptType.BUS_LANE -> "Bus lane"
            PromptType.BUS_STOP -> "Bus stop"
            PromptType.NO_ENTRY -> "No entry"
        }
    }

    private fun actionHint(type: PromptType): String {
        return when (type) {
            PromptType.ROUNDABOUT -> "Prepare early."
            PromptType.MINI_ROUNDABOUT -> "Slow now."
            PromptType.SCHOOL_ZONE -> "Slow down."
            PromptType.ZEBRA_CROSSING -> "Watch for pedestrians."
            PromptType.GIVE_WAY -> "Yield."
            PromptType.TRAFFIC_SIGNAL -> "Prepare to stop."
            PromptType.SPEED_CAMERA -> "Check speed."
            PromptType.BUS_LANE -> "Follow lane signs."
            PromptType.BUS_STOP -> "Watch for buses."
            PromptType.NO_ENTRY -> "Rerouting now."
        }
    }

    private fun distancePhrase(distanceMeters: Int?): String {
        if (distanceMeters == null || distanceMeters <= 0) return "ahead"
        if (distanceMeters <= 80) return "now"
        val rounded = ((distanceMeters + 9) / 10) * 10
        return "in $rounded meters"
    }

    companion object {
        const val MAX_CHARS = 70
        const val MAX_WORDS = 14

        private const val TRUNCATION_SUFFIX = "..."
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
