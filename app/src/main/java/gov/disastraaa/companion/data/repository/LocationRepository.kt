package gov.disastraaa.companion.data.repository

import android.content.Context
import android.location.Location
import android.os.Build
import com.google.gson.JsonParser
import gov.disastraaa.companion.data.api.TrackingApiService
import gov.disastraaa.companion.data.local.TokenStorage
import gov.disastraaa.companion.data.local.dao.LocationDao
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import gov.disastraaa.companion.data.model.DeviceInfo
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import gov.disastraaa.companion.sync.SyncLocationsWorker
import gov.disastraaa.companion.utils.Constants
import gov.disastraaa.companion.utils.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ResponseBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

sealed class LocationUploadResult {
    data class LiveDelivered(val response: PingResponse) : LocationUploadResult()
    data class QueuedLocally(val queueId: Long, val reason: String) : LocationUploadResult()
    data class Rejected(val error: Throwable) : LocationUploadResult()
}

enum class SyncStatus {
    IDLE,
    SYNCING,
    WAITING_TO_SYNC,
    SUCCESS,
    ERROR
}

class OfflineQueuedException(message: String, val queueId: Long) : IOException(message)

class LocationRepository(
    private val context: Context? = null,
    private val trackingApiService: TrackingApiService,
    private val locationDao: LocationDao,
    private val tokenStorage: TokenStorage,
    private val networkChecker: (() -> Boolean)? = null
) {

    private var activeSessionId: String? = null

    val unsyncedCountFlow: Flow<Int> = locationDao.observeUnsyncedCount()

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimeMillis = MutableStateFlow<Long?>(null)
    val lastSyncTimeMillis: StateFlow<Long?> = _lastSyncTimeMillis.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    suspend fun getUnsyncedCount(): Int = locationDao.getUnsyncedCount()

    suspend fun getUnsyncedCountForUser(userId: String): Int = locationDao.getUnsyncedCountForUser(userId)

    fun getActiveSessionId(): String? = activeSessionId

    fun setActiveSessionId(sessionId: String?) {
        activeSessionId = sessionId
    }

    private fun isOnline(): Boolean {
        return networkChecker?.invoke() ?: (context?.let { NetworkUtils.isNetworkAvailable(it) } ?: true)
    }

    private fun getCurrentUserId(): String {
        return tokenStorage.getUser()?.id ?: ""
    }

    suspend fun startSession(consentGiven: Boolean = true): Result<String> {
        if (!tokenStorage.isLoggedIn()) {
            return Result.failure(IllegalStateException("User is not logged in"))
        }

        if (!consentGiven) {
            return Result.failure(IllegalArgumentException("Explicit consent is required to start safety monitoring"))
        }

        if (!isOnline()) {
            return Result.failure(IOException("Cannot start monitoring: No internet connection"))
        }

        val request = SessionRequest(
            action = "START",
            consentGiven = true,
            deviceInfo = DeviceInfo(
                model = Build.MODEL ?: "Android Device",
                osVersion = "Android ${Build.VERSION.RELEASE ?: ""}".trim(),
                appVersion = "1.0"
            )
        )

        return try {
            val response = trackingApiService.createSession(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && !body.sessionId.isNullOrBlank()) {
                    activeSessionId = body.sessionId
                    Result.success(body.sessionId)
                } else {
                    val errorMsg = body?.message ?: "Failed to start tracking session (unsuccessful response)"
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = parseErrorMessage(
                    response.errorBody(),
                    when (response.code()) {
                        400 -> "Invalid tracking request (400)"
                        401 -> "Session unauthorized or expired (401)"
                        403 -> "Tracking access denied (403)"
                        404 -> "Tracking service not found (404)"
                        500 -> "DISASTRAAA server error (500)"
                        else -> "Failed to start session (${response.code()})"
                    }
                )
                Result.failure(Exception(errorMsg))
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("Cannot reach DISASTRAAA server. Check your network.", e))
        } catch (e: ConnectException) {
            Result.failure(IOException("Cannot connect to DISASTRAAA server: ${e.message}", e))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Connection timed out while contacting DISASTRAAA server.", e))
        } catch (e: IOException) {
            Result.failure(IOException("Network error while creating tracking session: ${e.localizedMessage}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopSession(): Result<Unit> {
        val currentSessionId = activeSessionId
        activeSessionId = null

        if (currentSessionId.isNullOrBlank()) {
            return Result.success(Unit)
        }

        if (!tokenStorage.isLoggedIn()) {
            return Result.success(Unit)
        }

        if (!isOnline()) {
            return Result.success(Unit)
        }

        return try {
            val request = SessionRequest(
                action = "STOP",
                sessionId = currentSessionId
            )
            val response = trackingApiService.createSession(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.success(Unit)
            }
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * Persists a location ping locally to Room offline queue when internet is unavailable or fails.
     * Enforces bounded retention (MAX_OFFLINE_QUEUE_CAPACITY).
     */
    suspend fun enqueueOfflineLocation(location: Location): Long {
        val currentUserId = getCurrentUserId()
        val sid = activeSessionId ?: ""

        // Enforce bounded queue capacity: prune oldest records if overflow
        val total = locationDao.getTotalRecordCount()
        if (total >= Constants.MAX_OFFLINE_QUEUE_CAPACITY) {
            val excess = (total - Constants.MAX_OFFLINE_QUEUE_CAPACITY) + 1
            locationDao.pruneOldestRecords(excess)
        }

        val entity = QueuedLocationEntity(
            userId = currentUserId,
            sessionId = sid,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed else null,
            timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
            isSynced = false
        )

        val id = locationDao.insertLocation(entity)
        _syncStatus.value = SyncStatus.WAITING_TO_SYNC

        // Schedule WorkManager one-time sync so it executes automatically when connectivity returns
        context?.let { ctx ->
            try {
                SyncLocationsWorker.enqueueOneTimeSync(ctx)
            } catch (_: Exception) {}
        }

        return id
    }

    /**
     * Core location dispatch method:
     * - If online and ping succeeds: LiveDelivered
     * - If offline or network/server exception: QueuedLocally
     * - If permanent failure (e.g. not logged in, no session): Rejected
     */
    suspend fun recordLocationDetailed(location: Location, batteryLevel: Int? = null): LocationUploadResult {
        val sessionId = activeSessionId
            ?: return LocationUploadResult.Rejected(IllegalStateException("No active tracking session"))

        if (!tokenStorage.isLoggedIn()) {
            activeSessionId = null
            return LocationUploadResult.Rejected(IllegalStateException("User is not logged in"))
        }

        // If explicitly offline, queue locally immediately
        if (!isOnline()) {
            val qId = enqueueOfflineLocation(location)
            return LocationUploadResult.QueuedLocally(qId, "Offline: Location stored locally in queue")
        }

        val pingReq = PingRequest(
            sessionId = sessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            batteryLevel = batteryLevel,
            timestamp = try {
                Instant.ofEpochMilli(if (location.time > 0) location.time else System.currentTimeMillis()).toString()
            } catch (_: Exception) {
                null
            }
        )

        return try {
            val response = trackingApiService.pingLocation(pingReq)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    LocationUploadResult.LiveDelivered(body)
                } else {
                    val errorMsg = body?.message ?: "Location ping rejected by server"
                    LocationUploadResult.Rejected(Exception(errorMsg))
                }
            } else {
                val code = response.code()
                if (code == 401) {
                    activeSessionId = null
                    LocationUploadResult.Rejected(Exception("Session expired (401 Unauthorized)"))
                } else if (code == 404 || code == 400 || code == 403) {
                    val errorMsg = parseErrorMessage(response.errorBody(), "Session invalid ($code)")
                    activeSessionId = null
                    LocationUploadResult.Rejected(Exception(errorMsg))
                } else if (code >= 500) {
                    // Server error: queue locally for later sync
                    val qId = enqueueOfflineLocation(location)
                    LocationUploadResult.QueuedLocally(qId, "Server error ($code): Queued locally")
                } else {
                    LocationUploadResult.Rejected(Exception("HTTP error $code"))
                }
            }
        } catch (e: UnknownHostException) {
            val qId = enqueueOfflineLocation(location)
            LocationUploadResult.QueuedLocally(qId, "Cannot reach server: Queued locally")
        } catch (e: ConnectException) {
            val qId = enqueueOfflineLocation(location)
            LocationUploadResult.QueuedLocally(qId, "Connection failed: Queued locally")
        } catch (e: SocketTimeoutException) {
            val qId = enqueueOfflineLocation(location)
            LocationUploadResult.QueuedLocally(qId, "Connection timed out: Queued locally")
        } catch (e: IOException) {
            val qId = enqueueOfflineLocation(location)
            LocationUploadResult.QueuedLocally(qId, "Network glitch: Queued locally")
        } catch (e: Exception) {
            LocationUploadResult.Rejected(e)
        }
    }

    /**
     * Backward-compatible pingLocation for tests and services.
     * Enqueues locally on network failure and returns failure with OfflineQueuedException.
     */
    suspend fun pingLocation(location: Location, batteryLevel: Int? = null): Result<PingResponse> {
        val result = recordLocationDetailed(location, batteryLevel)
        return when (result) {
            is LocationUploadResult.LiveDelivered -> Result.success(result.response)
            is LocationUploadResult.QueuedLocally -> Result.failure(OfflineQueuedException(result.reason, result.queueId))
            is LocationUploadResult.Rejected -> Result.failure(result.error)
        }
    }

    suspend fun recordLocation(location: Location): Result<PingResponse> {
        return pingLocation(location)
    }

    fun endSession() {
        endSessionLocally()
    }

    fun endSessionLocally() {
        activeSessionId = null
    }

    /**
     * Synchronize queued locations stored in Room with backend.
     * Scoped strictly by user ID for privacy and session isolation.
     * Handles batching, partial server acknowledgements, and retry limits.
     */
    suspend fun syncQueuedLocations(targetUserId: String? = null): Result<Int> {
        if (!isOnline()) {
            _syncStatus.value = SyncStatus.WAITING_TO_SYNC
            return Result.failure(IOException("Cannot sync: No internet connection"))
        }

        if (!tokenStorage.isLoggedIn()) {
            _syncStatus.value = SyncStatus.ERROR
            return Result.failure(IllegalStateException("Cannot sync: User is not logged in"))
        }

        val userId = targetUserId ?: getCurrentUserId()
        if (userId.isBlank()) {
            _syncStatus.value = SyncStatus.ERROR
            return Result.failure(IllegalStateException("No authenticated user for sync"))
        }

        _syncStatus.value = SyncStatus.SYNCING
        _lastSyncError.value = null

        var totalSynced = 0
        var hasPermanentError = false
        var permanentErrorMessage: String? = null

        try {
            // Find distinct sessions with pending records for this user
            val pendingSessions = locationDao.getUnsyncedSessionsForUser(userId)

            if (pendingSessions.isEmpty()) {
                // Also check if any records have blank sessionId (legacy or direct enqueue)
                val orphanRecords = locationDao.getUnsyncedLocationsForUser(userId, limit = 50)
                if (orphanRecords.isEmpty()) {
                    _syncStatus.value = SyncStatus.SUCCESS
                    return Result.success(0)
                }

                // If activeSessionId is available, associate and sync orphan records
                val sid = activeSessionId
                if (sid.isNullOrBlank()) {
                    // Cannot sync without a session ID per backend contract
                    _syncStatus.value = SyncStatus.WAITING_TO_SYNC
                    return Result.success(0)
                }

                val syncReq = SyncRequest(
                    sessionId = sid,
                    locations = orphanRecords.map { it.toOfflineRecord() }
                )
                val response = trackingApiService.syncLocations(syncReq)
                if (response.isSuccessful && response.body()?.success == true) {
                    val count = response.body()?.syncedCount ?: orphanRecords.size
                    val acknowledged = orphanRecords.take(count).map { it.id }
                    locationDao.deleteLocations(acknowledged)
                    totalSynced += acknowledged.size
                }
            } else {
                for (sessionId in pendingSessions) {
                    val batch = locationDao.getUnsyncedLocationsForSession(userId, sessionId, limit = 50)
                    if (batch.isEmpty()) continue

                    val syncReq = SyncRequest(
                        sessionId = sessionId,
                        locations = batch.map { it.toOfflineRecord() }
                    )

                    val response = trackingApiService.syncLocations(syncReq)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val count = response.body()?.syncedCount ?: batch.size
                        // Atomically acknowledge and delete ONLY records confirmed by backend
                        val acknowledged = batch.take(count).map { it.id }
                        locationDao.deleteLocations(acknowledged)
                        totalSynced += acknowledged.size
                    } else {
                        val code = response.code()
                        val errorBody = parseErrorMessage(response.errorBody(), "Sync failed ($code)")

                        if (code == 401) {
                            // Expired token: do NOT delete queued records!
                            _syncStatus.value = SyncStatus.ERROR
                            _lastSyncError.value = "Authentication token expired during sync"
                            return Result.failure(Exception("Authentication session expired (401)"))
                        } else if (code == 404 || code == 403 || code == 400) {
                            // Session ended or permanent validation issue
                            locationDao.incrementRetryCount(batch.map { it.id })
                            // Prune records that have exceeded max retry attempts to prevent endless queue clog
                            val deadLetters = batch.filter { (it.retryCount + 1) >= Constants.MAX_SYNC_RETRY_COUNT }.map { it.id }
                            if (deadLetters.isNotEmpty()) {
                                locationDao.deleteLocations(deadLetters)
                            }
                            _syncStatus.value = SyncStatus.ERROR
                            _lastSyncError.value = errorBody
                            hasPermanentError = true
                            permanentErrorMessage = errorBody
                        } else {
                            // Retryable 5xx server error
                            _syncStatus.value = SyncStatus.WAITING_TO_SYNC
                            _lastSyncError.value = "Server error ($code). Will retry."
                            return Result.failure(Exception("Sync server error: $code"))
                        }
                    }
                }
            }

            if (hasPermanentError) {
                _syncStatus.value = SyncStatus.ERROR
                _lastSyncError.value = permanentErrorMessage
                return Result.failure(Exception(permanentErrorMessage ?: "Permanent validation error during sync"))
            }

            _lastSyncTimeMillis.value = System.currentTimeMillis()
            _syncStatus.value = SyncStatus.SUCCESS
            return Result.success(totalSynced)

        } catch (e: UnknownHostException) {
            _syncStatus.value = SyncStatus.WAITING_TO_SYNC
            _lastSyncError.value = "Network unreachable. Waiting for connection."
            return Result.failure(e)
        } catch (e: SocketTimeoutException) {
            _syncStatus.value = SyncStatus.WAITING_TO_SYNC
            _lastSyncError.value = "Sync timed out. Will retry."
            return Result.failure(e)
        } catch (e: IOException) {
            _syncStatus.value = SyncStatus.WAITING_TO_SYNC
            _lastSyncError.value = "Connection error. Will retry."
            return Result.failure(e)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.ERROR
            _lastSyncError.value = e.localizedMessage
            return Result.failure(e)
        }
    }

    private fun parseErrorMessage(errorBody: ResponseBody?, fallback: String): String {
        if (errorBody == null) return fallback
        return try {
            val errorJson = errorBody.string()
            if (errorJson.isBlank()) return fallback
            val jsonObject = JsonParser.parseString(errorJson).asJsonObject
            when {
                jsonObject.has("message") && !jsonObject.get("message").isJsonNull -> {
                    jsonObject.get("message").asString
                }
                jsonObject.has("error") && !jsonObject.get("error").isJsonNull -> {
                    jsonObject.get("error").asString
                }
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
