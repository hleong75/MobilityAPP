package com.example.mobilityapp.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilityapp.data.RoutingRepository
import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.RouteCoordinate
import com.example.mobilityapp.domain.model.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

class MapViewModel(
    private val routingRepository: RoutingRepository = RoutingRepository()
) : ViewModel() {
    private val _routeGeoJson = MutableStateFlow<String?>(null)
    val routeGeoJson: StateFlow<String?> = _routeGeoJson.asStateFlow()

    private val _routeCoordinates = MutableStateFlow<List<RouteCoordinate>>(emptyList())
    val routeCoordinates: StateFlow<List<RouteCoordinate>> = _routeCoordinates.asStateFlow()

    fun requestRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        time: Date = Date(),
        mode: TravelMode = TravelMode.FOOT
    ) {
        viewModelScope.launch {
            val itinerary = kotlin.runCatching {
                routingRepository.calculateRoute(startLat, startLon, endLat, endLon, time, mode)
            }.getOrNull()
            updateRoute(itinerary)
        }
    }

    private fun updateRoute(itinerary: Itinerary?) {
        val coordinates = itinerary?.routeCoordinates.orEmpty()
        _routeCoordinates.value = coordinates
        _routeGeoJson.value = buildFeatureCollection(coordinates)
    }

    private fun buildFeatureCollection(coordinates: List<RouteCoordinate>): String? {
        if (coordinates.isEmpty()) {
            return null
        }
        val coordinateArray = coordinates.joinToString(separator = ",") { coordinate ->
            "[${coordinate.longitude},${coordinate.latitude}]"
        }
        return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[${coordinateArray}]}}]}"""
    }
}
