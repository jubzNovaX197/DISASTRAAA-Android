package gov.disastraaa.companion.data.model

import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("model") val model: String? = null,
    @SerializedName("osVersion") val osVersion: String? = null,
    @SerializedName("appVersion") val appVersion: String? = null
)

data class SessionRequest(
    @SerializedName("action") val action: String, // "START" or "STOP"
    @SerializedName("consentGiven") val consentGiven: Boolean? = null,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo? = null
)

data class SessionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sessionId") val sessionId: String? = null,
    @SerializedName("status") val status: String? = null, // "ACTIVE" or "ENDED"
    @SerializedName("message") val message: String? = null
)

data class PingRequest(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("speed") val speed: Double? = null,
    @SerializedName("batteryLevel") val batteryLevel: Int? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class RiskAssessment(
    @SerializedName("inHazardZone") val inHazardZone: Boolean = false,
    @SerializedName("riskLevel") val riskLevel: String = "LOW",
    @SerializedName("hazardId") val hazardId: String? = null,
    @SerializedName("hazardType") val hazardType: String? = null,
    @SerializedName("title") val title: String? = null
) {
    val isHighRisk: Boolean
        get() = inHazardZone || riskLevel.equals("HIGH", ignoreCase = true) || riskLevel.equals("CRITICAL", ignoreCase = true)
}

data class PingResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("riskAssessment") val riskAssessment: RiskAssessment? = null,
    @SerializedName("message") val message: String? = null
)

data class OfflineLocationRecord(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("timestamp") val timestamp: String
)

data class SyncRequest(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("locations") val locations: List<OfflineLocationRecord>
)

data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("syncedCount") val syncedCount: Int = 0,
    @SerializedName("message") val message: String? = null
)
