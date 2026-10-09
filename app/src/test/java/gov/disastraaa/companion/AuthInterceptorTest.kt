package gov.disastraaa.companion

import gov.disastraaa.companion.data.api.AuthInterceptor
import gov.disastraaa.companion.data.model.User
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
    }

    @Test
    fun interceptor_attachesBearerToken_whenRequestingTargetHost() {
        fakeTokenStorage.saveAuth(
            token = "valid_citizen_jwt_token",
            user = User(id = "1", name = "Test", email = "test@disastraaa.gov", role = "citizen")
        )

        var capturedRequest: Request? = null

        val interceptor = AuthInterceptor(
            tokenStorage = fakeTokenStorage,
            expectedHost = "10.0.2.2"
        )

        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder()
                .url("http://10.0.2.2:3000/api/tracking/ping")
                .build()

            override fun proceed(request: Request): Response {
                capturedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 1000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 1000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(fakeChain)

        assertEquals("Bearer valid_citizen_jwt_token", capturedRequest?.header("Authorization"))
        assertEquals("application/json", capturedRequest?.header("Accept"))
    }

    @Test
    fun interceptor_doesNotAttachBearerToken_toExternalUnrelatedHost() {
        fakeTokenStorage.saveAuth(
            token = "sensitive_jwt_token",
            user = User(id = "1", name = "Test", email = "test@disastraaa.gov", role = "citizen")
        )

        var capturedRequest: Request? = null

        val interceptor = AuthInterceptor(
            tokenStorage = fakeTokenStorage,
            expectedHost = "10.0.2.2" // configured DISASTRAAA host
        )

        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder()
                .url("https://tile.openstreetmap.org/1/2/3.png") // external host!
                .build()

            override fun proceed(request: Request): Response {
                capturedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 1000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 1000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(fakeChain)

        // Sensitive token must NOT be leaked to third-party host
        assertNull(capturedRequest?.header("Authorization"))
    }

    @Test
    fun interceptor_clearsSession_on401FromTargetHost() {
        fakeTokenStorage.saveAuth(
            token = "expired_jwt_token",
            user = User(id = "1", name = "Test", email = "test@disastraaa.gov", role = "citizen")
        )

        var unauthorizedCallbackTriggered = false

        val interceptor = AuthInterceptor(
            tokenStorage = fakeTokenStorage,
            expectedHost = "10.0.2.2",
            onUnauthorized = { unauthorizedCallbackTriggered = true }
        )

        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder()
                .url("http://10.0.2.2:3000/api/tracking/ping")
                .build()

            override fun proceed(request: Request): Response {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"message\":\"Token expired\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 1000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 1000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(fakeChain)

        assertTrue(unauthorizedCallbackTriggered)
        assertEquals(1, fakeTokenStorage.clearCallCount)
        assertNull(fakeTokenStorage.getAccessToken())
    }

    @Test
    fun interceptor_doesNotAddAuthHeader_whenUnauthenticated() {
        var capturedRequest: Request? = null

        val interceptor = AuthInterceptor(
            tokenStorage = fakeTokenStorage,
            expectedHost = "10.0.2.2"
        )

        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder()
                .url("http://10.0.2.2:3000/api/mobile/auth/login")
                .build()

            override fun proceed(request: Request): Response {
                capturedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 1000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 1000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(fakeChain)

        assertNull(capturedRequest?.header("Authorization"))
    }
}
