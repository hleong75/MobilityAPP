package com.example.mobilityapp.presentation.map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilityapp.data.GraphHopperInitializer
import com.example.mobilityapp.data.GraphHopperManager
import com.example.mobilityapp.data.RoutingRepository
import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.RouteCoordinate
import com.example.mobilityapp.domain.model.TravelMode
import com.example.mobilityapp.presentation.components.LoadingStep
import kotlinx.coroutines.Dispatchers
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

    private val _graphError = MutableStateFlow<String?>(null)
    val graphError: StateFlow<String?> = _graphError.asStateFlow()

    private val _forceUpdateInProgress = MutableStateFlow(false)
    val forceUpdateInProgress: StateFlow<Boolean> = _forceUpdateInProgress.asStateFlow()

    private val _loadingSteps = MutableStateFlow<List<LoadingStep>>(
        listOf(
            LoadingStep("Chargement de la carte", isCompleted = false),
            LoadingStep("Analyse des données de transport", isCompleted = false),
            LoadingStep("Construction du réseau", isCompleted = false),
            LoadingStep("Optimisation des trajets", isCompleted = false)
        )
    )
    val loadingSteps: StateFlow<List<LoadingStep>> = _loadingSteps.asStateFlow()

    fun initializeGraph(context: Context) {
        if (GraphHopperManager.isReady.value) {
            return
        }
        _graphError.value = null
        // Update step 1: Loading map
        updateLoadingStep(0, true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update step 2: Analyzing transport data
                updateLoadingStep(1, true)
                GraphHopperInitializer.start(context)
                // Update step 3: Building network
                updateLoadingStep(2, true)
                // Update step 4: Optimizing routes
                updateLoadingStep(3, true)
            } catch (e: Exception) {
                Log.e("GH_DEBUG", "CRASH", e)
                _graphError.value = e.message?.let { "Erreur: $it" } ?: "Erreur: Import GraphHopper"
            }
        }
    }

    private fun updateLoadingStep(index: Int, isCompleted: Boolean) {
        val currentSteps = _loadingSteps.value.toMutableList()
        if (index in currentSteps.indices) {
            currentSteps[index] = currentSteps[index].copy(isCompleted = isCompleted)
            _loadingSteps.value = currentSteps
        }
    }

    fun forceUpdateGraph(context: Context) {
        _graphError.value = null
        _forceUpdateInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                GraphHopperInitializer.forceRebuild(context)
            } catch (e: Exception) {
                Log.e("GH_DEBUG", "CRASH", e)
                _graphError.value = e.message?.let { "Erreur: $it" } ?: "Erreur: Import GraphHopper"
            } finally {
                _forceUpdateInProgress.value = false
            }
        }
    }

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
