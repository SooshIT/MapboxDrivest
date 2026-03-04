package com.drivest.navigation.parking

internal object ParkingOverpassQueryBuilder {

    fun parkingAround(
        lat: Double,
        lon: Double,
        radiusMeters: Int
    ): String {
        val clauses = buildClauses(lat, lon, radiusMeters)
        return """
            [out:json][timeout:20];
            (
              $clauses
            );
            out body center;
        """.trimIndent()
    }

    private fun buildClauses(lat: Double, lon: Double, radiusMeters: Int): String {
        val targets = listOf(
            "['amenity'='parking']",
            "['amenity'='parking_space']",
            "['parking'='street_side']",
            "['parking'='surface']",
            "['parking'='underground']",
            "['parking'='multi-storey']"
        )
        return targets.joinToString(separator = "\n") { filter ->
            val around = "around:$radiusMeters,$lat,$lon"
            """
                nwr($around)$filter['access'!='private'];
                nwr($around)$filter[!access];
            """.trimIndent()
        }
    }
}
