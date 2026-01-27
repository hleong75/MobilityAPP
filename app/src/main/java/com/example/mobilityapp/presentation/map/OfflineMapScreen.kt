package com.example.mobilityapp.presentation.map

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.io.File

@Composable
fun OfflineMapScreen() {
    val context = LocalContext.current
    val mbtilesFile = remember(context) { resolveMbtiles(context) }
    if (mbtilesFile == null) {
        PlaceholderMap()
    } else {
        OfflineMapView(mbtilesFile)
    }
}

@Composable
private fun PlaceholderMap() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Aucune carte locale détectée, veuillez télécharger une zone",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OfflineMapView(mbtilesFile: File) {
    val mapView = rememberMapViewWithLifecycle()
    val currentFile by rememberUpdatedState(mbtilesFile)
    LaunchedEffect(mapView, currentFile) {
        val fileUri = Uri.fromFile(currentFile).toString()
        val tileSet = TileSet("2.2.0", fileUri)
        val rasterSource = RasterSource("offline-raster", tileSet, 256)
        val rasterLayer = RasterLayer("offline-layer", "offline-raster")
        mapView.getMapAsync { mapboxMap ->
            mapboxMap.setStyle(
                Style.Builder()
                    .withSource(rasterSource)
                    .withLayer(rasterLayer)
            )
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView }
    )
}

private fun resolveMbtiles(context: Context): File? {
    val publicDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val appDir = context.getExternalFilesDir(null)
    val candidates = listOfNotNull(publicDir, appDir)
    return candidates
        .map { File(it, "city_map.mbtiles") }
        .firstOrNull { it.exists() }
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
        onDispose {
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}
