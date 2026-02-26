package com.drivest.navigation.highwaycode

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HighwayCodeRepository(private val context: Context) {

    suspend fun loadPack(): HighwayCodePack {
        cachedPack?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedPack?.let { return@withContext it }
            val pack = runCatching {
                val json = context.assets.open(PACK_ASSET_PATH).bufferedReader().use { it.readText() }
                parsePack(JSONObject(json))
            }.getOrElse {
                buildDefaultHighwayCodePack()
            }
            cachedPack = pack
            pack
        }
    }

    private fun parsePack(root: JSONObject): HighwayCodePack {
        val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
        val questionsArray = root.optJSONArray("questions") ?: JSONArray()
        val sourcesArray = root.optJSONArray("sourceReferences") ?: JSONArray()

        val categories = buildList {
            for (index in 0 until categoriesArray.length()) {
                val item = categoriesArray.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isBlank() || name.isBlank()) continue
                add(
                    HighwayCodeCategory(
                        id = id,
                        name = name,
                        iconEmoji = item.optString("iconEmoji"),
                        questionCount = item.optInt("questionCount", 0).coerceAtLeast(0)
                    )
                )
            }
        }

        val questions = buildList {
            for (index in 0 until questionsArray.length()) {
                val item = questionsArray.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val categoryId = item.optString("categoryId").trim()
                val prompt = item.optString("prompt").trim()
                val question = item.optString("question").trim()
                val options = item.optJSONArray("options").toStringList()
                val answerIndex = item.optInt("answerIndex", -1)
                if (id.isBlank() || categoryId.isBlank() || prompt.isBlank() || question.isBlank()) continue
                if (options.size < 2) continue
                if (answerIndex !in options.indices) continue
                add(
                    HighwayCodeQuestion(
                        id = id,
                        categoryId = categoryId,
                        difficulty = item.optString("difficulty").ifBlank { "easy" },
                        prompt = prompt,
                        question = question,
                        options = options,
                        answerIndex = answerIndex,
                        explanation = item.optString("explanation").trim(),
                        sourceHint = item.optString("sourceHint").trim()
                    )
                )
            }
        }

        val sourceReferences = buildList {
            for (index in 0 until sourcesArray.length()) {
                val value = sourcesArray.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }

        return HighwayCodePack(
            categories = categories,
            questions = questions,
            sourceReferences = sourceReferences
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private companion object {
        const val PACK_ASSET_PATH = "highway_code/highway_code_pack_v1.json"

        @Volatile
        private var cachedPack: HighwayCodePack? = null
    }
}
