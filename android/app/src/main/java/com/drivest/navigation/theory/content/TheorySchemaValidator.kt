package com.drivest.navigation.theory.content

import com.drivest.navigation.theory.models.TheoryPack

data class TheorySchemaValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object TheorySchemaValidator {

    // Imported Drivest question bank currently groups content into 10 broad topics.
    private const val MIN_TOPIC_COUNT = 10
    private const val MIN_LESSON_COUNT = 24
    private const val MIN_QUESTION_COUNT = 240

    fun validate(pack: TheoryPack): TheorySchemaValidationResult {
        val errors = mutableListOf<String>()
        if (pack.version.isBlank()) errors += "Missing pack version."
        if (pack.updatedAt.isBlank()) errors += "Missing pack updatedAt."
        if (pack.topics.size < MIN_TOPIC_COUNT) {
            errors += "Expected at least $MIN_TOPIC_COUNT topics."
        }
        if (pack.lessons.size < MIN_LESSON_COUNT) {
            errors += "Expected at least $MIN_LESSON_COUNT lessons."
        }
        if (pack.questions.size < MIN_QUESTION_COUNT) {
            errors += "Expected at least $MIN_QUESTION_COUNT questions."
        }

        val topicIds = pack.topics.map { it.id }.toSet()
        val lessonIds = pack.lessons.map { it.id }.toSet()
        val questionIds = pack.questions.map { it.id }.toSet()
        val signIds = pack.signs.map { it.id }.toSet()

        if (topicIds.size != pack.topics.size) errors += "Duplicate topic ids found."
        if (lessonIds.size != pack.lessons.size) errors += "Duplicate lesson ids found."
        if (questionIds.size != pack.questions.size) errors += "Duplicate question ids found."
        if (signIds.size != pack.signs.size) errors += "Duplicate sign ids found."

        pack.topics.forEach { topic ->
            if (topic.id.isBlank()) errors += "Topic id is blank."
            if (topic.title.isBlank()) errors += "Topic title is blank for ${topic.id}."
            topic.lessonIds.forEach { lessonId ->
                if (lessonId !in lessonIds) {
                    errors += "Topic ${topic.id} references unknown lesson $lessonId."
                }
            }
            topic.questionIds.forEach { questionId ->
                if (questionId !in questionIds) {
                    errors += "Topic ${topic.id} references unknown question $questionId."
                }
            }
        }

        pack.lessons.forEach { lesson ->
            if (lesson.id.isBlank()) errors += "Lesson id is blank."
            if (lesson.topicId !in topicIds) {
                errors += "Lesson ${lesson.id} references unknown topic ${lesson.topicId}."
            }
            lesson.signIds.forEach { signId ->
                if (signId !in signIds) {
                    errors += "Lesson ${lesson.id} references unknown sign $signId."
                }
            }
        }

        pack.signs.forEach { sign ->
            if (sign.id.isBlank()) errors += "Sign id is blank."
            if (sign.topicId !in topicIds) {
                errors += "Sign ${sign.id} references unknown topic ${sign.topicId}."
            }
        }

        pack.questions.forEach { question ->
            if (question.id.isBlank()) errors += "Question id is blank."
            if (question.topicId !in topicIds) {
                errors += "Question ${question.id} references unknown topic ${question.topicId}."
            }
            if (question.options.size < 2) {
                errors += "Question ${question.id} has less than 2 options."
            }
            val optionIds = question.options.map { it.id }.toSet()
            if (optionIds.size != question.options.size) {
                errors += "Question ${question.id} has duplicate option ids."
            }
            if (question.correctOptionId !in optionIds) {
                errors += "Question ${question.id} has unknown correctOptionId."
            }
        }

        return TheorySchemaValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
