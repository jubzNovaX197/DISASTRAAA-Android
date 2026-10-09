package gov.disastraaa.companion

import gov.disastraaa.companion.data.api.AuthApiService
import gov.disastraaa.companion.data.model.LoginRequest
import gov.disastraaa.companion.data.model.LoginResponse
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthRepositoryTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
    }

    @Test
    fun login_success_savesTokenAndUserToStorage() = runBlocking {
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                val responseBody = LoginResponse(
                    success = true,
                    accessToken = "sample_access_token_123",
                    user = User(
                        id = "user-1",
                        name = "Sarah Connor",
                        email = "sarah@disastraaa.gov",
                        role = "CITIZEN"
                    )
                )
                return Response.success(responseBody)
            }
        }

        val repository = AuthRepository(
            authApiService = fakeApi,
            tokenStorage = fakeTokenStorage
        )

        val result = repository.login("sarah@disastraaa.gov", "password123")

        assertTrue(result.isSuccess)
        val loginResponse = result.getOrNull()
        assertNotNull(loginResponse)
        assertEquals("sample_access_token_123", loginResponse?.accessToken)
        assertEquals("sample_access_token_123", fakeTokenStorage.getAccessToken())
        assertEquals("Sarah Connor", fakeTokenStorage.getUser()?.name)
        assertEquals("CITIZEN", fakeTokenStorage.getUser()?.role)
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun login_401Unauthorized_parsesBackendErrorMessage() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Invalid credentials. Please verify your email and password.\"}"
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                return Response.error(401, errorBody)
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "wrong_password")

        assertTrue(result.isFailure)
        assertEquals("Invalid credentials. Please verify your email and password.", result.exceptionOrNull()?.message)
        assertFalse(repository.isLoggedIn())
        assertNull(fakeTokenStorage.getAccessToken())
    }

    @Test
    fun login_500ServerError_returnsFriendlyErrorMessage() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Internal database connection failure\"}"
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                return Response.error(500, errorBody)
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "password")

        assertTrue(result.isFailure)
        assertEquals("Internal database connection failure", result.exceptionOrNull()?.message)
    }

    @Test
    fun login_missingAccessToken_failsGracefully() = runBlocking {
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                // Success is true but accessToken is null
                return Response.success(LoginResponse(success = true, accessToken = null))
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "password")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("access token", ignoreCase = true) == true)
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun login_connectException_returnsServerUnreachableError() = runBlocking {
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                throw ConnectException("Connection refused")
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "password")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Cannot connect to DISASTRAAA server") == true)
    }

    @Test
    fun login_unknownHostException_returnsDnsError() = runBlocking {
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                throw UnknownHostException("Unable to resolve host")
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "password")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("check internet connection") == true)
    }

    @Test
    fun login_timeoutException_returnsTimeoutMessage() = runBlocking {
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                throw SocketTimeoutException("Read timed out")
            }
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        val result = repository.login("user@test.org", "password")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Connection timed out") == true)
    }

    @Test
    fun logout_clearsStoredTokenAndSession() {
        fakeTokenStorage.saveAuth(
            token = "active_token",
            user = User(id = "1", name = "Test", email = "test@disastraaa.gov", role = "CITIZEN")
        )

        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> = throw UnsupportedOperationException()
        }

        val repository = AuthRepository(fakeApi, fakeTokenStorage)
        assertTrue(repository.isLoggedIn())

        repository.logout()

        assertFalse(repository.isLoggedIn())
        assertNull(repository.getCurrentUser())
        assertEquals(1, fakeTokenStorage.clearCallCount)
    }
}
