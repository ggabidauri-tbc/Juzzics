package com.example.juzzics.features.nearby.data

import android.content.Context
import com.example.juzzics.common.messages.AppMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** A map area saved on the phone (works without internet). */
data class SavedMapArea(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val complete: Boolean,
    /** where it is, to fly there */
    val bounds: LatLngBounds?,
)

data class OfflineMapsState(
    val areas: List<SavedMapArea> = emptyList(),
    /** 0..1 while an area downloads, null otherwise */
    val downloadProgress: Float? = null,
)

/**
 * Map areas downloaded for offline use (MapLibre offline regions, OpenFreeMap tiles):
 * before a trip, with internet, save the area; on the trail the map works without signal.
 */
class OfflineMaps(private val context: Context) {

    private val _state = MutableStateFlow(OfflineMapsState())
    val state: StateFlow<OfflineMapsState> = _state.asStateFlow()

    private val manager: OfflineManager by lazy {
        MapLibre.getInstance(context)
        OfflineManager.getInstance(context).also { it.setOfflineMapboxTileCountLimit(TILE_LIMIT) }
    }
    private var regions: List<OfflineRegion> = emptyList()
    private var downloading: OfflineRegion? = null
    /** a region is being created (not downloading yet), and Cancel was tapped meanwhile */
    private var starting = false
    private var cancelRequested = false

    /** reads the saved areas (and their sizes) */
    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                regions = offlineRegions?.toList().orEmpty()
                _state.update { it.copy(areas = regions.map { region -> SavedMapArea(region.id, nameOf(region), 0, false, region.definition.bounds) }) }
                regions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status ?: return
                            _state.update { state ->
                                state.copy(areas = state.areas.map {
                                    if (it.id == region.id) it.copy(sizeBytes = status.completedResourceSize, complete = status.isComplete) else it
                                })
                            }
                        }

                        override fun onError(error: String?) = Unit
                    })
                }
            }

            override fun onError(error: String) = Unit
        })
    }

    /** saves [bounds] (the part of the map on screen) for offline use */
    fun download(bounds: LatLngBounds, name: String) {
        if (starting || downloading != null) return
        // MapLibre's own tile limit only counts Mapbox tiles: count them here
        if (tileCount(bounds) > TILE_LIMIT) {
            AppMessages.show("That area is too big: zoom in a bit and try again")
            return
        }
        starting = true
        val definition = OfflineTilePyramidRegionDefinition(
            STYLE_URL, bounds, MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density
        )
        val metadata = JSONObject().put(KEY_NAME, name).toString().toByteArray()
        _state.update { it.copy(downloadProgress = 0f) }
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                starting = false
                downloading = offlineRegion
                if (cancelRequested) {
                    cancelRequested = false
                    finished(offlineRegion, "Map download stopped", delete = true)
                    return
                }
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val progress = if (status.requiredResourceCount > 0) {
                            status.completedResourceCount.toFloat() / status.requiredResourceCount
                        } else 0f
                        _state.update { it.copy(downloadProgress = progress) }
                        if (status.isComplete) finished(offlineRegion, "Map saved: \"$name\" now works without internet")
                    }

                    override fun onError(error: OfflineRegionError) {
                        // connection hiccups are retried by MapLibre; only give up on real errors
                        if (error.reason != OfflineRegionError.REASON_CONNECTION) {
                            finished(offlineRegion, "The map couldn't be saved: ${error.message}", delete = true)
                        }
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        finished(offlineRegion, "That area is too big: zoom in a bit and try again", delete = true)
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                starting = false
                cancelRequested = false
                _state.update { it.copy(downloadProgress = null) }
                AppMessages.show("The map couldn't be saved: $error")
            }
        })
    }

    fun cancelDownload() {
        // still being created: stopped as soon as it exists
        if (starting) cancelRequested = true
        downloading?.let { finished(it, "Map download stopped", delete = true) }
    }

    /** how many tiles [bounds] takes from [MIN_ZOOM] to [MAX_ZOOM] (web mercator) */
    private fun tileCount(bounds: LatLngBounds): Long {
        var total = 0L
        for (zoom in MIN_ZOOM.toInt()..MAX_ZOOM.toInt()) {
            val n = 1 shl zoom
            fun x(lon: Double) = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            fun y(lat: Double): Int {
                val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
                return ((1.0 - kotlin.math.ln(kotlin.math.tan(rad) + 1.0 / kotlin.math.cos(rad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            }
            val xs = (x(bounds.longitudeEast) - x(bounds.longitudeWest)).let { if (it < 0) it + n else it } + 1
            val ys = y(bounds.latitudeSouth) - y(bounds.latitudeNorth) + 1
            total += minOf(xs, n).toLong() * ys
        }
        return total
    }

    fun delete(id: Long) {
        val region = regions.find { it.id == id } ?: return
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                refresh()
                AppMessages.show("Map area deleted")
            }

            override fun onError(error: String) = AppMessages.show("Couldn't delete it: $error")
        })
    }

    private fun finished(region: OfflineRegion, message: String, delete: Boolean = false) {
        region.setObserver(null)
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        downloading = null
        _state.update { it.copy(downloadProgress = null) }
        if (delete) {
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = refresh()
                override fun onError(error: String) = refresh()
            })
        } else refresh()
        AppMessages.show(message)
    }

    private fun nameOf(region: OfflineRegion): String =
        runCatching { JSONObject(String(region.metadata)).getString(KEY_NAME) }.getOrDefault("Map area")

    companion object {
        /** OpenFreeMap: free, no key, OpenStreetMap data (attribution is shown on the map) */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        /** overview to street / trail level (the map zooms in further, drawing from level 14) */
        const val MIN_ZOOM = 8.0
        const val MAX_ZOOM = 14.0
        /** keeps one area to a sensible size (a region of a few tens of km, up to ~100 MB) */
        const val TILE_LIMIT = 3_000L
        private const val KEY_NAME = "name"
    }
}
