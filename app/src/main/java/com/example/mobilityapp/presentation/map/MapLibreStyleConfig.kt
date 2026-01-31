package com.example.mobilityapp.presentation.map

import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Configuration for MapLibre styles to use readable sans-serif fonts
 * and custom transport icons (Bus, Tram, Train)
 */
object MapLibreStyleConfig {
    
    // Font configuration for readable labels on map
    private const val DEFAULT_TEXT_FONT = "sans-serif"
    private val TEXT_FONTS = arrayOf(DEFAULT_TEXT_FONT)
    
    /**
     * Creates a SymbolLayer for bus stops with custom icon
     */
    fun createBusStopLayer(sourceId: String, layerId: String): SymbolLayer {
        return SymbolLayer(layerId, sourceId).withProperties(
            PropertyFactory.iconImage("ic_bus"),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.textField("{name}"),
            PropertyFactory.textFont(TEXT_FONTS),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
            PropertyFactory.textColor("#1C3F7A"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2f)
        )
    }
    
    /**
     * Creates a SymbolLayer for tram stops with custom icon
     */
    fun createTramStopLayer(sourceId: String, layerId: String): SymbolLayer {
        return SymbolLayer(layerId, sourceId).withProperties(
            PropertyFactory.iconImage("ic_tram"),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.textField("{name}"),
            PropertyFactory.textFont(TEXT_FONTS),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
            PropertyFactory.textColor("#1C3F7A"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2f)
        )
    }
    
    /**
     * Creates a SymbolLayer for train stations with custom icon
     */
    fun createTrainStationLayer(sourceId: String, layerId: String): SymbolLayer {
        return SymbolLayer(layerId, sourceId).withProperties(
            PropertyFactory.iconImage("ic_train"),
            PropertyFactory.iconSize(1.2f),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.textField("{name}"),
            PropertyFactory.textFont(TEXT_FONTS),
            PropertyFactory.textSize(14f),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
            PropertyFactory.textColor("#1C3F7A"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2f)
        )
    }
    
    /**
     * Creates a generic POI layer with readable sans-serif labels
     */
    fun createPoiLayer(sourceId: String, layerId: String): SymbolLayer {
        return SymbolLayer(layerId, sourceId).withProperties(
            PropertyFactory.textField("{name}"),
            PropertyFactory.textFont(TEXT_FONTS),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor("#2C3E50"),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(2f),
            PropertyFactory.textAllowOverlap(false)
        )
    }
}
