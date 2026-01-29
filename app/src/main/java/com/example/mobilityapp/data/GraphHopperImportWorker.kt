package com.example.mobilityapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.mobilityapp.R
import com.example.mobilityapp.presentation.MainActivity
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
            onFailure = { throwable ->
                Log.e(TAG, "GraphHopper import failed", throwable)
                Result.failure()
            }
        )
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
            ForegroundInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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

    companion object {
        const val KEY_OSM_PATH = "osm_path"
        const val KEY_GTFS_PATH = "gtfs_path"
        const val KEY_GRAPH_ROOT_PATH = "graph_root_path"

        private const val NOTIFICATION_CHANNEL_ID = "graphhopper_import_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "GraphHopperImport"
    }
}
