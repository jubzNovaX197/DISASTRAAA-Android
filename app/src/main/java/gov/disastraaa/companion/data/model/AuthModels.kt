package gov.disastraaa.companion.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class User(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String? = null
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("user") val user: User? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)
