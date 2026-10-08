package gov.disastraaa.companion.data.model

import com.google.gson.annotations.SerializedName

data class SessionRequest(
    @SerializedName("deviceTimestamp") val deviceTimestamp: Long = System.currentTimeMillis()
)

data class SessionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("message") val message: String? = null
)

data class GpsLocation(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("speed") val speed: Float? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class PingRequest(
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("speed") val speed: Float? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class RiskResult(
    @SerializedName("level") val level: String? = null,
    @SerializedName("zoneName") val zoneName: String? = null,
    @SerializedName("isHighRisk") val isHighRisk: Boolean = false,
    @SerializedName("message") val message: String? = null
)

data class PingResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("risk") val risk: RiskResult? = null,
    @SerializedName("message") val message: String? = null
)

data class OfflineLocationRecord(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("speed") val speed: Float? = null,
    @SerializedName("timestamp") val timestamp: Long
)

data class SyncRequest(
    @SerializedName("locations") val locations: List<OfflineLocationRecord>
)

data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("syncedCount") val syncedCount: Int = 0,
    @SerializedName("message") val message: String? = null
)
