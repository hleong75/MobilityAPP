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
        val osmPath = inputData.getString(KEY_OSM_PATH) ?: return Result.failure()
        val gtfsPath = inputData.getString(KEY_GTFS_PATH) ?: return Result.failure()
        val graphRootPath = inputData.getString(KEY_GRAPH_ROOT_PATH) ?: return Result.failure()
        val graphRoot = File(graphRootPath)
        val osmFile = File(osmPath)
        val gtfsFile = File(gtfsPath)
        val startTime = SystemClock.elapsedRealtime()
        return try {
            setForeground(createForegroundInfo())
            updateProgress(10)
            try {
                GraphHopperManager.importData(
                    osmFile = osmFile,
                    gtfsFile = gtfsFile,
                    graphRoot = graphRoot
                )
            } catch (e: Exception) {
                return handleFailure(graphRoot, "GraphHopper data import failed", e)
            }
            updateProgress(80)
            try {
                val metadata = GraphMetadataStore.fromFiles(osmFile, gtfsFile)
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
            cleanupAfterFailure(graphRoot)
            throw e
        } catch (e: Exception) {
            return handleFailure(graphRoot, "GraphHopper import failed", e)
        } finally {
            val durationMs = SystemClock.elapsedRealtime() - startTime
            Log.i(TAG, "GraphHopper import duration: ${durationMs}ms")
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
        Log.e(TAG, message, error)
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
    }
}
