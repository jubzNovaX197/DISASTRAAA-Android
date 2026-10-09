package gov.disastraaa.companion

import android.location.Location
import gov.disastraaa.companion.data.api.TrackingApiService
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.RiskAssessment
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import gov.disastraaa.companion.data.repository.LocationRepository
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LocationRepositoryTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var fakeLocationDao: FakeLocationDao

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
        fakeLocationDao = FakeLocationDao()
    }

    private fun createLocation(lat: Double, lon: Double): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lon
            time = 1700000000000L
        }
    }

    @Test
    fun startSession_blockedWhenUserIsLoggedOut() = runBlocking {
        // User is not logged in
        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                throw AssertionError("API should not be called when user is logged out")
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.startSession(consentGiven = true)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("User is not logged in", result.exceptionOrNull()?.message)
        assertNull(repository.getActiveSessionId())
    }

    @Test
    fun startSession_requiresExplicitConsent() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                throw AssertionError("API should not be called without consent")
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.startSession(consentGiven = false)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertNull(repository.getActiveSessionId())
    }

    @Test
    fun startSession_blockedWhenOffline() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { false } // Offline
        )

        val result = repository.startSession(consentGiven = true)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("No internet connection") == true)
        assertNull(repository.getActiveSessionId())
    }

    @Test
    fun startSession_successfulResponse_savesActiveSessionId() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)
        var capturedRequest: SessionRequest? = null

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                capturedRequest = request
                return Response.success(
                    SessionResponse(
                        success = true,
                        sessionId = "sess_backend_12345",
                        status = "ACTIVE"
                    )
                )
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.startSession(consentGiven = true)

        assertTrue(result.isSuccess)
        assertEquals("sess_backend_12345", result.getOrNull())
        assertEquals("sess_backend_12345", repository.getActiveSessionId())

        // Verify request payload conforms to verified backend contract
        assertNotNull(capturedRequest)
        assertEquals("START", capturedRequest?.action)
        assertEquals(true, capturedRequest?.consentGiven)
        assertNotNull(capturedRequest?.deviceInfo)
    }

    @Test
    fun startSession_backendHttp400_parsesErrorMessage() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                val errorJson = "{\"success\":false,\"message\":\"Explicit consent is required to start safety monitoring\"}"
                return Response.error(400, errorJson.toResponseBody("application/json".toMediaType()))
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.startSession(consentGiven = true)

        assertFalse(result.isSuccess)
        assertEquals("Explicit consent is required to start safety monitoring", result.exceptionOrNull()?.message)
        assertNull(repository.getActiveSessionId())
    }

    @Test
    fun startSession_networkTimeout_returnsFriendlyError() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                throw SocketTimeoutException("connect timed out")
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val result = repository.startSession(consentGiven = true)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true)
        assertNull(repository.getActiveSessionId())
    }

    @Test
    fun stopSession_sendsStopActionAndClearsSessionId() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)
        var capturedRequest: SessionRequest? = null

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> {
                capturedRequest = request
                return Response.success(
                    SessionResponse(
                        success = true,
                        sessionId = request.sessionId,
                        status = "ENDED"
                    )
                )
            }
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        repository.setActiveSessionId("sess_to_stop_999")
        val result = repository.stopSession()

        assertTrue(result.isSuccess)
        assertNull(repository.getActiveSessionId())
        assertNotNull(capturedRequest)
        assertEquals("STOP", capturedRequest?.action)
        assertEquals("sess_to_stop_999", capturedRequest?.sessionId)
    }

    @Test
    fun pingLocation_blockedWhenNoActiveSession() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
            override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
        }

        val repository = LocationRepository(
            context = null,
            trackingApiService = fakeApi,
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage,
            networkChecker = { true }
        )

        val location = createLocation(20.2961, 85.8245)
        val result = repository.pingLocation(location)

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("No active tracking session", result.exceptionOrNull()?.message)
    }

    @Test
    fun pingLocation_successfulResponse_returnsPingResponse() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)
        var capturedPing: PingRequest? = null

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> {
                capturedPing = request
                return Response.success(
                    PingResponse(
                        success = true,
                        riskAssessment = RiskAssessment(
                            inHazardZone = false,
                            riskLevel = "LOW",
                            hazardId = null,
                            title = null
                        )
                    )
                )
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

        repository.setActiveSessionId("sess_active_123")
        val location = createLocation(20.2961, 85.8245)
        val result = repository.pingLocation(location, batteryLevel = 82)

        assertTrue(result.isSuccess)
        val pingResponse = result.getOrNull()
        assertNotNull(pingResponse)
        assertEquals(true, pingResponse?.success)
        assertEquals("LOW", pingResponse?.riskAssessment?.riskLevel)
        assertEquals(false, pingResponse?.riskAssessment?.inHazardZone)

        // Verify captured ping payload
        assertNotNull(capturedPing)
        assertEquals("sess_active_123", capturedPing?.sessionId)
        assertEquals(location.latitude, capturedPing?.latitude ?: 0.0, 0.0001)
        assertEquals(location.longitude, capturedPing?.longitude ?: 0.0, 0.0001)
        assertEquals(82, capturedPing?.batteryLevel)
    }

    @Test
    fun pingLocation_http401Unauthorized_clearsSessionAndFails() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> {
                return Response.error(401, "{\"success\":false,\"message\":\"Unauthorized token\"}".toResponseBody("application/json".toMediaType()))
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

        repository.setActiveSessionId("sess_invalid_token")
        val result = repository.pingLocation(createLocation(20.2961, 85.8245))

        assertFalse(result.isSuccess)
        assertNull(repository.getActiveSessionId())
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun pingLocation_http404SessionNotFound_clearsSessionAndFails() = runBlocking {
        fakeTokenStorage.saveAuth("token_abc", null)

        val fakeApi = object : TrackingApiService {
            override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
            override suspend fun pingLocation(request: PingRequest): Response<PingResponse> {
                return Response.error(404, "{\"success\":false,\"message\":\"Session not found\"}".toResponseBody("application/json".toMediaType()))
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

        repository.setActiveSessionId("sess_expired_999")
        val result = repository.pingLocation(createLocation(20.2961, 85.8245))

        assertFalse(result.isSuccess)
        assertNull(repository.getActiveSessionId())
        assertEquals("Session not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun endSessionLocally_clearsActiveSessionId() {
        val repository = LocationRepository(
            context = null,
            trackingApiService = object : TrackingApiService {
                override suspend fun createSession(request: SessionRequest): Response<SessionResponse> = throw AssertionError()
                override suspend fun pingLocation(request: PingRequest): Response<PingResponse> = throw AssertionError()
                override suspend fun syncLocations(request: SyncRequest): Response<SyncResponse> = throw AssertionError()
            },
            locationDao = fakeLocationDao,
            tokenStorage = fakeTokenStorage
        )

        repository.setActiveSessionId("some_session_id")
        assertEquals("some_session_id", repository.getActiveSessionId())

        repository.endSessionLocally()
        assertNull(repository.getActiveSessionId())
    }
}
