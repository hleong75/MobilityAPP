package com.example.mobilityapp.data

import com.example.mobilityapp.domain.model.Instruction
import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.Leg
import com.example.mobilityapp.domain.model.TravelMode
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.Profile
import com.graphhopper.gtfs.GHPointLocation
import com.graphhopper.gtfs.Request
import com.graphhopper.json.Statement
import com.graphhopper.util.CustomModel
import com.graphhopper.util.shapes.GHPoint
import com.graphhopper.gtfs.GraphHopperGtfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

object GraphHopperManager {
    private const val DEFAULT_USE_MMAP_STORE = true
    private const val PROFILE_FOOT = "foot"
    private const val GRAPH_CACHE_DIR = "graph-cache"
    private const val ENCODERS = "foot"
    private const val ENCODED_VALUES = "foot_access,foot_average_speed,foot_priority"
    private const val MILLIS_TO_SECONDS = 1000.0

    private var hopper: GraphHopperGtfs? = null

    fun init(path: String, useMmapStore: Boolean = DEFAULT_USE_MMAP_STORE) {
        val cacheDir = File(path, GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            loadGraph(cacheDir, useMmapStore)
        }
    }

    fun importData(
        osmFile: File,
        gtfsFile: File,
        graphRoot: File,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        useMmapStore: Boolean = DEFAULT_USE_MMAP_STORE
    ) {
        scope.launch {
            val graphCacheDir = File(graphRoot, GRAPH_CACHE_DIR)
            val config = GraphHopperConfig().apply {
                putObject("graph.location", graphCacheDir.absolutePath)
                putObject("datareader.file", osmFile.absolutePath)
                putObject("gtfs.file", gtfsFile.absolutePath)
                putObject("graph.dataaccess", dataAccessType(useMmapStore))
                applyProfiles(this)
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
        time: Date,
        mode: TravelMode
    ): Itinerary? {
        val hopperInstance = hopper ?: return null
        val response = if (mode == TravelMode.PT) {
            val request = Request(listOf(
                GHPointLocation(GHPoint(startLat, startLon)),
                GHPointLocation(GHPoint(endLat, endLon))
            ), time.toInstant())
            request.setAccessProfile(PROFILE_FOOT)
            request.setEgressProfile(PROFILE_FOOT)
            hopperInstance.route(request)
        } else {
            val request = GHRequest(startLat, startLon, endLat, endLon)
                .setProfile(PROFILE_FOOT)
            hopperInstance.route(request)
        }
        return mapResponse(response, mode)
    }

    private fun loadGraph(cacheDir: File, useMmapStore: Boolean) {
        val graph = GraphHopperGtfs(GraphHopperConfig().apply {
            putObject("graph.location", cacheDir.absolutePath)
            putObject("graph.dataaccess", dataAccessType(useMmapStore))
            applyProfiles(this)
        })
        graph.load(cacheDir.absolutePath)
        hopper = graph
    }

    private fun mapResponse(response: GHResponse, mode: TravelMode): Itinerary? {
        if (response.hasErrors()) return null
        val path = response.best ?: return null
        val instructions = path.instructions.map {
            Instruction(
                text = it.text,
                distanceMeters = it.distance,
                durationSeconds = (it.time / MILLIS_TO_SECONDS).toLong()
            )
        }
        val leg = Leg(
            mode = mode,
            instructions = instructions,
            distanceMeters = path.distance,
            durationSeconds = (path.time / MILLIS_TO_SECONDS).toLong()
        )
        return Itinerary(
            legs = listOf(leg),
            startTime = null,
            endTime = null,
            distanceMeters = path.distance,
            durationSeconds = (path.time / MILLIS_TO_SECONDS).toLong()
        )
    }

    private fun applyProfiles(config: GraphHopperConfig) {
        config.putObject("graph.flag_encoders", ENCODERS)
        config.putObject("graph.encoded_values", ENCODED_VALUES)
        config.setProfiles(
            listOf(
                Profile(PROFILE_FOOT)
                    .setCustomModel(
                        CustomModel()
                            .addToPriority(Statement.If("foot_access", Statement.Op.MULTIPLY, "foot_priority"))
                            .addToPriority(Statement.Else(Statement.Op.MULTIPLY, "0"))
                            .addToSpeed(Statement.If("true", Statement.Op.LIMIT, "foot_average_speed"))
                    )
            )
        )
    }

    private fun dataAccessType(useMmapStore: Boolean): String {
        return if (useMmapStore) "MMAP_STORE" else "RAM_STORE"
    }

}
