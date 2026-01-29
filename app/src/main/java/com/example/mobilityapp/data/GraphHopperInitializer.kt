package com.example.mobilityapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GraphHopperInitializer {
    private const val DEFAULT_OSM_FILE = "data.osm.pbf"
    private const val DEFAULT_GTFS_FILE = "data.gtfs.zip"

    suspend fun start(context: Context) {
        withContext(Dispatchers.IO) {
            val graphRoot = context.filesDir
            GraphHopperManager.init(graphRoot.absolutePath)
            val graphCacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
            if (graphCacheDir.exists()) {
                return@withContext
            }
            val dataDir = context.getExternalFilesDir(null) ?: graphRoot
            val osmFile = File(dataDir, DEFAULT_OSM_FILE)
            val gtfsFile = File(dataDir, DEFAULT_GTFS_FILE)
            if (!osmFile.exists() || !gtfsFile.exists()) {
                val errorMessage = when {
                    !osmFile.exists() && !gtfsFile.exists() -> "Fichier OSM et GTFS manquants"
                    !osmFile.exists() -> "Fichier OSM manquant"
                    else -> "Fichier GTFS manquant"
                }
                Log.e("GH_DEBUG", "Vérification des fichiers...")
                Log.e("GH_DEBUG", "Fichier OSM trouvé : ${osmFile.exists()}")
                throw IllegalStateException(errorMessage)
            }
            GraphHopperManager.importData(
                osmFile = osmFile,
                gtfsFile = gtfsFile,
                graphRoot = graphRoot
            )
        }
    }
}
