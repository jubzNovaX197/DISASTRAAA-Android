package gov.disastraaa.companion.data.repository

import gov.disastraaa.companion.data.api.AuthApiService
import gov.disastraaa.companion.data.local.TokenManager
import gov.disastraaa.companion.data.model.LoginRequest
import gov.disastraaa.companion.data.model.LoginResponse
import gov.disastraaa.companion.data.model.User
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

import gov.disastraaa.companion.data.local.TokenStorage

class AuthRepository(
    private val authApiService: AuthApiService,
    private val tokenStorage: TokenStorage
) {

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        val trimmedEmail = email.trim()
        return try {
            val response = authApiService.login(LoginRequest(trimmedEmail, password))

            if (response.isSuccessful) {
                val body = response.body()
                when {
                    body == null -> {
                        Result.failure(Exception("Login failed: Empty response received from server."))
                    }
                    !body.success -> {
                        val message = body.message ?: body.error ?: "Authentication failed."
                        Result.failure(Exception(message))
                    }
                    body.accessToken.isNullOrBlank() -> {
                        Result.failure(Exception("Login failed: Server response did not contain an access token."))
                    }
                    else -> {
                        val user = body.user ?: User(
                            id = null,
                            name = trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                            email = trimmedEmail,
                            role = "CITIZEN"
                        )
                        tokenStorage.saveAuth(
                            token = body.accessToken,
                            user = user
                        )
                        Result.success(body)
                    }
                }
            } else {
                val rawError = try {
                    response.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                val serverMsg = parseErrorMessage(rawError)
                val fallbackMsg = when (response.code()) {
                    400 -> "Invalid login request. Please verify email and password."
                    401 -> "Invalid email or password. Please verify your credentials."
                    403 -> "Account is unauthorized for companion access."
                    404 -> "Authentication service endpoint not found."
                    in 500..599 -> "DISASTRAAA server error (${response.code()}). Please try again later."
                    else -> "Authentication failed with status ${response.code()}."
                }
                Result.failure(Exception(serverMsg ?: fallbackMsg))
            }
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Cannot connect to DISASTRAAA server. Verify the server is running and accessible."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Cannot reach DISASTRAAA server. Please check internet connection."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out. Server is taking too long to respond."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: Unable to connect to DISASTRAAA backend."))
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP error ${e.code()}: ${e.message()}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected login error: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    private fun parseErrorMessage(jsonString: String?): String? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = com.google.gson.JsonParser.parseString(jsonString).asJsonObject
            when {
                json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                json.has("error") && !json.get("error").isJsonNull -> json.get("error").asString
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Development / Mock login to allow testing UI, navigation, and location monitoring
     * even if the local backend server is temporarily not running.
     */
    fun mockLogin(email: String = "citizen@disastraaa.gov", name: String = "Test Citizen"): LoginResponse {
        val mockToken = "mock_token_${System.currentTimeMillis()}"
        val mockUser = User(
            id = "usr_mock_001",
            name = name,
            email = email,
            role = "citizen"
        )
        tokenStorage.saveAuth(mockToken, mockUser)
        return LoginResponse(
            success = true,
            accessToken = mockToken,
            user = mockUser,
            message = "Mock development session started"
        )
    }

    fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    fun getCurrentUser(): User? = tokenStorage.getUser()

    fun logout() {
        tokenStorage.clear()
    }
}
