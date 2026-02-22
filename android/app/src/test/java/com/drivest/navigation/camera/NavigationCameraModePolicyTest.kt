package com.drivest.navigation.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraModePolicyTest {

    @Test
    fun overviewButtonSwitchesFollowToOverviewWhenRouteExists() {
        val next = NavigationCameraModePolicy.onOverviewButtonPressed(
            currentMode = NavigationCameraMode.FOLLOW,
            hasRouteForOverview = true
        )
        assertEquals(NavigationCameraMode.OVERVIEW, next)
    }

    @Test
    fun overviewButtonSwitchesOverviewBackToFollow() {
        val next = NavigationCameraModePolicy.onOverviewButtonPressed(
            currentMode = NavigationCameraMode.OVERVIEW,
            hasRouteForOverview = true
        )
        assertEquals(NavigationCameraMode.FOLLOW, next)
    }

    @Test
    fun overviewButtonStaysFollowWhenNoRouteExists() {
        val next = NavigationCameraModePolicy.onOverviewButtonPressed(
            currentMode = NavigationCameraMode.FOLLOW,
            hasRouteForOverview = false
        )
        assertEquals(NavigationCameraMode.FOLLOW, next)
    }

    @Test
    fun userGestureMovesCameraIntoOverviewOnlyWhenRouteExists() {
        val noRoute = NavigationCameraModePolicy.onUserMapGesture(
            currentMode = NavigationCameraMode.FOLLOW,
            hasRouteForOverview = false
        )
        val withRoute = NavigationCameraModePolicy.onUserMapGesture(
            currentMode = NavigationCameraMode.FOLLOW,
            hasRouteForOverview = true
        )
        assertEquals(NavigationCameraMode.FOLLOW, noRoute)
        assertEquals(NavigationCameraMode.OVERVIEW, withRoute)
    }

    @Test
    fun locationCameraUpdatesBlockedInOverviewUnlessForced() {
        assertFalse(
            NavigationCameraModePolicy.shouldApplyLocationCameraUpdate(
                mode = NavigationCameraMode.OVERVIEW,
                force = false
            )
        )
        assertTrue(
            NavigationCameraModePolicy.shouldApplyLocationCameraUpdate(
                mode = NavigationCameraMode.OVERVIEW,
                force = true
            )
        )
        assertTrue(
            NavigationCameraModePolicy.shouldApplyLocationCameraUpdate(
                mode = NavigationCameraMode.FOLLOW,
                force = false
            )
        )
    }

    @Test
    fun sessionStartAlwaysReturnsFollow() {
        assertEquals(NavigationCameraMode.FOLLOW, NavigationCameraModePolicy.onSessionStart())
    }
}
