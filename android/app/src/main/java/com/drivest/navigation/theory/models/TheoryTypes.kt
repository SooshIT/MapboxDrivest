package com.drivest.navigation.theory.models

data class TheoryPack(
    val version: String,
    val updatedAt: String,
    val topics: List<TheoryTopic>,
    val lessons: List<TheoryLesson>,
    val signs: List<TheorySign>,
    val questions: List<TheoryQuestion>
) {
    companion object {
        fun empty(): TheoryPack {
            return TheoryPack(
                version = "empty",
                updatedAt = "",
                topics = emptyList(),
                lessons = emptyList(),
                signs = emptyList(),
                questions = emptyList()
            )
        }
    }
}

data class TheoryTopic(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val lessonIds: List<String>,
    val questionIds: List<String>
)

data class TheoryLesson(
    val id: String,
    val topicId: String,
    val title: String,
    val content: String,
    val keyPoints: List<String>,
    val signIds: List<String>
)

data class TheorySign(
    val id: String,
    val topicId: String,
    val name: String,
    val meaning: String,
    val memoryHint: String
)

data class TheoryQuestion(
    val id: String,
    val topicId: String,
    val prompt: String,
    val options: List<TheoryAnswerOption>,
    val correctOptionId: String,
    val explanation: String,
    val difficulty: String
)

data class TheoryAnswerOption(
    val id: String,
    val text: String
)
