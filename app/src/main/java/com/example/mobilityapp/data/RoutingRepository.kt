package com.example.mobilityapp.data

import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.TravelMode
import java.util.Date

class RoutingRepository {
    fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        time: Date,
        mode: TravelMode
    ): Itinerary? {
        return GraphHopperManager.route(startLat, startLon, endLat, endLon, time, mode)
    }
}
