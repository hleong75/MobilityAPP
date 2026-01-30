package com.example.mobilityapp.data

import org.json.JSONObject
import java.io.File

data class GraphMetadata(
    val osmTimestamp: Long,
    val osmSize: Long,
    val gtfsTimestamp: Long,
    val gtfsSize: Long
)

object GraphMetadataStore {
    const val VERSION_FILE_NAME = "version.json"
    private const val KEY_OSM_TIMESTAMP = "osmTimestamp"
    private const val KEY_OSM_SIZE = "osmSize"
    private const val KEY_GTFS_TIMESTAMP = "gtfsTimestamp"
    private const val KEY_GTFS_SIZE = "gtfsSize"

    fun fromFiles(osmFile: File, gtfsFile: File): GraphMetadata {
        return GraphMetadata(
            osmTimestamp = osmFile.lastModified(),
            osmSize = osmFile.length(),
            gtfsTimestamp = gtfsFile.lastModified(),
            gtfsSize = gtfsFile.length()
        )
    }

    fun read(versionFile: File): GraphMetadata? {
        if (!versionFile.exists()) return null
        return runCatching {
            val json = JSONObject(versionFile.readText())
            val osmTimestamp = json.optLong(KEY_OSM_TIMESTAMP, -1L)
            val osmSize = json.optLong(KEY_OSM_SIZE, -1L)
            val gtfsTimestamp = json.optLong(KEY_GTFS_TIMESTAMP, -1L)
            val gtfsSize = json.optLong(KEY_GTFS_SIZE, -1L)
            if (osmTimestamp < 0 || osmSize < 0 || gtfsTimestamp < 0 || gtfsSize < 0) {
                null
            } else {
                GraphMetadata(
                    osmTimestamp = osmTimestamp,
                    osmSize = osmSize,
                    gtfsTimestamp = gtfsTimestamp,
                    gtfsSize = gtfsSize
                )
            }
        }.getOrNull()
    }

    fun write(versionFile: File, metadata: GraphMetadata) {
        val json = JSONObject()
            .put(KEY_OSM_TIMESTAMP, metadata.osmTimestamp)
            .put(KEY_OSM_SIZE, metadata.osmSize)
            .put(KEY_GTFS_TIMESTAMP, metadata.gtfsTimestamp)
            .put(KEY_GTFS_SIZE, metadata.gtfsSize)
        versionFile.writeText(json.toString())
    }
}
