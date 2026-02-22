package com.drivest.navigation.theory.content

import android.content.Context
import android.util.Log
import com.drivest.navigation.theory.models.TheoryAnswerOption
import com.drivest.navigation.theory.models.TheoryLesson
import com.drivest.navigation.theory.models.TheoryPack
import com.drivest.navigation.theory.models.TheoryQuestion
import com.drivest.navigation.theory.models.TheorySign
import com.drivest.navigation.theory.models.TheoryTopic
import org.json.JSONArray
import org.json.JSONObject

class TheoryPackLoader(
    private val context: Context,
    private val assetPath: String = THEORY_PACK_ASSET_PATH
) {
    fun load(): TheoryPack {
        sharedCachedPack?.let { return it }
        val parsed = runCatching {
            context.assets.open(assetPath).bufferedReader().use { reader ->
                parsePack(reader.readText())
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read theory pack: ${error.message}")
            TheoryPack.empty()
        }

        val validation = TheorySchemaValidator.validate(parsed)
        if (!validation.isValid) {
            Log.w(TAG, "Theory pack validation failed: ${validation.errors.joinToString("; ")}")
            return TheoryPack.empty()
        }

        sharedCachedPack = parsed
        return parsed
    }

    fun resetCache() {
        sharedCachedPack = null
    }

    private fun parsePack(json: String): TheoryPack {
        val root = JSONObject(json)
        return TheoryPack(
            version = root.optString("version", ""),
            updatedAt = root.optString("updatedAt", ""),
            topics = parseTopics(root.optJSONArray("topics")),
            lessons = parseLessons(root.optJSONArray("lessons")),
            signs = parseSigns(root.optJSONArray("signs")),
            questions = parseQuestions(root.optJSONArray("questions"))
        )
    }

    private fun parseTopics(topicsArray: JSONArray?): List<TheoryTopic> {
        if (topicsArray == null) return emptyList()
        return buildList {
            for (index in 0 until topicsArray.length()) {
                val item = topicsArray.optJSONObject(index) ?: continue
                add(
                    TheoryTopic(
                        id = item.optString("id", ""),
                        title = item.optString("title", ""),
                        description = item.optString("description", ""),
                        tags = parseStringList(item.optJSONArray("tags")),
                        lessonIds = parseStringList(item.optJSONArray("lessonIds")),
                        questionIds = parseStringList(item.optJSONArray("questionIds"))
                    )
                )
            }
        }
    }

    private fun parseLessons(lessonsArray: JSONArray?): List<TheoryLesson> {
        if (lessonsArray == null) return emptyList()
        return buildList {
            for (index in 0 until lessonsArray.length()) {
                val item = lessonsArray.optJSONObject(index) ?: continue
                add(
                    TheoryLesson(
                        id = item.optString("id", ""),
                        topicId = item.optString("topicId", ""),
                        title = item.optString("title", ""),
                        content = item.optString("content", ""),
                        keyPoints = parseStringList(item.optJSONArray("keyPoints")),
                        signIds = parseStringList(item.optJSONArray("signIds"))
                    )
                )
            }
        }
    }

    private fun parseSigns(signsArray: JSONArray?): List<TheorySign> {
        if (signsArray == null) return emptyList()
        return buildList {
            for (index in 0 until signsArray.length()) {
                val item = signsArray.optJSONObject(index) ?: continue
                add(
                    TheorySign(
                        id = item.optString("id", ""),
                        topicId = item.optString("topicId", ""),
                        name = item.optString("name", ""),
                        meaning = item.optString("meaning", ""),
                        memoryHint = item.optString("memoryHint", "")
                    )
                )
            }
        }
    }

    private fun parseQuestions(questionsArray: JSONArray?): List<TheoryQuestion> {
        if (questionsArray == null) return emptyList()
        return buildList {
            for (index in 0 until questionsArray.length()) {
                val item = questionsArray.optJSONObject(index) ?: continue
                add(
                    TheoryQuestion(
                        id = item.optString("id", ""),
                        topicId = item.optString("topicId", ""),
                        prompt = item.optString("prompt", ""),
                        options = parseAnswerOptions(item.optJSONArray("options")),
                        correctOptionId = item.optString("correctOptionId", ""),
                        explanation = item.optString("explanation", ""),
                        difficulty = item.optString("difficulty", "easy")
                    )
                )
            }
        }
    }

    private fun parseAnswerOptions(optionsArray: JSONArray?): List<TheoryAnswerOption> {
        if (optionsArray == null) return emptyList()
        return buildList {
            for (index in 0 until optionsArray.length()) {
                val item = optionsArray.optJSONObject(index) ?: continue
                add(
                    TheoryAnswerOption(
                        id = item.optString("id", ""),
                        text = item.optString("text", "")
                    )
                )
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index, "").trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    companion object {
        const val THEORY_PACK_ASSET_PATH = "theory/theory_pack_v1.json"
        private const val TAG = "TheoryPackLoader"
        @Volatile
        private var sharedCachedPack: TheoryPack? = null
    }
}
