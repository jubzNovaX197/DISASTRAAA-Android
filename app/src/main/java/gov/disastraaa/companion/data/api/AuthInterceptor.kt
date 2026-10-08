package gov.disastraaa.companion.data.api

import gov.disastraaa.companion.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val onUnauthorized: (() -> Unit)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getAccessToken()

        val requestBuilder = originalRequest.newBuilder()

        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        requestBuilder.addHeader("Accept", "application/json")
        requestBuilder.addHeader("Content-Type", "application/json")

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401) {
            // Token is invalid or expired
            tokenManager.clear()
            onUnauthorized?.invoke()
        }

        return response
    }
}
