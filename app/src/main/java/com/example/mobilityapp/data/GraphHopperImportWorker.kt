package com.example.mobilityapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.mobilityapp.R
import com.example.mobilityapp.presentation.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class GraphHopperImportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val cacheCleaned = AtomicBoolean(false)

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        var startTime = 0L
        var graphRoot: File? = null
        return try {
            startTime = SystemClock.elapsedRealtime()
            val osmPath = inputData.getString(KEY_OSM_PATH) ?: run {
                Log.e(ERROR_TAG, "Missing OSM path for GraphHopper import")
                return Result.failure()
            }
            val gtfsPath = inputData.getString(KEY_GTFS_PATH) ?: run {
                Log.e(ERROR_TAG, "Missing GTFS path for GraphHopper import")
                return Result.failure()
            }
            val graphRootPath = inputData.getString(KEY_GRAPH_ROOT_PATH) ?: run {
                Log.e(ERROR_TAG, "Missing graph root path for GraphHopper import")
                return Result.failure()
            }
            graphRoot = File(graphRootPath)
            val osmFile = File(osmPath)
            val gtfsFile = File(gtfsPath)
            updateProgress(10)
            val osmReadStart = System.currentTimeMillis()
            val osmTimestamp = osmFile.lastModified()
            val osmSize = osmFile.length()
            Log.w(PERF_TAG, "Lecture OSM (métadonnées): ${System.currentTimeMillis() - osmReadStart}ms")
            val gtfsReadStart = System.currentTimeMillis()
            val gtfsTimestamp = gtfsFile.lastModified()
            val gtfsSize = gtfsFile.length()
            Log.w(PERF_TAG, "Lecture GTFS (métadonnées): ${System.currentTimeMillis() - gtfsReadStart}ms")
            try {
                val graphBuildStart = System.currentTimeMillis()
                GraphHopperManager.importData(
                    osmFile = osmFile,
                    gtfsFile = gtfsFile,
                    graphRoot = graphRoot
                )
                Log.w(PERF_TAG, "Création Graphe: ${System.currentTimeMillis() - graphBuildStart}ms")
            } catch (e: Exception) {
                return handleFailure(graphRoot, "GraphHopper data import failed", e)
            }
            updateProgress(80)
            try {
                val metadata = GraphMetadata(
                    osmTimestamp = osmTimestamp,
                    osmSize = osmSize,
                    gtfsTimestamp = gtfsTimestamp,
                    gtfsSize = gtfsSize
                )
                GraphMetadataStore.write(
                    File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME),
                    metadata
                )
            } catch (e: Exception) {
                return handleFailure(graphRoot, "GraphHopper metadata write failed", e)
            }
            updateProgress(90)
            try {
                GraphHopperManager.init(graphRoot.absolutePath)
            } catch (e: Exception) {
                return handleFailure(graphRoot, "GraphHopper init failed", e)
            }
            updateProgress(100)
            Result.success()
        } catch (e: CancellationException) {
            Log.w(TAG, "GraphHopper import cancelled", e)
            graphRoot?.let { cleanupAfterFailure(it) }
            throw e
        } catch (e: Exception) {
            Log.e(ERROR_TAG, "GraphHopper import failed", e)
            graphRoot?.let { cleanupAfterFailure(it) }
            return Result.failure(buildFailureData(e))
        } finally {
            if (startTime > 0L) {
                val durationMs = SystemClock.elapsedRealtime() - startTime
                Log.i(TAG, "GraphHopper import duration: ${durationMs}ms")
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        ensureNotificationChannel()
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            pendingIntentFlags
        )
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.graphhopper_notification_title))
            .setContentText(appContext.getString(R.string.graphhopper_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            appContext.getString(R.string.graphhopper_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = appContext.getString(R.string.graphhopper_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun updateProgress(percent: Int) {
        if (isStopped) return
        setProgress(
            Data.Builder()
                .putInt(KEY_PROGRESS_PERCENT, percent)
                .build()
        )
    }

    private fun cleanPartialGraphCache(graphRoot: File) {
        if (!cacheCleaned.compareAndSet(false, true)) {
            return
        }
        val cacheDir = File(graphRoot, GraphHopperManager.GRAPH_CACHE_DIR)
        if (cacheDir.exists()) {
            if (!cacheDir.deleteRecursively()) {
                Log.w(TAG, "Failed to delete graph cache at ${cacheDir.absolutePath}")
            }
        }
        val metadataFile = File(graphRoot, GraphMetadataStore.VERSION_FILE_NAME)
        if (metadataFile.exists() && !metadataFile.delete()) {
            Log.w(TAG, "Failed to delete graph metadata at ${metadataFile.absolutePath}")
        }
    }

    private suspend fun cleanupAfterFailure(graphRoot: File) {
        withContext(NonCancellable) {
            cleanPartialGraphCache(graphRoot)
        }
    }

    private suspend fun handleFailure(
        graphRoot: File,
        message: String,
        error: Exception
    ): Result {
        Log.e(ERROR_TAG, message, error)
        cleanupAfterFailure(graphRoot)
        return Result.failure(buildFailureData(error))
    }

    private fun buildFailureData(error: Throwable): Data {
        return Data.Builder()
            .putString(KEY_FAILURE_MESSAGE, error.message ?: error.toString())
            .build()
    }

    companion object {
        const val KEY_OSM_PATH = "osm_path"
        const val KEY_GTFS_PATH = "gtfs_path"
        const val KEY_GRAPH_ROOT_PATH = "graph_root_path"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_FAILURE_MESSAGE = "failure_message"

        private const val NOTIFICATION_CHANNEL_ID = "graphhopper_import_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "GraphHopperImport"
        private const val PERF_TAG = "GH_PERF"
        private const val ERROR_TAG = "GH_ERROR"
    }
}
