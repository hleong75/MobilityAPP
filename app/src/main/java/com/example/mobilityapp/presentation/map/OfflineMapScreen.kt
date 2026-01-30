package com.example.mobilityapp.presentation.map

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobilityapp.R
import com.example.mobilityapp.data.GraphHopperManager
import com.example.mobilityapp.domain.model.RouteCoordinate
import com.example.mobilityapp.presentation.components.LoadingStep
import com.example.mobilityapp.presentation.components.SteppedProgressScreen
import com.example.mobilityapp.presentation.components.PersistentBottomSheet
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.io.File

private const val MBTILES_FILE_NAME = "city_map.mbtiles"
private const val TILEJSON_VERSION = "2.2.0"
private const val TILE_SIZE_PX = 256
private const val OFFLINE_SOURCE_ID = "offline-raster"
private const val OFFLINE_LAYER_ID = "offline-layer"
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_BORDER_ID = "route-line-border"
private const val ROUTE_LAYER_ID = "route-line"
private const val EMPTY_ROUTE_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val ROUTE_BOUNDS_PADDING_DP = 50f

@Composable
fun OfflineMapScreen(mapViewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val mbtilesFile = remember(context) { resolveMbtiles(context) }
    val isGraphReady by GraphHopperManager.isReady.collectAsState()
    val graphError by mapViewModel.graphError.collectAsState()
    val forceUpdateInProgress by mapViewModel.forceUpdateInProgress.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        mapViewModel.initializeGraph(context.applicationContext)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isGraphReady) {
            LoadingScreen(graphError)
        } else if (mbtilesFile == null) {
            PlaceholderMap()
        } else {
            PersistentBottomSheet(
                onSearchQuery = { query ->
                    // TODO: Handle search query
                }
            ) {
                OfflineMapView(mbtilesFile, mapViewModel)
            }
        }
        if (isGraphReady) {
            TextButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                onClick = { showSettings = true },
                enabled = !forceUpdateInProgress
            ) {
                Text(text = stringResource(R.string.settings_title))
            }
        }
    }
    if (showSettings) {
        SettingsDialog(
            forceUpdateInProgress = forceUpdateInProgress,
            onDismiss = { showSettings = false },
            onForceUpdate = { mapViewModel.forceUpdateGraph(context.applicationContext) }
        )
    }
}

@Composable
private fun LoadingScreen(errorMessage: String?) {
    if (errorMessage != null) {
        // Show error in a simple box
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        // Show stepped progress
        val steps = remember {
            listOf(
                LoadingStep("Chargement de la carte", isCompleted = true),
                LoadingStep("Analyse des données de transport", isCompleted = false),
                LoadingStep("Construction du réseau", isCompleted = false),
                LoadingStep("Optimisation des trajets", isCompleted = false)
            )
        }
        
        SteppedProgressScreen(steps = steps)
    }
}

@Composable
private fun SettingsDialog(
    forceUpdateInProgress: Boolean,
    onDismiss: () -> Unit,
    onForceUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_title)) },
        text = { Text(text = stringResource(R.string.graphhopper_force_update_description)) },
        confirmButton = {
            Button(
                onClick = onForceUpdate,
                enabled = !forceUpdateInProgress
            ) {
                Text(
                    text = if (forceUpdateInProgress) {
                        stringResource(R.string.graphhopper_force_update_progress)
                    } else {
                        stringResource(R.string.graphhopper_force_update)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !forceUpdateInProgress) {
                Text(text = stringResource(R.string.settings_close))
            }
        }
    )
}

@Composable
private fun PlaceholderMap() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.offline_map_placeholder),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OfflineMapView(mbtilesFile: File, mapViewModel: MapViewModel) {
    val mapView = rememberMapViewWithLifecycle()
    val currentFile by rememberUpdatedState(mbtilesFile)
    val routeGeoJson by mapViewModel.routeGeoJson.collectAsState()
    val routeCoordinates by mapViewModel.routeCoordinates.collectAsState()
    LaunchedEffect(mapView, currentFile) {
        val fileUri = Uri.fromFile(currentFile)
        val tileSet = TileSet(TILEJSON_VERSION, fileUri.toString())
        val rasterSource = RasterSource(OFFLINE_SOURCE_ID, tileSet, TILE_SIZE_PX)
        val rasterLayer = RasterLayer(OFFLINE_LAYER_ID, OFFLINE_SOURCE_ID)
        mapView.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(
                Style.Builder().apply {
                    withSource(rasterSource)
                    withLayer(rasterLayer)
                    withSource(GeoJsonSource(ROUTE_SOURCE_ID, EMPTY_ROUTE_GEOJSON))
                    withLayerAbove(
                        LineLayer(ROUTE_LAYER_BORDER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#0A1F3D"),
                            lineWidth(8f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND)
                        ),
                        OFFLINE_LAYER_ID
                    )
                    withLayerAbove(
                        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#1C3F7A"),
                            lineWidth(5f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND)
                        ),
                        ROUTE_LAYER_BORDER_ID
                    )
                }
            )
        }
    }
    LaunchedEffect(mapView, routeGeoJson, routeCoordinates) {
        mapView.getMapAsync { mapboxMap ->
            val geoJson = routeGeoJson ?: EMPTY_ROUTE_GEOJSON
            mapboxMap.getStyle { style ->
                style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(geoJson)
            }
            if (routeCoordinates.isNotEmpty()) {
                zoomToRoute(mapView, routeCoordinates)
            }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

private fun resolveMbtiles(context: Context): File? {
    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val defaultAppDir = context.getExternalFilesDir(null)
    val candidates = listOfNotNull(downloadsDir, defaultAppDir)
    return candidates
        .map { File(it, MBTILES_FILE_NAME) }
        .firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }
    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun zoomToRoute(mapView: MapView, coordinates: List<RouteCoordinate>) {
    val bounds = buildBounds(coordinates) ?: return
    val paddingPx = mapView.resources.displayMetrics.density * ROUTE_BOUNDS_PADDING_DP
    mapView.getMapAsync { mapboxMap ->
        mapboxMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                paddingPx.toInt()
            )
        )
    }
}

private fun buildBounds(coordinates: List<RouteCoordinate>): LatLngBounds? {
    if (coordinates.isEmpty() || coordinates.size < 2) return null
    var minLat = Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    coordinates.forEach { coordinate ->
        minLat = minOf(minLat, coordinate.latitude)
        maxLat = maxOf(maxLat, coordinate.latitude)
        minLon = minOf(minLon, coordinate.longitude)
        maxLon = maxOf(maxLon, coordinate.longitude)
    }
    val southwest = LatLng(minLat, minLon)
    val northeast = LatLng(maxLat, maxLon)
    return LatLngBounds.Builder()
        .include(southwest)
        .include(northeast)
        .build()
}
