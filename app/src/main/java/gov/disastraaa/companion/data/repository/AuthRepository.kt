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

class AuthRepository(
    private val authApiService: AuthApiService,
    private val tokenManager: TokenManager
) {

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = authApiService.login(LoginRequest(email.trim(), password))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.accessToken.isNullOrBlank()) {
                    tokenManager.saveAuth(
                        token = body.accessToken,
                        user = body.user ?: User(
                            id = "citizen-${System.currentTimeMillis()}",
                            name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                            email = email,
                            role = "citizen"
                        )
                    )
                    Result.success(body)
                } else {
                    val message = body?.message ?: "Login failed: Invalid server response"
                    Result.failure(Exception(message))
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Invalid email or password. Please verify your credentials."
                    403 -> "Account is unauthorized for companion access."
                    404 -> "Authentication service endpoint not found."
                    in 500..599 -> "Server error (${response.code()}). Please try again later."
                    else -> "Authentication failed with status ${response.code()}."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Cannot reach DISASTRAAA server. Please check internet connection."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out. Server is taking too long to respond."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: Unable to connect to DISASTRAAA backend. (${e.localizedMessage})"))
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP error ${e.code()}: ${e.message()}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected login error: ${e.localizedMessage ?: "Unknown error"}"))
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
        tokenManager.saveAuth(mockToken, mockUser)
        return LoginResponse(
            success = true,
            accessToken = mockToken,
            user = mockUser,
            message = "Mock development session started"
        )
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    fun getCurrentUser(): User? = tokenManager.getUser()

    fun logout() {
        tokenManager.clear()
    }
}
