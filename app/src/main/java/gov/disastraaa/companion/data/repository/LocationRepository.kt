package gov.disastraaa.companion.data.repository

import android.content.Context
import android.location.Location
import gov.disastraaa.companion.data.api.TrackingApiService
import gov.disastraaa.companion.data.local.dao.LocationDao
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import gov.disastraaa.companion.utils.NetworkUtils
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class LocationRepository(
    private val context: Context,
    private val trackingApiService: TrackingApiService,
    private val locationDao: LocationDao
) {

    private var activeSessionId: String? = null

    val unsyncedCountFlow: Flow<Int> = locationDao.observeUnsyncedCount()

    suspend fun getUnsyncedCount(): Int = locationDao.getUnsyncedCount()

    suspend fun startSession(): Result<String> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            val localSessionId = "offline_session_${System.currentTimeMillis()}"
            activeSessionId = localSessionId
            return Result.success(localSessionId)
        }

        return try {
            val response = trackingApiService.createSession(SessionRequest())
            if (response.isSuccessful && response.body()?.success == true) {
                val sid = response.body()?.sessionId ?: "sess_${System.currentTimeMillis()}"
                activeSessionId = sid
                Result.success(sid)
            } else {
                val localSessionId = "fallback_session_${System.currentTimeMillis()}"
                activeSessionId = localSessionId
                Result.success(localSessionId)
            }
        } catch (e: Exception) {
            val localSessionId = "fallback_session_${System.currentTimeMillis()}"
            activeSessionId = localSessionId
            Result.success(localSessionId)
        }
    }

    suspend fun recordLocation(location: Location): Result<PingResponse> {
        val entity = QueuedLocationEntity(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null,
            timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        )

        val isOnline = NetworkUtils.isNetworkAvailable(context)

        if (isOnline) {
            try {
                val pingReq = PingRequest(
                    sessionId = activeSessionId,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    altitude = entity.altitude,
                    accuracy = entity.accuracy,
                    speed = entity.speed,
                    timestamp = entity.timestamp
                )
                val response = trackingApiService.pingLocation(pingReq)

                if (response.isSuccessful && response.body()?.success == true) {
                    return Result.success(response.body()!!)
                } else {
                    // Non-successful HTTP response, queue to Room for later sync
                    locationDao.insertLocation(entity)
                    return Result.success(
                        PingResponse(
                            success = false,
                            message = "Server reported status ${response.code()}, queued locally."
                        )
                    )
                }
            } catch (e: IOException) {
                // Network glitch despite capability check -> queue to Room
                locationDao.insertLocation(entity)
                return Result.success(
                    PingResponse(
                        success = false,
                        message = "Network error, stored locally in Room: ${e.localizedMessage}"
                    )
                )
            } catch (e: Exception) {
                locationDao.insertLocation(entity)
                return Result.failure(e)
            }
        } else {
            // Offline: store location in Room database
            locationDao.insertLocation(entity)
            return Result.success(
                PingResponse(
                    success = true,
                    message = "Offline: Location stored locally for future sync"
                )
            )
        }
    }

    /**
     * Synchronize queued locations stored in Room with backend.
     * Records are NEVER deleted until successful sync is confirmed by backend.
     */
    suspend fun syncQueuedLocations(): Result<Int> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.failure(Exception("Cannot sync: No internet connection"))
        }

        val unsyncedList = locationDao.getUnsyncedLocations(limit = 100)
        if (unsyncedList.isEmpty()) {
            return Result.success(0)
        }

        val request = SyncRequest(
            locations = unsyncedList.map { it.toOfflineRecord() }
        )

        return try {
            val response = trackingApiService.syncLocations(request)
            if (response.isSuccessful && response.body()?.success == true) {
                val ids = unsyncedList.map { it.id }
                // Confirm synchronization before removing from database
                locationDao.deleteLocations(ids)
                val count = response.body()?.syncedCount ?: unsyncedList.size
                Result.success(count)
            } else {
                Result.failure(Exception("Sync rejected by backend: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun endSession() {
        activeSessionId = null
    }
}
