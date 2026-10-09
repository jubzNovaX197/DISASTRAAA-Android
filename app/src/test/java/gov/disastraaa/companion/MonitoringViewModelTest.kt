package gov.disastraaa.companion

import gov.disastraaa.companion.data.api.AuthApiService
import gov.disastraaa.companion.data.api.TrackingApiService
import gov.disastraaa.companion.data.model.LoginRequest
import gov.disastraaa.companion.data.model.LoginResponse
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
import gov.disastraaa.companion.data.repository.LocationRepository
import gov.disastraaa.companion.service.TrackingStatus
import gov.disastraaa.companion.ui.monitoring.MonitoringViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MonitoringViewModelTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var fakeLocationDao: FakeLocationDao
    private lateinit var authRepository: AuthRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
        fakeLocationDao = FakeLocationDao()

        authRepository = AuthRepository(
            authApiService = object : AuthApiService {
                override suspend fun login(request: LoginRequest): Response<LoginResponse> = throw AssertionError()
            },
            tokenStorage = fakeTokenStorage
        )

        locationRepository = LocationRepository(
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
    }

    @Test
    fun uiState_initialStateReflectsLoggedOutUser() {
        val viewModel = MonitoringViewModel(authRepository, locationRepository)
        val state = viewModel.uiState.value

        assertEquals("Citizen", state.userName)
        assertEquals("", state.userEmail)
        assertEquals(TrackingStatus.NOT_TRACKING, state.trackingStatus)
        assertFalse(state.isMonitoringActive)
        assertFalse(state.isActionInProgress)
        assertNull(state.sessionId)
        assertNull(state.lastLatitude)
    }

    @Test
    fun uiState_initialStateReflectsLoggedInUser() {
        fakeTokenStorage.saveAuth(
            "token_xyz",
            User(
                id = "u-42",
                name = "Amiya Patnaik",
                email = "amiya@disastraaa.gov",
                role = "NDRF_RESPONDER"
            )
        )

        val viewModel = MonitoringViewModel(authRepository, locationRepository)
        val state = viewModel.uiState.value

        assertEquals("Amiya Patnaik", state.userName)
        assertFalse(state.isMonitoringActive)
    }

    @Test
    fun logout_clearsAuthAndLocalSession() {
        fakeTokenStorage.saveAuth(
            "token_active",
            User(id = "u-1", name = "Test User", email = "test@disastraaa.gov", role = "CITIZEN")
        )
        locationRepository.setActiveSessionId("sess_to_clear")
        assertTrue(authRepository.isLoggedIn())
        assertEquals("sess_to_clear", locationRepository.getActiveSessionId())

        locationRepository.endSessionLocally()
        authRepository.logout()

        assertFalse(authRepository.isLoggedIn())
        assertNull(locationRepository.getActiveSessionId())
        assertNull(fakeTokenStorage.getAccessToken())
        assertNull(fakeTokenStorage.getUser())
    }
}
