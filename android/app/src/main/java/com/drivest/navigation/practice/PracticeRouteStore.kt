package com.drivest.navigation.practice

interface PracticeRouteStore {
    suspend fun loadRoutesForCentre(centreId: String): List<PracticeRoute>
}
