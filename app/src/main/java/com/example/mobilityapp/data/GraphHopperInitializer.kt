package com.example.mobilityapp.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File

object GraphHopperInitializer {
    private const val WORK_NAME = "graphhopper_import"
    private const val DEFAULT_OSM_FILE = "data.osm.pbf"
    private const val DEFAULT_GTFS_FILE = "data.gtfs.zip"

    fun start(context: Context) {
        val graphRoot = context.filesDir
        GraphHopperManager.init(graphRoot.absolutePath)
        val graphCacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
        if (graphCacheDir.exists()) {
            return
        }
        val dataDir = context.getExternalFilesDir(null) ?: graphRoot
        val osmFile = File(dataDir, DEFAULT_OSM_FILE)
        val gtfsFile = File(dataDir, DEFAULT_GTFS_FILE)
        if (!osmFile.exists() || !gtfsFile.exists()) {
            return
        }
        enqueueImportWork(context, osmFile, gtfsFile, graphRoot)
    }

    private fun enqueueImportWork(context: Context, osmFile: File, gtfsFile: File, graphRoot: File) {
        val request = OneTimeWorkRequestBuilder<GraphHopperImportWorker>()
            .setInputData(
                workDataOf(
                    GraphHopperImportWorker.KEY_OSM_PATH to osmFile.absolutePath,
                    GraphHopperImportWorker.KEY_GTFS_PATH to gtfsFile.absolutePath,
                    GraphHopperImportWorker.KEY_GRAPH_ROOT_PATH to graphRoot.absolutePath
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
