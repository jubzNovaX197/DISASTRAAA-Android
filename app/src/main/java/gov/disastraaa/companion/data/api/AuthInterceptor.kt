package gov.disastraaa.companion.data.api

import gov.disastraaa.companion.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val expectedHost: String? = null,
    private val onUnauthorized: (() -> Unit)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStorage.getAccessToken()

        val requestBuilder = originalRequest.newBuilder()

        // Security check: Only attach Authorization Bearer token to requests
        // destined for the configured DISASTRAAA backend host.
        // Never send tokens to third-party or external hosts.
        val isTargetHost = expectedHost.isNullOrBlank() || originalRequest.url.host.equals(expectedHost, ignoreCase = true)

        if (isTargetHost && !token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        requestBuilder.addHeader("Accept", "application/json")
        requestBuilder.addHeader("Content-Type", "application/json")

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401 && isTargetHost) {
            // Token is invalid or expired
            tokenStorage.clear()
            onUnauthorized?.invoke()
        }

        return response
    }
}
