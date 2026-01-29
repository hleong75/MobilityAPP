package com.example.mobilityapp.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File

class GraphHopperImportWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val osmPath = inputData.getString(KEY_OSM_PATH) ?: return Result.failure()
        val gtfsPath = inputData.getString(KEY_GTFS_PATH) ?: return Result.failure()
        val graphRootPath = inputData.getString(KEY_GRAPH_ROOT_PATH) ?: return Result.failure()
        return kotlin.runCatching {
            setForeground(createForegroundInfo())
            GraphHopperManager.importData(
                osmFile = File(osmPath),
                gtfsFile = File(gtfsPath),
                graphRoot = File(graphRootPath)
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.failure() }
        )
    }

    private fun createForegroundInfo(): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, Notification.FOREGROUND_SERVICE_DATA_SYNC)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = NOTIFICATION_CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_OSM_PATH = "osm_path"
        const val KEY_GTFS_PATH = "gtfs_path"
        const val KEY_GRAPH_ROOT_PATH = "graph_root_path"

        private const val NOTIFICATION_CHANNEL_ID = "graphhopper_import_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Import GraphHopper"
        private const val NOTIFICATION_CHANNEL_DESCRIPTION = "Import des données de transport"
        private const val NOTIFICATION_TITLE = "Initialisation du réseau"
        private const val NOTIFICATION_TEXT = "Construction du réseau de transport en cours..."
        private const val NOTIFICATION_ID = 1001
    }
}
