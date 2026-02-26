package com.drivest.navigation.trafficsigns

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TrafficSignsRepository(private val context: Context) {

    suspend fun loadPack(): TrafficSignsPack {
        cachedPack?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedPack?.let { return@withContext it }
            val json = context.assets.open(PACK_ASSET_PATH).bufferedReader().use { it.readText() }
            val pack = parsePack(JSONObject(json))
            cachedPack = pack
            pack
        }
    }

    private fun parsePack(root: JSONObject): TrafficSignsPack {
        val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
        val signsArray = root.optJSONArray("signs") ?: JSONArray()
        val sourcesArray = root.optJSONArray("sourceReferences") ?: JSONArray()

        val categories = buildList {
            for (index in 0 until categoriesArray.length()) {
                val item = categoriesArray.optJSONObject(index) ?: continue
                add(
                    TrafficSignCategory(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        signCount = item.optInt("signCount")
                    )
                )
            }
        }

        val signs = buildList {
            for (index in 0 until signsArray.length()) {
                val item = signsArray.optJSONObject(index) ?: continue
                add(
                    TrafficSign(
                        id = item.optString("id"),
                        code = item.optString("code"),
                        caption = item.optString("caption"),
                        description = item.optString("description"),
                        officialCategory = item.optString("officialCategory"),
                        officialCategories = item.optJSONArray("officialCategories").toStringList(),
                        primaryCategoryId = item.optString("primaryCategoryId"),
                        categoryIds = item.optJSONArray("categoryIds").toStringList(),
                        shape = item.optString("shape"),
                        backgroundColor = item.optString("backgroundColor"),
                        borderColor = item.optString("borderColor"),
                        textHint = item.optString("textHint"),
                        symbol1 = item.optString("symbol1"),
                        symbol2 = item.optString("symbol2"),
                        imageAssetPath = item.optString("imageAssetPath")
                    )
                )
            }
        }

        val sourceReferences = buildList {
            for (index in 0 until sourcesArray.length()) {
                val value = sourcesArray.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }

        return TrafficSignsPack(
            categories = categories,
            signs = signs,
            sourceReferences = sourceReferences
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private companion object {
        const val PACK_ASSET_PATH = "traffic_signs/traffic_signs_pack_v1.json"

        @Volatile
        private var cachedPack: TrafficSignsPack? = null
    }
}
