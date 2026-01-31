package com.example.mobilityapp.presentation.map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobilityapp.R
import com.example.mobilityapp.data.GraphHopperInitializer
import com.example.mobilityapp.data.InitializationState
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

    private val _loadingSteps = MutableStateFlow<List<LoadingStep>>(emptyList())
    val loadingSteps: StateFlow<List<LoadingStep>> = _loadingSteps.asStateFlow()

    private val _initializationState =
        MutableStateFlow<InitializationState>(InitializationState.NeedsImport)
    val initializationState: StateFlow<InitializationState> = _initializationState.asStateFlow()

    private val _osmLastModified = MutableStateFlow<Long?>(null)
    val osmLastModified: StateFlow<Long?> = _osmLastModified.asStateFlow()

    fun initializeGraph(context: Context) {
        if (GraphHopperManager.isReady.value) {
            return
        }
        _graphError.value = null
        
        // Initialize loading steps with context strings
        _loadingSteps.value = listOf(
            LoadingStep(context.getString(R.string.loading_step_map), isCompleted = false),
            LoadingStep(context.getString(R.string.loading_step_transport), isCompleted = false),
            LoadingStep(context.getString(R.string.loading_step_network), isCompleted = false),
            LoadingStep(context.getString(R.string.loading_step_optimize), isCompleted = false)
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update step 1: Loading map (initial step)
                updateLoadingStep(0, true)
                GraphHopperInitializer.start(context).collect { state ->
                    _initializationState.value = state
                    when (state) {
                        is InitializationState.MissingFiles -> {
                            _osmLastModified.value = null
                        }
                        InitializationState.Ready -> {
                            updateLoadingStep(1, true)
                            updateLoadingStep(2, true)
                            updateLoadingStep(3, true)
                            _osmLastModified.value = GraphHopperInitializer.getOsmLastModified(context)
                        }
                        is InitializationState.Error -> {
                            _graphError.value = state.message
                        }
                        InitializationState.NeedsImport,
                        InitializationState.Importing -> {
                            // Progress steps already shown; no additional UI updates needed here.
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GH_DEBUG", "CRASH", e)
                _graphError.value = e.message?.let { "Erreur: $it" } ?: GraphHopperInitializer.DEFAULT_ERROR_MESSAGE
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
        val missingFiles = GraphHopperInitializer.getMissingFiles(context)
        if (missingFiles.isNotEmpty()) {
            _graphError.value = "$MISSING_FILES_PREFIX ${missingFiles.joinToString(", ")}"
            return
        }
        _forceUpdateInProgress.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                GraphHopperInitializer.forceRebuild(context)
            } catch (e: Exception) {
                Log.e("GH_DEBUG", "CRASH", e)
                _graphError.value = e.message?.let { "Erreur: $it" } ?: GraphHopperInitializer.DEFAULT_ERROR_MESSAGE
            } finally {
                _forceUpdateInProgress.value = false
            }
        }
    }

    fun scanDownloads(context: Context) {
        _graphError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val copiedAny = GraphHopperInitializer.copyMissingFilesFromDownloads(context)
            if (!copiedAny) {
                val missingFiles = GraphHopperInitializer.getMissingFiles(context)
                if (missingFiles.isNotEmpty()) {
                    _graphError.value = "$MISSING_FILES_PREFIX ${missingFiles.joinToString(", ")}"
                }
            }
            initializeGraph(context)
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

    companion object {
        private const val MISSING_FILES_PREFIX = "Fichiers manquants:"
    }
}
