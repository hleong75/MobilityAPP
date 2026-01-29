package com.example.mobilityapp.data

import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class RoutingRepository {
    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        time: Date,
        mode: TravelMode
    ): Itinerary? {
        return withContext(Dispatchers.IO) {
            GraphHopperManager.route(startLat, startLon, endLat, endLon, time, mode)
        }
    }
}
