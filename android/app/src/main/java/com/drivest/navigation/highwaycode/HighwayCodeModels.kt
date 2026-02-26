package com.drivest.navigation.highwaycode

data class HighwayCodePack(
    val categories: List<HighwayCodeCategory>,
    val questions: List<HighwayCodeQuestion>,
    val sourceReferences: List<String>
)

data class HighwayCodeCategory(
    val id: String,
    val name: String,
    val iconEmoji: String = "",
    val questionCount: Int = 0
)

data class HighwayCodeQuestion(
    val id: String,
    val categoryId: String,
    val difficulty: String,
    val prompt: String,
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
    val sourceHint: String
)
