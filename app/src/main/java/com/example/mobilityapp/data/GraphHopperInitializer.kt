package com.example.mobilityapp.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.os.Environment
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.File

sealed class InitializationState {
    data class MissingFiles(val missingFiles: List<String>) : InitializationState()
    data object NeedsImport : InitializationState()
    data object Importing : InitializationState()
    data object Ready : InitializationState()
    data class Error(val message: String) : InitializationState()
}

object GraphHopperInitializer {
    private const val DEFAULT_OSM_FILE = "data.osm.pbf"
    private const val DEFAULT_GTFS_FILE = "data.gtfs.zip"
    private const val WORK_NAME = "graphhopper_import"
    private const val READY_TIMEOUT_MS = 60_000L
    private const val MIN_DISK_SPACE_BYTES = 3L * 1024L * 1024L * 1024L // 3 GB
    private const val BYTES_TO_GB = 1024.0 * 1024.0 * 1024.0
    const val DEFAULT_ERROR_MESSAGE = "Erreur: Import GraphHopper"

    fun start(context: Context): Flow<InitializationState> = flow {
        try {
            val graphRoot = context.filesDir
            val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
            val gtfsFile = File(graphRoot, DEFAULT_GTFS_FILE)
            val missingFiles = getMissingFiles(osmFile, gtfsFile)
            if (missingFiles.isNotEmpty()) {
                Log.e("GH_DEBUG", "Fichiers manquants: ${missingFiles.joinToString(", ")}")
                emit(InitializationState.MissingFiles(missingFiles))
                return@flow
            }
            if (!osmFile.canRead() || !gtfsFile.canRead()) {
                Log.e("GH_DEBUG", "Fichiers illisibles: osm=${osmFile.canRead()}, gtfs=${gtfsFile.canRead()}")
                emit(InitializationState.Error("Fichiers illisibles. Vérifiez les permissions."))
                return@flow
            }
            
            // Check available disk space before import
            val diskSpaceError = checkDiskSpace(graphRoot)
            if (diskSpaceError != null) {
                emit(InitializationState.Error(diskSpaceError))
                return@flow
            }
            
            val shouldRebuild = shouldRebuildGraph(graphRoot, osmFile, gtfsFile)
            if (shouldRebuild) {
                // Force deletion of ALL cache content before starting
                deleteAllCache(graphRoot)
                GraphHopperManager.reset()
            }
            val graphCacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
            if (graphCacheDir.exists() && !shouldRebuild) {
                GraphHopperManager.init(graphRoot.absolutePath)
                emit(InitializationState.Ready)
                return@flow
            }
            emit(InitializationState.NeedsImport)
            enqueueImport(context, graphRoot, osmFile, gtfsFile)
            emit(InitializationState.Importing)
            withTimeout(READY_TIMEOUT_MS) {
                GraphHopperManager.isReady.filter { it }.first()
            }
            emit(InitializationState.Ready)
        } catch (e: TimeoutCancellationException) {
            Log.e("GH_DEBUG", "Timeout during GraphHopper init", e)
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            emit(InitializationState.Error("Délai dépassé après 60s"))
        } catch (e: Exception) {
            Log.e("GH_DEBUG", "CRASH", e)
            emit(InitializationState.Error(e.message ?: DEFAULT_ERROR_MESSAGE))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun forceRebuild(context: Context) {
        withContext(Dispatchers.IO) {
            val graphRoot = context.filesDir
            val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
            val gtfsFile = File(graphRoot, DEFAULT_GTFS_FILE)
            val missingFiles = getMissingFiles(osmFile, gtfsFile)
            if (missingFiles.isNotEmpty()) {
                Log.e("GH_DEBUG", "Fichiers manquants: ${missingFiles.joinToString(", ")}")
                throw IllegalStateException("Fichiers manquants: ${missingFiles.joinToString(", ")}")
            }
            if (!osmFile.canRead() || !gtfsFile.canRead()) {
                Log.e("GH_DEBUG", "Fichiers illisibles: osm=${osmFile.canRead()}, gtfs=${gtfsFile.canRead()}")
                throw IllegalStateException("Fichiers illisibles. Vérifiez les permissions.")
            }
            
            // Check available disk space before rebuild
            val diskSpaceError = checkDiskSpace(graphRoot)
            if (diskSpaceError != null) {
                throw IllegalStateException(diskSpaceError)
            }
            
            // Force deletion of ALL cache content before starting
            deleteAllCache(graphRoot)
            GraphHopperManager.reset()
            enqueueImport(context, graphRoot, osmFile, gtfsFile)
        }
    }

    suspend fun forceRefreshCheck(context: Context) {
        withContext(Dispatchers.IO) {
            val graphRoot = context.filesDir
            val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
            val gtfsFile = File(graphRoot, DEFAULT_GTFS_FILE)
            val missingFiles = getMissingFiles(osmFile, gtfsFile)
            if (missingFiles.isNotEmpty()) {
                Log.e("GH_DEBUG", "Fichiers manquants: ${missingFiles.joinToString(", ")}")
                return@withContext
            }
            if (!osmFile.canRead() || !gtfsFile.canRead()) {
                Log.e("GH_DEBUG", "Fichiers illisibles: osm=${osmFile.canRead()}, gtfs=${gtfsFile.canRead()}")
                return@withContext
            }
            val versionFile = File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME)
            val savedMetadata = GraphMetadataStore.read(versionFile) ?: return@withContext
            val currentMetadata = GraphMetadataStore.fromFiles(osmFile, gtfsFile)
            val hasChanges = savedMetadata != currentMetadata
            if (!hasChanges || isImportRunning(context)) {
                return@withContext
            }
            
            // Force deletion of ALL cache content before starting
            deleteAllCache(graphRoot)
            GraphHopperManager.reset()
            enqueueImport(context, graphRoot, osmFile, gtfsFile)
        }
    }

    fun getMissingFiles(context: Context): List<String> {
        val graphRoot = context.filesDir
        val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
        val gtfsFile = File(graphRoot, DEFAULT_GTFS_FILE)
        return getMissingFiles(osmFile, gtfsFile)
    }

    fun getOsmLastModified(context: Context): Long? {
        val graphRoot = context.filesDir
        val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
        return osmFile.takeIf { it.exists() }?.lastModified()
    }

    fun copyMissingFilesFromDownloads(context: Context): Boolean {
        val graphRoot = context.filesDir
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        if (needsPermission) {
            val canReadStorage = context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!canReadStorage) {
                Log.w("GH_DEBUG", "READ_EXTERNAL_STORAGE permission not granted.")
                return false
            }
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val osmFile = File(graphRoot, DEFAULT_OSM_FILE)
        val gtfsFile = File(graphRoot, DEFAULT_GTFS_FILE)
        val missingFiles = getMissingFiles(osmFile, gtfsFile)
        var copiedAny = false
        missingFiles.forEach { fileName ->
            val source = File(downloadsDir, fileName)
            val target = File(graphRoot, fileName)
            if (source.exists() && source.isFile) {
                runCatching { source.copyTo(target, overwrite = true) }
                    .onFailure { Log.e("GH_DEBUG", "Failed to copy $fileName from downloads.", it) }
                    .onSuccess { copiedAny = true }
            }
        }
        return copiedAny
    }

    private fun getMissingFiles(osmFile: File, gtfsFile: File): List<String> {
        return buildList {
            if (!osmFile.exists() || !osmFile.isFile) {
                add(DEFAULT_OSM_FILE)
            }
            if (!gtfsFile.exists() || !gtfsFile.isFile) {
                add(DEFAULT_GTFS_FILE)
            }
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
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun isImportRunning(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            workInfos.any { workInfo ->
                workInfo.state == WorkInfo.State.RUNNING ||
                    workInfo.state == WorkInfo.State.ENQUEUED
            }
        }
    }

    private fun deleteGraphCache(graphRoot: File) {
        val cacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME).delete()
    }
    
    private fun deleteAllCache(graphRoot: File) {
        // Force deletion of ALL cache content before starting import
        Log.i("GH_DEBUG", "Suppression agressive de tout le cache...")
        val cacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            val deleted = cacheDir.deleteRecursively()
            if (deleted) {
                Log.i("GH_DEBUG", "Cache supprimé avec succès")
            } else {
                Log.w("GH_DEBUG", "Échec de la suppression complète du cache")
            }
        }
        val metadataFile = File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME)
        if (metadataFile.exists()) {
            metadataFile.delete()
        }
    }
    
    private fun checkDiskSpace(graphRoot: File): String? {
        val availableSpace = graphRoot.usableSpace
        if (availableSpace < MIN_DISK_SPACE_BYTES) {
            val availableGB = availableSpace / BYTES_TO_GB
            val errorMsg = "Espace disque insuffisant: ${String.format("%.2f", availableGB)} GB disponible, 3 GB requis"
            Log.e("GH_DEBUG", errorMsg)
            return errorMsg
        }
        return null
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
