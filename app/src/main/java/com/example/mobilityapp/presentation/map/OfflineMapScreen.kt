package com.example.mobilityapp.presentation.map

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobilityapp.R
import com.example.mobilityapp.data.GraphHopperManager
import com.example.mobilityapp.domain.model.RouteCoordinate
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
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
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
private const val TRANSPORT_SOURCE_ID = "transport-source"
private const val TRANSPORT_LAYER_ID = "transport-layer"
private const val TRANSPORT_ICON_BUS = "transport-bus"
private const val TRANSPORT_ICON_TRAM = "transport-tram"
private const val TRANSPORT_ICON_TRAIN = "transport-train"
private const val EMPTY_ROUTE_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val EMPTY_TRANSPORT_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val ROUTE_BOUNDS_PADDING_DP = 50f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OfflineMapScreen(mapViewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val mbtilesFile = remember(context) { resolveMbtiles(context) }
    val isGraphReady by GraphHopperManager.isReady.collectAsState()
    val graphError by mapViewModel.graphError.collectAsState()
    val forceUpdateInProgress by mapViewModel.forceUpdateInProgress.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    LaunchedEffect(Unit) {
        sheetState.show()
        mapViewModel.initializeGraph(context.applicationContext)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isGraphReady) {
            LoadingScreen(graphError)
        } else if (mbtilesFile == null) {
            PlaceholderMap()
        } else {
            OfflineMapView(mbtilesFile, mapViewModel)
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
        MapSearchBottomSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            sheetState = sheetState
        )
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (errorMessage == null) {
                LoadingChecklist()
            }
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = errorMessage ?: stringResource(R.string.graphhopper_loading_message),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun LoadingChecklist() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LoadingStep(
            label = stringResource(R.string.loading_step_index),
            isChecked = true
        )
        LoadingStep(
            label = stringResource(R.string.loading_step_network),
            isChecked = false
        )
        LoadingStep(
            label = stringResource(R.string.loading_step_finalize),
            isChecked = false
        )
    }
}

@Composable
private fun LoadingStep(label: String, isChecked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isChecked) "✓" else "○",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isChecked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MapSearchBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = { },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search_bar_icon_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_bar_title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = stringResource(R.string.search_bar_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
                    withSource(GeoJsonSource(TRANSPORT_SOURCE_ID, EMPTY_TRANSPORT_GEOJSON))
                    withLayerAbove(
                        LineLayer(ROUTE_LAYER_BORDER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#1c3f7a"),
                            lineWidth(8f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND)
                        ),
                        OFFLINE_LAYER_ID
                    )
                    withLayerAbove(
                        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#2b74ff"),
                            lineWidth(5f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND)
                        ),
                        ROUTE_LAYER_BORDER_ID
                    )
                    withLayerAbove(
                        SymbolLayer(TRANSPORT_LAYER_ID, TRANSPORT_SOURCE_ID).withProperties(
                            iconImage("{icon}"),
                            iconSize(1.0f),
                            textFont(arrayOf("sans-serif")),
                            textField("{name}")
                        ),
                        ROUTE_LAYER_ID
                    )
                }
            )
            mapboxMap.getStyle { style ->
                androidx.core.content.ContextCompat.getDrawable(
                    mapView.context,
                    R.drawable.ic_transport_bus
                )?.let { style.addImage(TRANSPORT_ICON_BUS, it) }
                androidx.core.content.ContextCompat.getDrawable(
                    mapView.context,
                    R.drawable.ic_transport_tram
                )?.let { style.addImage(TRANSPORT_ICON_TRAM, it) }
                androidx.core.content.ContextCompat.getDrawable(
                    mapView.context,
                    R.drawable.ic_transport_train
                )?.let { style.addImage(TRANSPORT_ICON_TRAIN, it) }
            }
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
