package com.drivest.navigation.theory.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Context.theoryProgressDataStore by preferencesDataStore(name = "drivest_theory_progress")

data class TopicStat(
    val attempts: Int,
    val correct: Int,
    val wrong: Int,
    val masteryPercent: Int
)

data class RouteTagSnapshot(
    val tags: List<String>,
    val centreId: String?,
    val routeId: String?,
    val recordedAtMs: Long
)

data class TheoryProgress(
    val completedLessons: Set<String>,
    val topicStats: Map<String, TopicStat>,
    val wrongQueue: Set<String>,
    val bookmarks: Set<String>,
    val streakDays: Int,
    val lastActiveAtMs: Long,
    val lastRouteTagSnapshot: RouteTagSnapshot?
)

class TheoryProgressStore(
    private val dataStore: DataStore<Preferences>,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    constructor(
        context: Context,
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ) : this(
        dataStore = context.theoryProgressDataStore,
        nowProvider = nowProvider
    )

    val progress: Flow<TheoryProgress> = dataStore.data.map { preferences ->
        TheoryProgress(
            completedLessons = decodeStringSet(preferences[KEY_COMPLETED_LESSONS_CSV]),
            topicStats = decodeTopicStats(preferences[KEY_TOPIC_STATS_JSON]),
            wrongQueue = decodeStringSet(preferences[KEY_WRONG_QUEUE_CSV]),
            bookmarks = decodeStringSet(preferences[KEY_BOOKMARKS_CSV]),
            streakDays = preferences[KEY_STREAK_DAYS] ?: 0,
            lastActiveAtMs = preferences[KEY_LAST_ACTIVE_MS] ?: 0L,
            lastRouteTagSnapshot = decodeRouteTagSnapshot(
                tagsRaw = preferences[KEY_LAST_ROUTE_TAGS_CSV],
                centreId = preferences[KEY_LAST_ROUTE_CENTRE_ID],
                routeId = preferences[KEY_LAST_ROUTE_ID],
                recordedAtMs = preferences[KEY_LAST_ROUTE_RECORDED_MS] ?: 0L
            )
        )
    }

    suspend fun markLessonCompleted(lessonId: String) {
        if (lessonId.isBlank()) return
        dataStore.edit { preferences ->
            val updated = decodeStringSet(preferences[KEY_COMPLETED_LESSONS_CSV]).toMutableSet()
            updated += lessonId
            preferences[KEY_COMPLETED_LESSONS_CSV] = encodeStringSet(updated)
            updateLastActive(preferences)
        }
    }

    suspend fun recordQuizAnswer(
        topicId: String,
        questionId: String,
        isCorrect: Boolean
    ) {
        if (topicId.isBlank() || questionId.isBlank()) return
        dataStore.edit { preferences ->
            val stats = decodeTopicStats(preferences[KEY_TOPIC_STATS_JSON]).toMutableMap()
            val current = stats[topicId] ?: TopicStat(
                attempts = 0,
                correct = 0,
                wrong = 0,
                masteryPercent = 0
            )
            val attempts = current.attempts + 1
            val correct = current.correct + if (isCorrect) 1 else 0
            val wrong = current.wrong + if (isCorrect) 0 else 1
            val mastery = ((correct.toFloat() / attempts.toFloat()) * 100f).toInt().coerceIn(0, 100)
            stats[topicId] = TopicStat(
                attempts = attempts,
                correct = correct,
                wrong = wrong,
                masteryPercent = mastery
            )
            preferences[KEY_TOPIC_STATS_JSON] = encodeTopicStats(stats)

            val wrongQueue = decodeStringSet(preferences[KEY_WRONG_QUEUE_CSV]).toMutableSet()
            if (isCorrect) {
                wrongQueue.remove(questionId)
            } else {
                wrongQueue.add(questionId)
            }
            preferences[KEY_WRONG_QUEUE_CSV] = encodeStringSet(wrongQueue)
            updateLastActive(preferences)
        }
    }

    suspend fun clearWrongQuestion(questionId: String) {
        if (questionId.isBlank()) return
        dataStore.edit { preferences ->
            val wrongQueue = decodeStringSet(preferences[KEY_WRONG_QUEUE_CSV]).toMutableSet()
            wrongQueue.remove(questionId)
            preferences[KEY_WRONG_QUEUE_CSV] = encodeStringSet(wrongQueue)
            updateLastActive(preferences)
        }
    }

    suspend fun toggleBookmark(questionId: String): Boolean {
        if (questionId.isBlank()) return false
        var bookmarked = false
        dataStore.edit { preferences ->
            val bookmarks = decodeStringSet(preferences[KEY_BOOKMARKS_CSV]).toMutableSet()
            bookmarked = if (bookmarks.contains(questionId)) {
                bookmarks.remove(questionId)
                false
            } else {
                bookmarks.add(questionId)
                true
            }
            preferences[KEY_BOOKMARKS_CSV] = encodeStringSet(bookmarks)
            updateLastActive(preferences)
        }
        return bookmarked
    }

    suspend fun setBookmark(questionId: String, bookmarked: Boolean) {
        if (questionId.isBlank()) return
        dataStore.edit { preferences ->
            val bookmarks = decodeStringSet(preferences[KEY_BOOKMARKS_CSV]).toMutableSet()
            if (bookmarked) {
                bookmarks.add(questionId)
            } else {
                bookmarks.remove(questionId)
            }
            preferences[KEY_BOOKMARKS_CSV] = encodeStringSet(bookmarks)
            updateLastActive(preferences)
        }
    }

    suspend fun touch() {
        dataStore.edit { preferences ->
            updateLastActive(preferences)
        }
    }

    suspend fun recordLastRouteTagSnapshot(
        tags: Collection<String>,
        centreId: String?,
        routeId: String?,
        nowMs: Long = nowProvider()
    ) {
        val normalizedTags = tags.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        dataStore.edit { preferences ->
            preferences[KEY_LAST_ROUTE_TAGS_CSV] = encodeStringSet(normalizedTags.toSet())
            if (centreId.isNullOrBlank()) {
                preferences.remove(KEY_LAST_ROUTE_CENTRE_ID)
            } else {
                preferences[KEY_LAST_ROUTE_CENTRE_ID] = centreId.trim()
            }
            if (routeId.isNullOrBlank()) {
                preferences.remove(KEY_LAST_ROUTE_ID)
            } else {
                preferences[KEY_LAST_ROUTE_ID] = routeId.trim()
            }
            preferences[KEY_LAST_ROUTE_RECORDED_MS] = nowMs
            updateLastActive(preferences, nowMs = nowMs)
        }
    }

    private fun updateLastActive(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        nowMs: Long = nowProvider()
    ) {
        val previousLastActive = preferences[KEY_LAST_ACTIVE_MS] ?: 0L
        val previousDay = if (previousLastActive > 0L) {
            TimeUnit.MILLISECONDS.toDays(previousLastActive)
        } else {
            Long.MIN_VALUE
        }
        val currentDay = TimeUnit.MILLISECONDS.toDays(nowMs)
        val previousStreak = preferences[KEY_STREAK_DAYS] ?: 0
        val nextStreak = when {
            previousDay == Long.MIN_VALUE -> 1
            currentDay == previousDay -> previousStreak.coerceAtLeast(1)
            currentDay == previousDay + 1L -> previousStreak.coerceAtLeast(1) + 1
            else -> 1
        }
        preferences[KEY_LAST_ACTIVE_MS] = nowMs
        preferences[KEY_STREAK_DAYS] = nextStreak
    }

    private fun decodeRouteTagSnapshot(
        tagsRaw: String?,
        centreId: String?,
        routeId: String?,
        recordedAtMs: Long
    ): RouteTagSnapshot? {
        val tags = decodeStringSet(tagsRaw).toList()
        if (tags.isEmpty() || recordedAtMs <= 0L) {
            return null
        }
        return RouteTagSnapshot(
            tags = tags,
            centreId = centreId,
            routeId = routeId,
            recordedAtMs = recordedAtMs
        )
    }

    private fun decodeTopicStats(raw: String?): Map<String, TopicStat> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val topicId = keys.next().orEmpty()
                    if (topicId.isBlank()) continue
                    val statJson = root.optJSONObject(topicId) ?: continue
                    put(
                        topicId,
                        TopicStat(
                            attempts = statJson.optInt("attempts", 0).coerceAtLeast(0),
                            correct = statJson.optInt("correct", 0).coerceAtLeast(0),
                            wrong = statJson.optInt("wrong", 0).coerceAtLeast(0),
                            masteryPercent = statJson.optInt("masteryPercent", 0).coerceIn(0, 100)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeTopicStats(stats: Map<String, TopicStat>): String {
        val root = JSONObject()
        stats.forEach { (topicId, stat) ->
            root.put(
                topicId,
                JSONObject().apply {
                    put("attempts", stat.attempts)
                    put("correct", stat.correct)
                    put("wrong", stat.wrong)
                    put("masteryPercent", stat.masteryPercent)
                }
            )
        }
        return root.toString()
    }

    private fun decodeStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val value = array.optString(index, "").trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeStringSet(values: Set<String>): String {
        val array = JSONArray()
        values.sorted().forEach { array.put(it) }
        return array.toString()
    }

    private companion object {
        val KEY_COMPLETED_LESSONS_CSV = stringPreferencesKey("theory_completed_lessons_csv")
        val KEY_TOPIC_STATS_JSON = stringPreferencesKey("theory_topic_stats_json")
        val KEY_WRONG_QUEUE_CSV = stringPreferencesKey("theory_wrong_queue_csv")
        val KEY_BOOKMARKS_CSV = stringPreferencesKey("theory_bookmarks_csv")
        val KEY_STREAK_DAYS = intPreferencesKey("theory_streak_days")
        val KEY_LAST_ACTIVE_MS = longPreferencesKey("theory_last_active_ms")
        val KEY_LAST_ROUTE_TAGS_CSV = stringPreferencesKey("theory_last_route_tags_csv")
        val KEY_LAST_ROUTE_CENTRE_ID = stringPreferencesKey("theory_last_route_centre_id")
        val KEY_LAST_ROUTE_ID = stringPreferencesKey("theory_last_route_id")
        val KEY_LAST_ROUTE_RECORDED_MS = longPreferencesKey("theory_last_route_recorded_ms")
    }
}
