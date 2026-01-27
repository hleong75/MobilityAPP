package com.example.mobilityapp.data

import com.example.mobilityapp.domain.model.Instruction
import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.Leg
import com.example.mobilityapp.domain.model.TravelMode
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopperConfig
import com.graphhopper.gtfs.GraphHopperGtfs
import com.graphhopper.util.Parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

object GraphHopperManager {
    private const val PROFILE_FOOT = "foot"
    private const val PROFILE_PT = "pt"
    private const val GRAPH_CACHE_DIR = "graph-cache"
    private const val ENCODERS = "foot"
    private const val ENCODED_VALUES = "foot_access,foot_average_speed"
    private const val PROFILE_CONFIG_KEY = "profile"

    private var hopper: GraphHopperGtfs? = null

    fun init(path: String) {
        val cacheDir = File(path, GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            loadGraph(cacheDir)
        }
    }

    fun importData(
        osmFile: File,
        gtfsFile: File,
        graphRoot: File,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            val graphCacheDir = File(graphRoot, GRAPH_CACHE_DIR)
            val config = GraphHopperConfig().apply {
                putObject("graph.location", graphCacheDir.absolutePath)
                putObject("datareader.file", osmFile.absolutePath)
                putObject("gtfs.file", gtfsFile.absolutePath)
                putObject("graph.flag_encoders", ENCODERS)
                putObject("graph.encoded_values", ENCODED_VALUES)
                putObject(PROFILE_CONFIG_KEY, listOf(
                    mapOf("name" to PROFILE_FOOT, "vehicle" to "foot", "weighting" to "shortest"),
                    mapOf("name" to PROFILE_PT, "vehicle" to "foot", "weighting" to "shortest")
                ))
            }

            val gtfsHopper = GraphHopperGtfs(config)
            gtfsHopper.importOrLoad()
            hopper = gtfsHopper
        }
    }

    fun route(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        time: Date
    ): Itinerary? {
        val hopperInstance = hopper ?: return null
        val request = GHRequest(startLat, startLon, endLat, endLon)
            .setProfile(PROFILE_PT)
            .putHint(Parameters.PT.EARLIEST_DEPARTURE_TIME, time)
        val response = hopperInstance.route(request)
        return mapResponse(response)
    }

    private fun loadGraph(cacheDir: File) {
        val graph = GraphHopperGtfs(GraphHopperConfig().apply {
            putObject("graph.location", cacheDir.absolutePath)
            putObject("graph.flag_encoders", ENCODERS)
            putObject("graph.encoded_values", ENCODED_VALUES)
            putObject(PROFILE_CONFIG_KEY, listOf(
                mapOf("name" to PROFILE_FOOT, "vehicle" to "foot", "weighting" to "shortest"),
                mapOf("name" to PROFILE_PT, "vehicle" to "foot", "weighting" to "shortest")
            ))
        })
        graph.load(cacheDir.absolutePath)
        hopper = graph
    }

    private fun mapResponse(response: GHResponse): Itinerary? {
        if (response.hasErrors()) return null
        val path = response.best ?: return null
        val instructions = path.instructions.map {
            Instruction(
                text = it.text,
                distanceMeters = it.distance,
                durationSeconds = it.time / 1000
            )
        }
        val mode = if (path.description?.contains(PROFILE_PT, ignoreCase = true) == true) {
            TravelMode.PT
        } else {
            TravelMode.FOOT
        }
        val leg = Leg(
            mode = mode,
            instructions = instructions,
            distanceMeters = path.distance,
            durationSeconds = path.time / 1000
        )
        return Itinerary(
            legs = listOf(leg),
            startTime = null,
            endTime = null,
            distanceMeters = path.distance,
            durationSeconds = path.time / 1000
        )
    }
}
