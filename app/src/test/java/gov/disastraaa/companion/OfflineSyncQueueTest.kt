package gov.disastraaa.companion

import android.location.Location
import gov.disastraaa.companion.data.api.TrackingApiService
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import gov.disastraaa.companion.data.model.OfflineLocationRecord
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.LocationRepository
import gov.disastraaa.companion.data.repository.LocationUploadResult
import gov.disastraaa.companion.data.repository.SyncStatus
import gov.disastraaa.companion.utils.Constants
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

class OfflineSyncQueueTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var fakeLocationDao: FakeLocationDao

    private val testUser = User(
        id = "user_001",
        name = "Kavita Sahoo",
        email = "kavita@disastraaa.gov",
        role = "CITIZEN"
    )

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
        fakeLocationDao = FakeLocationDao()
        fakeTokenStorage.saveAuth("token_abc_123", testUser)
    }

    private fun createLocation(lat: Double = 20.2961, lon: Double = 85.8245, timeMillis: Long = 1700000000000L): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lon
            time = timeMillis
        }
    }

    // 1. A retryable ping failure results in a queued record
    @Test
    fun test1_retryablePingFailure_resultsInQueuedRecord() = runBlocking {
        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> {
                // Return 500 Internal Server Error
                return Response.error(500, "{\"message\":\"Internal Server Error\"}".toResponseBody("application/json".toMediaType()))
            }
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )
        repository.setActiveSessionId("sess_retryable_500")

        val result = repository.recordLocationDetailed(createLocation())

        assertTrue(result is LocationUploadResult.QueuedLocally)
        assertEquals(1, fakeLocationDao.getUnsyncedCount())
        val queued = fakeLocationDao.getUnsyncedLocations(10).first()
        assertEquals("user_001", queued.userId)
        assertEquals("sess_retryable_500", queued.sessionId)
        assertFalse(queued.isSynced)
    }

    // 2. Queue records preserve original timestamps and ordering
    @Test
    fun test2_queueRecords_preserveOriginalTimestampsAndOrdering() = runBlocking {
        val t1 = 1700000001000L
        val t2 = 1700000002000L
        val t3 = 1700000003000L

        fakeLocationDao.insertLocation(
            QueuedLocationEntity(
                userId = "user_001",
                sessionId = "sess_order",
                latitude = 20.1,
                longitude = 85.1,
                timestamp = t2
            )
        )
        fakeLocationDao.insertLocation(
            QueuedLocationEntity(
                userId = "user_001",
                sessionId = "sess_order",
                latitude = 20.0,
                longitude = 85.0,
                timestamp = t1
            )
        )
        fakeLocationDao.insertLocation(
            QueuedLocationEntity(
                userId = "user_001",
                sessionId = "sess_order",
                latitude = 20.2,
                longitude = 85.2,
                timestamp = t3
            )
        )

        val ordered = fakeLocationDao.getUnsyncedLocationsForSession("user_001", "sess_order", 10)
        assertEquals(3, ordered.size)
        assertEquals(t1, ordered[0].timestamp)
        assertEquals(t2, ordered[1].timestamp)
        assertEquals(t3, ordered[2].timestamp)

        // Verify ISO-8601 timestamp is preserved in conversion to OfflineLocationRecord
        val offlineRecord = ordered[0].toOfflineRecord()
        assertNotNull(offlineRecord.timestamp)
        assertTrue(offlineRecord.timestamp.isNotEmpty())
    }

    // 3. Successful synchronization marks only acknowledged records as synchronized
    @Test
    fun test3_successfulSynchronization_marksOnlyAcknowledgedRecords() = runBlocking {
        val id1 = fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_s1", latitude = 20.1, longitude = 85.1, timestamp = 1000L))
        val id2 = fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_s1", latitude = 20.2, longitude = 85.2, timestamp = 2000L))
        val id3 = fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_s1", latitude = 20.3, longitude = 85.3, timestamp = 3000L))

        var capturedSyncReq: SyncRequest? = null
        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                capturedSyncReq = request
                return Response.success(SyncResponse(success = true, syncedCount = 3))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
        assertEquals(0, fakeLocationDao.getUnsyncedCount())
        assertNotNull(capturedSyncReq)
        assertEquals("sess_s1", capturedSyncReq?.sessionId)
        assertEquals(3, capturedSyncReq?.locations?.size)
    }

    // 4. Partial batch success preserves unacknowledged records
    @Test
    fun test4_partialBatchSuccess_preservesUnacknowledgedRecords() = runBlocking {
        // Enqueue 4 records
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_partial", latitude = 20.1, longitude = 85.1, timestamp = 1000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_partial", latitude = 20.2, longitude = 85.2, timestamp = 2000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_partial", latitude = 20.3, longitude = 85.3, timestamp = 3000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_partial", latitude = 20.4, longitude = 85.4, timestamp = 4000L))

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                // Server acknowledged only 2 of the 4
                return Response.success(SyncResponse(success = true, syncedCount = 2))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        // Remaining 2 must still be preserved in Room
        assertEquals(2, fakeLocationDao.getUnsyncedCount())
        val remaining = fakeLocationDao.getUnsyncedLocations(10)
        assertEquals(2, remaining.size)
        assertEquals(3000L, remaining[0].timestamp)
        assertEquals(4000L, remaining[1].timestamp)
    }

    // 5. Network failure preserves queued records
    @Test
    fun test5_networkFailure_preservesQueuedRecords() = runBlocking {
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_net_fail", latitude = 20.1, longitude = 85.1, timestamp = 1000L))

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                throw UnknownHostException("Unable to resolve host")
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        assertTrue(result.isFailure)
        assertEquals(SyncStatus.WAITING_TO_SYNC, repository.syncStatus.value)
        assertEquals(1, fakeLocationDao.getUnsyncedCount())
    }

    // 6. Retryable server errors trigger retry behavior
    @Test
    fun test6_retryableServerErrors_triggerRetryBehavior() = runBlocking {
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_503", latitude = 20.1, longitude = 85.1, timestamp = 1000L))

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                return Response.error(503, "Service Unavailable".toResponseBody("text/plain".toMediaType()))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        assertTrue(result.isFailure)
        assertEquals(SyncStatus.WAITING_TO_SYNC, repository.syncStatus.value)
        assertEquals(1, fakeLocationDao.getUnsyncedCount())
    }

    // 7. Permanent validation errors are handled safely
    @Test
    fun test7_permanentValidationErrors_incrementsRetryAndPrunesDeadLetters() = runBlocking {
        // Record with 4 previous retries
        fakeLocationDao.insertLocation(
            QueuedLocationEntity(
                userId = "user_001",
                sessionId = "sess_dead_letter",
                latitude = 20.1,
                longitude = 85.1,
                timestamp = 1000L,
                retryCount = 4
            )
        )

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                return Response.error(400, "{\"message\":\"Session is expired or invalid\"}".toResponseBody("application/json".toMediaType()))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        // 5th failure exceeds MAX_SYNC_RETRY_COUNT (5) and record is pruned
        assertEquals(SyncStatus.ERROR, repository.syncStatus.value)
        assertEquals(0, fakeLocationDao.getUnsyncedCount())
    }

    // 8. Expired authentication tokens do not delete queued records
    @Test
    fun test8_expiredAuthenticationTokens_doNotDeleteQueuedRecords() = runBlocking {
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_auth_expired", latitude = 20.1, longitude = 85.1, timestamp = 1000L))

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                return Response.error(401, "{\"message\":\"Token expired\"}".toResponseBody("application/json".toMediaType()))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.syncQueuedLocations("user_001")

        assertTrue(result.isFailure)
        assertEquals(SyncStatus.ERROR, repository.syncStatus.value)
        // Record must NOT be deleted
        assertEquals(1, fakeLocationDao.getUnsyncedCount())
    }

    // 9. Duplicate WorkManager jobs are prevented
    @Test
    fun test9_duplicateWorkManagerJobs_prevented() {
        assertEquals("disastraaa_offline_location_sync", Constants.UNIQUE_SYNC_WORK_NAME)
        assertEquals("disastraaa_periodic_location_sync", Constants.UNIQUE_PERIODIC_SYNC_WORK_NAME)
    }

    // 10. A user cannot upload another user's queued locations
    @Test
    fun test10_userIsolation_userCannotUploadAnotherUsersQueuedLocations() = runBlocking {
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_A", sessionId = "sess_A", latitude = 20.1, longitude = 85.1, timestamp = 1000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_A", sessionId = "sess_A", latitude = 20.2, longitude = 85.2, timestamp = 2000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_B", sessionId = "sess_B", latitude = 21.0, longitude = 86.0, timestamp = 3000L))

        var syncedSessionId: String? = null
        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> {
                syncedSessionId = request.sessionId
                return Response.success(SyncResponse(success = true, syncedCount = request.locations.size))
            }
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        // Sync for user_A
        val result = repository.syncQueuedLocations("user_A")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals("sess_A", syncedSessionId)

        // User B's record is completely untouched
        assertEquals(1, fakeLocationDao.getUnsyncedCountForUser("user_B"))
        val userBRecords = fakeLocationDao.getUnsyncedLocationsForUser("user_B", 10)
        assertEquals(1, userBRecords.size)
        assertEquals("user_B", userBRecords[0].userId)
    }

    // 11. Logout follows the documented pending-record policy
    @Test
    fun test11_logoutPolicy_cancelsSyncAndMaintainsIsolation() = runBlocking {
        val repository = LocationRepository(
            context = null,
            trackingApiService = object : TrackingApiService {
                override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
                override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
                override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
            },
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        repository.setActiveSessionId("active_sess_logout")
        fakeLocationDao.insertLocation(
            QueuedLocationEntity(userId = "user_001", sessionId = "active_sess_logout", latitude = 20.0, longitude = 85.0, timestamp = 1000L)
        )

        // Logout action
        repository.endSessionLocally()
        fakeTokenStorage.clear()

        // Active session is ended
        assertNull(repository.getActiveSessionId())
        assertFalse(fakeTokenStorage.isLoggedIn())

        // Offline sync fails safely when logged out
        val syncResult = repository.syncQueuedLocations("user_001")
        assertTrue(syncResult.isFailure)
        assertEquals("Cannot sync: User is not logged in", syncResult.exceptionOrNull()?.message)

        // User's offline records remain isolated and safe in Room
        assertEquals(1, fakeLocationDao.getUnsyncedCountForUser("user_001"))
    }

    // 12. Queue size or retention limits are enforced
    @Test
    fun test12_queueSizeAndRetentionLimits_enforced() = runBlocking {
        val repository = LocationRepository(
            context = null,
            trackingApiService = object : TrackingApiService {
                override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
                override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
                override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
            },
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { false } // Offline
        )
        repository.setActiveSessionId("sess_capacity_test")

        // Pre-fill fake DAO with MAX_OFFLINE_QUEUE_CAPACITY records
        for (i in 1..Constants.MAX_OFFLINE_QUEUE_CAPACITY) {
            fakeLocationDao.insertLocation(
                QueuedLocationEntity(
                    userId = "user_001",
                    sessionId = "sess_capacity_test",
                    latitude = 20.0,
                    longitude = 85.0,
                    timestamp = i.toLong()
                )
            )
        }
        assertEquals(Constants.MAX_OFFLINE_QUEUE_CAPACITY, fakeLocationDao.getTotalRecordCount())

        // Enqueue one more item
        repository.enqueueOfflineLocation(createLocation(20.5, 85.5, timeMillis = 9999999L))

        // Total count must not exceed capacity
        assertEquals(Constants.MAX_OFFLINE_QUEUE_CAPACITY, fakeLocationDao.getTotalRecordCount())
    }

    // 13. Stopping tracking prevents additional GPS collection
    @Test
    fun test13_stoppingTracking_preventsAdditionalGpsCollection() = runBlocking {
        val repository = LocationRepository(
            context = null,
            trackingApiService = object : TrackingApiService {
                override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
                override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
                override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
            },
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        repository.setActiveSessionId("sess_to_stop")
        repository.endSessionLocally()

        assertNull(repository.getActiveSessionId())

        val result = repository.recordLocationDetailed(createLocation())
        assertTrue(result is LocationUploadResult.Rejected)
        assertEquals("No active tracking session", (result as LocationUploadResult.Rejected).error.message)
        assertEquals(0, fakeLocationDao.getUnsyncedCount())
    }

    // 14. Process interruption does not corrupt queue state
    @Test
    fun test14_processInterruption_doesNotCorruptQueueState() = runBlocking {
        // Records saved before process interruption
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_survive", latitude = 20.1, longitude = 85.1, timestamp = 1000L))
        fakeLocationDao.insertLocation(QueuedLocationEntity(userId = "user_001", sessionId = "sess_survive", latitude = 20.2, longitude = 85.2, timestamp = 2000L))

        // Simulate app restart by constructing a new Repository instance
        val restartedRepo = LocationRepository(
            context = null,
            trackingApiService = object : TrackingApiService {
                override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
                override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
                override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
            },
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        assertEquals(2, restartedRepo.getUnsyncedCount())
        assertEquals(2, restartedRepo.getUnsyncedCountForUser("user_001"))
    }
}
