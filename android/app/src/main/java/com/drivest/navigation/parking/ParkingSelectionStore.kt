package com.drivest.navigation.parking

import com.mapbox.geojson.Point

object ParkingSelectionStore {

    data class Selection(
        val spotId: String,
        val spotLat: Double,
        val spotLng: Double,
        val spotTitle: String,
        val finalDestinationLat: Double?,
        val finalDestinationLng: Double?,
        val finalDestinationName: String?
    )

    @Volatile
    private var selection: Selection? = null

    fun setSelection(
        spot: ParkingSpot,
        finalDestination: Point?,
        finalDestinationName: String?
    ) {
        selection = Selection(
            spotId = spot.id,
            spotLat = spot.lat,
            spotLng = spot.lng,
            spotTitle = spot.title,
            finalDestinationLat = finalDestination?.latitude(),
            finalDestinationLng = finalDestination?.longitude(),
            finalDestinationName = finalDestinationName
        )
    }

    fun consumeSelection(): Selection? {
        val current = selection
        selection = null
        return current
    }

    fun peekSelection(): Selection? = selection
}
