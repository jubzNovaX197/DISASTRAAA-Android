package gov.disastraaa.companion.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("user") val user: User? = null,
    @SerializedName("message") val message: String? = null
)
