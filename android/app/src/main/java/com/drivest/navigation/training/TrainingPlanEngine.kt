package com.drivest.navigation.training

import com.drivest.navigation.intelligence.RouteDifficultyLabel
import com.drivest.navigation.practice.PracticeRoute
import com.drivest.navigation.profile.DriverProfile
import java.time.LocalDate

data class TrainingDayRecommendation(
    val dayIndex: Int,
    val routeId: String,
    val routeName: String,
    val difficulty: RouteDifficultyLabel
)

data class TrainingPlan(
    val centreId: String,
    val generatedAt: LocalDate,
    val days: List<TrainingDayRecommendation>
)

class TrainingPlanEngine {

    fun generatePlan(
        centreId: String,
        routes: List<PracticeRoute>,
        profile: DriverProfile
    ): TrainingPlan {
        if (routes.isEmpty()) {
            return TrainingPlan(centreId = centreId, generatedAt = LocalDate.now(), days = emptyList())
        }

        val bucketed = routes.groupBy { inferDifficulty(it) }
        val easy = bucketed[RouteDifficultyLabel.EASY].orEmpty()
        val medium = bucketed[RouteDifficultyLabel.MEDIUM].orEmpty()
        val hard = bucketed[RouteDifficultyLabel.HARD].orEmpty()

        val difficultyPattern = when {
            profile.confidenceScore < 40 -> listOf(
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.EASY
            )
            profile.confidenceScore < 70 -> listOf(
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.HARD,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.EASY
            )
            else -> listOf(
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.HARD,
                RouteDifficultyLabel.EASY,
                RouteDifficultyLabel.HARD,
                RouteDifficultyLabel.MEDIUM,
                RouteDifficultyLabel.HARD,
                RouteDifficultyLabel.MEDIUM
            )
        }

        val cursors = mutableMapOf<RouteDifficultyLabel, Int>()
        fun pick(difficulty: RouteDifficultyLabel): PracticeRoute {
            val fallbackList = when {
                routes.isEmpty() -> emptyList()
                else -> routes
            }
            val pool = when (difficulty) {
                RouteDifficultyLabel.EASY -> easy
                RouteDifficultyLabel.MEDIUM -> medium
                RouteDifficultyLabel.HARD -> hard
            }.ifEmpty { fallbackList }
            if (pool.isEmpty()) return routes.first()
            val cursor = cursors.getOrDefault(difficulty, 0)
            cursors[difficulty] = cursor + 1
            return pool[cursor % pool.size]
        }

        val dayPlans = difficultyPattern.mapIndexed { index, difficulty ->
            val route = pick(difficulty)
            TrainingDayRecommendation(
                dayIndex = index,
                routeId = route.id,
                routeName = route.name,
                difficulty = inferDifficulty(route)
            )
        }
        return TrainingPlan(
            centreId = centreId,
            generatedAt = LocalDate.now(),
            days = dayPlans
        )
    }

    fun recommendedToday(plan: TrainingPlan, date: LocalDate = LocalDate.now()): TrainingDayRecommendation? {
        if (plan.days.isEmpty()) return null
        val index = Math.floorMod(date.dayOfYear, plan.days.size)
        return plan.days[index]
    }

    private fun inferDifficulty(route: PracticeRoute): RouteDifficultyLabel {
        route.intelligence?.let { return it.difficultyLabel }
        return when {
            route.distanceM < 7000 -> RouteDifficultyLabel.EASY
            route.distanceM < 11000 -> RouteDifficultyLabel.MEDIUM
            else -> RouteDifficultyLabel.HARD
        }
    }
}

