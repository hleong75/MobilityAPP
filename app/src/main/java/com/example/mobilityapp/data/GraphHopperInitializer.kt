package com.example.mobilityapp.data

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GraphHopperInitializer {
    private const val DEFAULT_OSM_FILE = "data.osm.pbf"
    private const val DEFAULT_GTFS_FILE = "data.gtfs.zip"
    private const val WORK_NAME = "graphhopper_import"

    suspend fun start(context: Context) {
        withContext(Dispatchers.IO) {
            val graphRoot = context.filesDir
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
            val shouldRebuild = shouldRebuildGraph(graphRoot, osmFile, gtfsFile)
            if (shouldRebuild) {
                deleteGraphCache(graphRoot)
                GraphHopperManager.reset()
            }
            val graphCacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
            if (graphCacheDir.exists() && !shouldRebuild) {
                GraphHopperManager.init(graphRoot.absolutePath)
                return@withContext
            }
            enqueueImport(context, graphRoot, osmFile, gtfsFile)
        }
    }

    suspend fun forceRebuild(context: Context) {
        withContext(Dispatchers.IO) {
            val graphRoot = context.filesDir
            val dataDir = context.getExternalFilesDir(null) ?: graphRoot
            val osmFile = File(dataDir, DEFAULT_OSM_FILE)
            val gtfsFile = File(dataDir, DEFAULT_GTFS_FILE)
            if (!osmFile.exists() || !gtfsFile.exists()) {
                val errorMessage = when {
                    !osmFile.exists() && !gtfsFile.exists() -> "Fichier OSM et GTFS manquants"
                    !osmFile.exists() -> "Fichier OSM manquant"
                    else -> "Fichier GTFS manquant"
                }
                throw IllegalStateException(errorMessage)
            }
            deleteGraphCache(graphRoot)
            GraphHopperManager.reset()
            enqueueImport(context, graphRoot, osmFile, gtfsFile)
        }
    }

    private fun enqueueImport(
        context: Context,
        graphRoot: File,
        osmFile: File,
        gtfsFile: File
    ) {
        val workManager = WorkManager.getInstance(context)
        val inputData = Data.Builder()
            .putString(GraphHopperImportWorker.KEY_OSM_PATH, osmFile.absolutePath)
            .putString(GraphHopperImportWorker.KEY_GTFS_PATH, gtfsFile.absolutePath)
            .putString(GraphHopperImportWorker.KEY_GRAPH_ROOT_PATH, graphRoot.absolutePath)
            .build()
        val request = OneTimeWorkRequestBuilder<GraphHopperImportWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun deleteGraphCache(graphRoot: File) {
        val cacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME).delete()
    }

    private fun shouldRebuildGraph(graphRoot: File, osmFile: File, gtfsFile: File): Boolean {
        val versionFile = File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME)
        val savedMetadata = GraphMetadataStore.read(versionFile) ?: return true
        val currentMetadata = GraphMetadataStore.fromFiles(osmFile, gtfsFile)
        return savedMetadata.osmTimestamp != currentMetadata.osmTimestamp ||
            savedMetadata.osmSize != currentMetadata.osmSize ||
            savedMetadata.gtfsTimestamp != currentMetadata.gtfsTimestamp ||
            savedMetadata.gtfsSize != currentMetadata.gtfsSize
    }
}
