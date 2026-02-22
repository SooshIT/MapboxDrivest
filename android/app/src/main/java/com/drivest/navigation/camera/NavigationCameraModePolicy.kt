package com.drivest.navigation.camera

enum class NavigationCameraMode {
    FOLLOW,
    OVERVIEW
}

object NavigationCameraModePolicy {

    fun onOverviewButtonPressed(
        currentMode: NavigationCameraMode,
        hasRouteForOverview: Boolean
    ): NavigationCameraMode {
        return when {
            currentMode == NavigationCameraMode.OVERVIEW -> NavigationCameraMode.FOLLOW
            hasRouteForOverview -> NavigationCameraMode.OVERVIEW
            else -> NavigationCameraMode.FOLLOW
        }
    }

    fun onUserMapGesture(
        currentMode: NavigationCameraMode,
        hasRouteForOverview: Boolean
    ): NavigationCameraMode {
        if (!hasRouteForOverview) return currentMode
        return NavigationCameraMode.OVERVIEW
    }

    fun onSessionStart(): NavigationCameraMode = NavigationCameraMode.FOLLOW

    fun shouldApplyLocationCameraUpdate(
        mode: NavigationCameraMode,
        force: Boolean
    ): Boolean {
        return force || mode == NavigationCameraMode.FOLLOW
    }
}
