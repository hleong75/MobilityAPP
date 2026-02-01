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
    private const val ELEVATION_PROVIDER_NOOP = "noop"
    private const val PROFILE_FOOT = "foot"
    private const val OUT_OF_MEMORY_MESSAGE =
        "OutOfMemoryError during GraphHopper import. Verify that the Large Heap option is enabled."
    private const val DATA_ACCESS_TYPE = "MMAP_STORE"
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

    suspend fun init(path: String) {
        if (_isReady.value) {
            return
        }
        val cacheDir = File(path, GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            loadGraph(cacheDir)
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
        graphRoot: File
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.i(LOG_TAG, "Verifying files...")
                Log.i(LOG_TAG, "OSM file found: ${osmFile.exists()}")
                Log.i(LOG_TAG, "Starting GraphHopper import...")
                
                // Security cleanup: Delete graph-cache directory to start fresh
                val graphCacheDir = File(graphRoot, GRAPH_CACHE_DIR)
                if (graphCacheDir.exists()) {
                    Log.i(LOG_TAG, "Deleting existing graph-cache directory for clean import...")
                    if (graphCacheDir.deleteRecursively()) {
                        Log.i(LOG_TAG, "Graph-cache directory deleted successfully")
                    } else {
                        Log.w(LOG_TAG, "Failed to delete graph-cache directory completely")
                    }
                }
                
                val config = GraphHopperConfig().apply {
                    putObject("graph.location", graphCacheDir.absolutePath)
                    putObject("graph.elevation.provider", ELEVATION_PROVIDER_NOOP)
                    putObject("datareader.file", osmFile.absolutePath)
                    putObject("gtfs.file", gtfsFile.absolutePath)
                    
                    applyMemoryOptimizations(this)
                    
                    putObject("gtfs.trip_based", false)
                    applyProfiles(this)
                }

                val gtfsHopper = GraphHopperGtfs(config)
                gtfsHopper.init(config)
                
                // Diagnostic logging with timeout detection
                try {
                    Log.i(LOG_TAG, "Starting hopper.importOrLoad()...")
                    val importStartTime = System.currentTimeMillis()
                    gtfsHopper.importOrLoad()
                    val importDuration = System.currentTimeMillis() - importStartTime
                    Log.i(LOG_TAG, "hopper.importOrLoad() completed in ${importDuration}ms")
                } catch (e: Exception) {
                    logTimeoutIfDetected(e, "Import/Load")
                    throw e
                }
                
                val router = buildPtRouter(config, gtfsHopper)
                synchronized(this@GraphHopperManager) {
                    hopper = gtfsHopper
                    graphConfig = config
                    ptRouter = router
                    _isReady.value = true
                }
                Log.i(LOG_TAG, "Import completed!")
            } catch (e: OutOfMemoryError) {
                logOutOfMemory(e)
                throw e
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

    private suspend fun loadGraph(cacheDir: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.i(LOG_TAG, "Verifying files...")
                Log.i(LOG_TAG, "Cache directory found: ${cacheDir.exists()}")
                Log.i(LOG_TAG, "Starting GraphHopper load...")
                val config = GraphHopperConfig().apply {
                    putObject("graph.location", cacheDir.absolutePath)
                    putObject("graph.elevation.provider", ELEVATION_PROVIDER_NOOP)
                    
                    applyMemoryOptimizations(this)
                    
                    putObject("gtfs.trip_based", false)
                    applyProfiles(this)
                }
                val graph = GraphHopperGtfs(config)
                graph.init(config)
                
                // Diagnostic logging with timeout detection
                try {
                    Log.i(LOG_TAG, "Starting graph.load()...")
                    val loadStartTime = System.currentTimeMillis()
                    graph.load()
                    val loadDuration = System.currentTimeMillis() - loadStartTime
                    Log.i(LOG_TAG, "graph.load() completed in ${loadDuration}ms")
                } catch (e: Exception) {
                    logTimeoutIfDetected(e, "Load")
                    throw e
                }
                
                val router = buildPtRouter(config, graph)
                synchronized(this@GraphHopperManager) {
                    hopper = graph
                    graphConfig = config
                    ptRouter = router
                    _isReady.value = true
                }
                Log.i(LOG_TAG, "Load completed!")
            } catch (e: OutOfMemoryError) {
                logOutOfMemory(e)
                throw e
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
    
    private fun applyMemoryOptimizations(config: GraphHopperConfig) {
        // RAM management for 1GB files
        config.putObject("graph.dataaccess", DATA_ACCESS_TYPE)
        config.putObject("graph.ch.prepare_threads", "1")
        
        // Block network access (Air-Gapped mode)
        config.putObject("graph.elevation.cache_dir", "")
        config.putObject("graph.logging.level", "error")
        
        // Reduce graph complexity by ignoring small isolated segments (saves RAM)
        config.putObject("prepare.min_network_size", "200")
        config.putObject("prepare.min_one_way_network_size", "200")
        
        // Disable street name storage to reduce memory usage during import
        config.putObject("datareader.instructions", "false")
        
        // Disable heavy optimizations
        config.putObject("prepare.ch.weightings", "no")
        config.putObject("prepare.lm.weightings", "no")
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

    private fun logOutOfMemory(error: OutOfMemoryError) {
        Log.e(LOG_TAG, OUT_OF_MEMORY_MESSAGE, error)
    }

    private fun logTimeoutIfDetected(e: Exception, operation: String) {
        val errorMsg = e.message ?: e.toString()
        Log.e(LOG_TAG, "$operation failed: $errorMsg", e)
        if (errorMsg.contains("timeout", ignoreCase = true) || 
            errorMsg.contains("timed out", ignoreCase = true)) {
            Log.e(LOG_TAG, "TIMEOUT DETECTED: The operation exceeded the allowed time limit")
            Log.e(LOG_TAG, "Timeout details: $errorMsg")
        }
    }

}
