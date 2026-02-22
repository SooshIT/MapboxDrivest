package com.drivest.navigation.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PracticeOffRouteDebugState(
    val rawDistanceM: Double?,
    val smoothedDistanceM: Double?,
    val offRouteState: String
)

object PracticeOffRouteDebugStore {
    private val _state = MutableStateFlow(
        PracticeOffRouteDebugState(
            rawDistanceM = null,
            smoothedDistanceM = null,
            offRouteState = "INACTIVE"
        )
    )

    val state: StateFlow<PracticeOffRouteDebugState> = _state.asStateFlow()

    fun update(
        rawDistanceM: Double?,
        smoothedDistanceM: Double?,
        offRouteState: String
    ) {
        _state.value = PracticeOffRouteDebugState(
            rawDistanceM = rawDistanceM,
            smoothedDistanceM = smoothedDistanceM,
            offRouteState = offRouteState
        )
    }

    fun reset() {
        update(
            rawDistanceM = null,
            smoothedDistanceM = null,
            offRouteState = "INACTIVE"
        )
    }
}
