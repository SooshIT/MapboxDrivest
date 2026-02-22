package com.drivest.navigation.theory

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TheoryContentPackUniquenessTest {

    @Test
    fun questionPromptsAndIdsAreUnique() {
        val root = loadPackJson()
        val questions = root.getJSONArray("questions")
        val ids = mutableSetOf<String>()
        val prompts = mutableSetOf<String>()

        for (index in 0 until questions.length()) {
            val question = questions.getJSONObject(index)
            ids += question.getString("id")
            prompts += question.getString("prompt")
        }

        assertEquals("Question IDs should be unique", questions.length(), ids.size)
        assertEquals("Question prompts should be unique", questions.length(), prompts.size)
    }

    @Test
    fun eachTopicHasTwentyDistinctPrompts() {
        val root = loadPackJson()
        val topics = root.getJSONArray("topics")
        val questions = root.getJSONArray("questions")

        for (topicIndex in 0 until topics.length()) {
            val topicId = topics.getJSONObject(topicIndex).getString("id")
            val topicPrompts = mutableSetOf<String>()
            var topicQuestionCount = 0
            for (questionIndex in 0 until questions.length()) {
                val question = questions.getJSONObject(questionIndex)
                if (question.getString("topicId") == topicId) {
                    topicQuestionCount += 1
                    topicPrompts += question.getString("prompt")
                }
            }

            assertEquals("Each topic should currently ship with 20 questions", 20, topicQuestionCount)
            assertEquals("Topic $topicId should have 20 distinct prompts", 20, topicPrompts.size)
        }
    }

    private fun loadPackJson(): JSONObject {
        val file = File("src/main/assets/theory/theory_pack_v1.json")
        assertTrue("Theory pack file must exist at ${file.path}", file.exists())
        val text = file.readText(Charsets.UTF_8)
        return JSONObject(text)
    }
}
