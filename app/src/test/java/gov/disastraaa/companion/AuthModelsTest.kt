package gov.disastraaa.companion

import com.google.gson.Gson
import gov.disastraaa.companion.data.model.LoginRequest
import gov.disastraaa.companion.data.model.LoginResponse
import gov.disastraaa.companion.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelsTest {

    private val gson = Gson()

    @Test
    fun loginRequest_serializesToJsonCorrectly() {
        val request = LoginRequest(
            email = "citizen@disastraaa.gov",
            password = "SecretPassword123"
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"email\":\"citizen@disastraaa.gov\""))
        assertTrue(json.contains("\"password\":\"SecretPassword123\""))
    }

    @Test
    fun loginResponse_parsesSuccessfulBackendJson() {
        val json = """
            {
                "success": true,
                "accessToken": "eyJh...sample.jwt.token",
                "user": {
                    "id": "usr_789",
                    "name": "Jane Citizen",
                    "email": "jane@disastraaa.gov",
                    "role": "CITIZEN"
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, LoginResponse::class.java)

        assertTrue(response.success)
        assertEquals("eyJh...sample.jwt.token", response.accessToken)
        assertNotNull(response.user)
        assertEquals("usr_789", response.user?.id)
        assertEquals("Jane Citizen", response.user?.name)
        assertEquals("jane@disastraaa.gov", response.user?.email)
        assertEquals("CITIZEN", response.user?.role)
        assertNull(response.message)
        assertNull(response.error)
    }

    @Test
    fun loginResponse_parsesDifferentUserRoles() {
        val roles = listOf("CITIZEN", "citizen", "RESCUE_WORKER", "ADMIN")
        for (role in roles) {
            val json = """
                {
                    "success": true,
                    "accessToken": "token_for_$role",
                    "user": {
                        "id": "1",
                        "name": "User",
                        "email": "user@test.org",
                        "role": "$role"
                    }
                }
            """.trimIndent()

            val response = gson.fromJson(json, LoginResponse::class.java)
            assertEquals(role, response.user?.role)
        }
    }

    @Test
    fun loginResponse_parsesBackendErrorMessageField() {
        val json = """
            {
                "success": false,
                "message": "Invalid email or password"
            }
        """.trimIndent()

        val response = gson.fromJson(json, LoginResponse::class.java)

        assertFalse(response.success)
        assertNull(response.accessToken)
        assertNull(response.user)
        assertEquals("Invalid email or password", response.message)
    }

    @Test
    fun loginResponse_parsesBackendErrorField() {
        val json = """
            {
                "success": false,
                "error": "Account suspended"
            }
        """.trimIndent()

        val response = gson.fromJson(json, LoginResponse::class.java)

        assertFalse(response.success)
        assertEquals("Account suspended", response.error)
    }

    @Test
    fun loginResponse_handlesMissingOrEmptyUserGracefully() {
        val json = """
            {
                "success": true,
                "accessToken": "token_without_user"
            }
        """.trimIndent()

        val response = gson.fromJson(json, LoginResponse::class.java)

        assertTrue(response.success)
        assertEquals("token_without_user", response.accessToken)
        assertNull(response.user)
    }
}
