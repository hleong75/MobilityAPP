package com.example.mobilityapp.data

import com.example.mobilityapp.domain.model.Instruction
import com.example.mobilityapp.domain.model.Itinerary
import com.example.mobilityapp.domain.model.Leg
import com.example.mobilityapp.domain.model.RouteCoordinate
import com.example.mobilityapp.domain.model.TravelMode
import android.util.Log
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopperConfig
import com.graphhopper.config.Profile
import com.graphhopper.gtfs.GHPointLocation
import com.graphhopper.gtfs.GraphHopperGtfs
import com.graphhopper.gtfs.PtRouter
import com.graphhopper.gtfs.PtRouterImpl
import com.graphhopper.gtfs.Request
import com.graphhopper.json.Statement
import com.graphhopper.util.CustomModel
import com.graphhopper.util.TranslationMap
import com.graphhopper.util.shapes.GHPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

object GraphHopperManager {
    private const val LOG_TAG = "GH_DEBUG"
    private const val DEFAULT_USE_MMAP_STORE = true
    private const val PROFILE_FOOT = "foot"
    internal const val GRAPH_CACHE_DIR = "graph-cache"
    private const val ENCODED_VALUES = "foot_access,foot_average_speed,foot_priority"
    private const val MILLIS_TO_SECONDS = 1000.0

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    private var hopper: GraphHopperGtfs? = null

    @Volatile
    private var ptRouter: PtRouter? = null

    @Volatile
    private var graphConfig: GraphHopperConfig? = null

    suspend fun init(path: String, useMmapStore: Boolean = DEFAULT_USE_MMAP_STORE) {
        if (_isReady.value) {
            return
        }
        val cacheDir = File(path, GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            loadGraph(cacheDir, useMmapStore)
        }
    }

    fun reset() {
        synchronized(this@GraphHopperManager) {
            hopper?.close()
            hopper = null
            ptRouter = null
            graphConfig = null
            _isReady.value = false
        }
    }

    suspend fun importData(
        osmFile: File,
        gtfsFile: File,
        graphRoot: File,
        useMmapStore: Boolean = DEFAULT_USE_MMAP_STORE
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.e(LOG_TAG, "Vérification des fichiers...")
                Log.e(LOG_TAG, "Fichier OSM trouvé : ${osmFile.exists()}")
                Log.e(LOG_TAG, "Démarrage import GraphHopper...")
                val graphCacheDir = File(graphRoot, GRAPH_CACHE_DIR)
                val config = GraphHopperConfig().apply {
                    putObject("graph.location", graphCacheDir.absolutePath)
                    putObject("datareader.file", osmFile.absolutePath)
                    putObject("gtfs.file", gtfsFile.absolutePath)
                    putObject("graph.dataaccess", dataAccessType(useMmapStore))
                    applyProfiles(this)
                }

                val gtfsHopper = GraphHopperGtfs(config)
                gtfsHopper.init(config)
                gtfsHopper.importOrLoad()
                val router = buildPtRouter(config, gtfsHopper)
                synchronized(this@GraphHopperManager) {
                    hopper = gtfsHopper
                    graphConfig = config
                    ptRouter = router
                    _isReady.value = true
                }
                Log.e(LOG_TAG, "Import terminé !")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "CRASH", e)
                throw e
            }
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
            val config = graphConfig ?: return null
            val router = ptRouter ?: synchronized(this@GraphHopperManager) {
                ptRouter ?: buildPtRouter(config, hopperInstance).also { ptRouter = it }
            }
            val request = Request(listOf(
                GHPointLocation(GHPoint(startLat, startLon)),
                GHPointLocation(GHPoint(endLat, endLon))
            ), time.toInstant())
            request.setAccessProfile(PROFILE_FOOT)
            request.setEgressProfile(PROFILE_FOOT)
            router.route(request)
        } else {
            val request = GHRequest(startLat, startLon, endLat, endLon)
                .setProfile(PROFILE_FOOT)
            hopperInstance.route(request)
        }
        return mapResponse(response, mode)
    }

    private suspend fun loadGraph(cacheDir: File, useMmapStore: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                Log.e(LOG_TAG, "Vérification des fichiers...")
                Log.e(LOG_TAG, "Fichier OSM trouvé : ${cacheDir.exists()}")
                Log.e(LOG_TAG, "Démarrage import GraphHopper...")
                val config = GraphHopperConfig().apply {
                    putObject("graph.location", cacheDir.absolutePath)
                    putObject("graph.dataaccess", dataAccessType(useMmapStore))
                    applyProfiles(this)
                }
                val graph = GraphHopperGtfs(config)
                graph.init(config)
                graph.load()
                val router = buildPtRouter(config, graph)
                synchronized(this@GraphHopperManager) {
                    hopper = graph
                    graphConfig = config
                    ptRouter = router
                    _isReady.value = true
                }
                Log.e(LOG_TAG, "Import terminé !")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "CRASH", e)
                throw e
            }
        }
    }

    private fun mapResponse(response: GHResponse, mode: TravelMode): Itinerary? {
        if (response.hasErrors()) return null
        val path = response.best ?: return null
        val instructions = path.instructions.map {
            Instruction(
                text = it.name,
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
        val pointList = path.points
        val coordinates = (0 until pointList.size()).map { index ->
            RouteCoordinate(
                latitude = pointList.getLat(index),
                longitude = pointList.getLon(index)
            )
        }
        return Itinerary(
            legs = listOf(leg),
            startTime = null,
            endTime = null,
            distanceMeters = path.distance,
            durationSeconds = (path.time / MILLIS_TO_SECONDS).toLong(),
            routeCoordinates = coordinates
        )
    }

    private fun applyProfiles(config: GraphHopperConfig) {
        config.putObject("import.osm.ignored_highways", "")
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

    private fun buildPtRouter(config: GraphHopperConfig, hopperInstance: GraphHopperGtfs): PtRouter {
        return PtRouterImpl.Factory(
            config,
            TranslationMap().doImport(),
            hopperInstance.baseGraph,
            hopperInstance.encodingManager,
            hopperInstance.locationIndex,
            hopperInstance.gtfsStorage
        ).createWithoutRealtimeFeed()
    }

    private fun dataAccessType(useMmapStore: Boolean): String {
        return if (useMmapStore) "MMAP_STORE" else "RAM_STORE"
    }

}
