package com.drivest.navigation.theory

import com.drivest.navigation.theory.content.TheoryReadinessCalculator
import com.drivest.navigation.theory.content.TheoryReadinessLabel
import com.drivest.navigation.theory.storage.TheoryProgress
import com.drivest.navigation.theory.storage.TopicStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TheoryReadinessScoreTest {

    @Test
    fun lowReadinessReturnsBuilding() {
        val progress = TheoryProgress(
            completedLessons = emptySet(),
            topicStats = mapOf(
                "roundabouts" to TopicStat(attempts = 5, correct = 2, wrong = 3, masteryPercent = 40)
            ),
            wrongQueue = setOf("q1", "q2"),
            bookmarks = emptySet(),
            streakDays = 0,
            lastActiveAtMs = 0L,
            lastRouteTagSnapshot = null
        )
        val readiness = TheoryReadinessCalculator.calculate(progress, totalTopics = 12)
        assertEquals(TheoryReadinessLabel.BUILDING, readiness.label)
        assertTrue(readiness.score < 40)
    }

    @Test
    fun highReadinessReturnsReady() {
        val topicStats = (1..12).associate { index ->
            "topic_$index" to TopicStat(attempts = 20, correct = 18, wrong = 2, masteryPercent = 90)
        }
        val progress = TheoryProgress(
            completedLessons = setOf("l1", "l2"),
            topicStats = topicStats,
            wrongQueue = emptySet(),
            bookmarks = emptySet(),
            streakDays = 5,
            lastActiveAtMs = 1L,
            lastRouteTagSnapshot = null
        )
        val readiness = TheoryReadinessCalculator.calculate(progress, totalTopics = 12)
        assertEquals(TheoryReadinessLabel.READY, readiness.label)
        assertTrue(readiness.score >= 75)
    }
}
