package com.drivest.navigation.parking

import android.content.Context
import com.drivest.navigation.osm.OverpassClient
import com.drivest.navigation.osm.OverpassParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ParkingService(
    context: Context,
    private val overpassClient: OverpassClient = OverpassClient(),
    private val cache: ParkingCache = ParkingCache(context)
) {

    suspend fun fetchParkingSpots(
        destinationLat: Double,
        destinationLng: Double,
        radiusMeters: Int = DEFAULT_RADIUS_METERS,
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): List<ParkingSpot> = withContext(Dispatchers.Default) {
        val cacheKey = buildCacheKey(destinationLat, destinationLng, radiusMeters, maxResults)
        val cached = cache.get(cacheKey)
        if (cached != null) {
            return@withContext enrichDistance(cached, destinationLat, destinationLng)
                .sortedWith(ParkingSort.defaultComparator())
                .take(maxResults)
        }

        val query = ParkingOverpassQueryBuilder.parkingAround(
            lat = destinationLat,
            lon = destinationLng,
            radiusMeters = radiusMeters
        )

        val records = try {
            val response = overpassClient.fetch(query)
            val elements = OverpassParser.parse(response)
            ParkingSpotMapper.map(elements)
                .distinctBy { it.id }
                .also { cache.put(cacheKey, it) }
        } catch (_: Exception) {
            cached ?: emptyList()
        }

        enrichDistance(records, destinationLat, destinationLng)
            .sortedWith(ParkingSort.defaultComparator())
            .take(maxResults)
    }

    private fun enrichDistance(
        records: List<ParkingSpotRecord>,
        destinationLat: Double,
        destinationLng: Double
    ): List<ParkingSpot> {
        return records.map { record ->
            val distanceMeters = haversineDistanceMeters(
                destinationLat,
                destinationLng,
                record.lat,
                record.lng
            ).roundToInt().coerceAtLeast(0)
            ParkingSpot(
                id = record.id,
                title = record.title,
                lat = record.lat,
                lng = record.lng,
                distanceMetersToDestination = distanceMeters,
                source = record.source,
                feeFlag = record.feeFlag,
                rulesSummary = record.rulesSummary,
                confidenceScore = record.confidenceScore,
                isAccessible = record.isAccessible
            )
        }
    }

    private fun buildCacheKey(
        destinationLat: Double,
        destinationLng: Double,
        radiusMeters: Int,
        maxResults: Int
    ): String {
        val latBucket = "%.3f".format(Locale.US, destinationLat)
        val lngBucket = "%.3f".format(Locale.US, destinationLng)
        return "parking:$latBucket,$lngBucket:r=$radiusMeters:max=$maxResults"
    }

    private fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val radius = 6371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(radLat1) * cos(radLat2)
        val c = 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return radius * c
    }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 600
        const val DEFAULT_MAX_RESULTS = 30
    }
}

object ParkingSort {
    fun defaultComparator(): Comparator<ParkingSpot> {
        return compareBy<ParkingSpot> { spot ->
            when (spot.feeFlag) {
                ParkingFeeFlag.LIKELY_FREE -> 0
                ParkingFeeFlag.LIKELY_PAID -> 1
                ParkingFeeFlag.UNKNOWN -> 2
            }
        }.thenBy { it.distanceMetersToDestination }
            .thenByDescending { it.confidenceScore }
    }
}
