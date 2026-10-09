package gov.disastraaa.companion

import com.google.gson.Gson
import gov.disastraaa.companion.data.model.DeviceInfo
import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.RiskAssessment
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingModelsTest {

    private val gson = Gson()

    @Test
    fun sessionStartRequest_serializesToJsonCorrectly() {
        val request = SessionRequest(
            action = "START",
            consentGiven = true,
            deviceInfo = DeviceInfo(
                model = "Pixel 7 Pro",
                osVersion = "Android 14",
                appVersion = "1.0.0"
            )
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"action\":\"START\""))
        assertTrue(json.contains("\"consentGiven\":true"))
        assertTrue(json.contains("\"model\":\"Pixel 7 Pro\""))
        assertTrue(json.contains("\"osVersion\":\"Android 14\""))
        assertTrue(json.contains("\"appVersion\":\"1.0.0\""))
        assertFalse(json.contains("sessionId"))
    }

    @Test
    fun sessionStopRequest_serializesToJsonCorrectly() {
        val request = SessionRequest(
            action = "STOP",
            sessionId = "sess_xyz_789"
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"action\":\"STOP\""))
        assertTrue(json.contains("\"sessionId\":\"sess_xyz_789\""))
    }

    @Test
    fun sessionResponse_deserializesActiveResponse() {
        val json = """
            {
                "success": true,
                "sessionId": "sess_active_123",
                "status": "ACTIVE"
            }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertTrue(response.success)
        assertEquals("sess_active_123", response.sessionId)
        assertEquals("ACTIVE", response.status)
        assertNull(response.message)
    }

    @Test
    fun sessionResponse_deserializesEndedResponse() {
        val json = """
            {
                "success": true,
                "sessionId": "sess_active_123",
                "status": "ENDED"
            }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertTrue(response.success)
        assertEquals("sess_active_123", response.sessionId)
        assertEquals("ENDED", response.status)
    }

    @Test
    fun pingRequest_serializesAllFieldsCorrectly() {
        val request = PingRequest(
            sessionId = "sess_999",
            latitude = 20.2961,
            longitude = 85.8245,
            accuracy = 4.5,
            altitude = 45.0,
            speed = 1.25,
            batteryLevel = 88,
            timestamp = "2026-10-09T06:00:00.000Z"
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"sessionId\":\"sess_999\""))
        assertTrue(json.contains("\"latitude\":20.2961"))
        assertTrue(json.contains("\"longitude\":85.8245"))
        assertTrue(json.contains("\"accuracy\":4.5"))
        assertTrue(json.contains("\"altitude\":45.0"))
        assertTrue(json.contains("\"speed\":1.25"))
        assertTrue(json.contains("\"batteryLevel\":88"))
        assertTrue(json.contains("\"timestamp\":\"2026-10-09T06:00:00.000Z\""))
    }

    @Test
    fun pingResponse_deserializesSafeZoneResponse() {
        val json = """
            {
                "success": true,
                "riskAssessment": {
                    "inHazardZone": false,
                    "riskLevel": "LOW",
                    "hazardId": null,
                    "hazardType": null,
                    "title": null
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, PingResponse::class.java)

        assertTrue(response.success)
        assertNotNull(response.riskAssessment)
        assertFalse(response.riskAssessment!!.inHazardZone)
        assertEquals("LOW", response.riskAssessment!!.riskLevel)
        assertFalse(response.riskAssessment!!.isHighRisk)
        assertNull(response.riskAssessment!!.hazardId)
    }

    @Test
    fun pingResponse_deserializesHazardZoneResponse() {
        val json = """
            {
                "success": true,
                "riskAssessment": {
                    "inHazardZone": true,
                    "riskLevel": "CRITICAL",
                    "hazardId": "hazard_flood_01",
                    "hazardType": "FLOOD",
                    "title": "Mahanadi River Inundation"
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, PingResponse::class.java)

        assertTrue(response.success)
        assertNotNull(response.riskAssessment)
        assertTrue(response.riskAssessment!!.inHazardZone)
        assertEquals("CRITICAL", response.riskAssessment!!.riskLevel)
        assertEquals("FLOOD", response.riskAssessment!!.hazardType)
        assertEquals("Mahanadi River Inundation", response.riskAssessment!!.title)
        assertTrue(response.riskAssessment!!.isHighRisk)
    }

    @Test
    fun riskAssessment_isHighRiskEvaluatesCorrectly() {
        val lowSafe = RiskAssessment(inHazardZone = false, riskLevel = "LOW")
        assertFalse(lowSafe.isHighRisk)

        val moderateSafe = RiskAssessment(inHazardZone = false, riskLevel = "MODERATE")
        assertFalse(moderateSafe.isHighRisk)

        val highRisk = RiskAssessment(inHazardZone = false, riskLevel = "HIGH")
        assertTrue(highRisk.isHighRisk)

        val criticalRisk = RiskAssessment(inHazardZone = false, riskLevel = "CRITICAL")
        assertTrue(criticalRisk.isHighRisk)

        val hazardInZone = RiskAssessment(inHazardZone = true, riskLevel = "MODERATE")
        assertTrue(hazardInZone.isHighRisk)
    }

    @Test
    fun syncRequest_serializesToJsonMatchingBackendContract() {
        val request = gov.disastraaa.companion.data.model.SyncRequest(
            sessionId = "sess_batch_123",
            locations = listOf(
                gov.disastraaa.companion.data.model.OfflineLocationRecord(
                    latitude = 20.2961,
                    longitude = 85.8245,
                    accuracy = 5.0,
                    timestamp = "2026-10-09T06:00:00.000Z"
                )
            )
        )
        val json = gson.toJson(request)

        assertTrue(json.contains("\"sessionId\":\"sess_batch_123\""))
        assertTrue(json.contains("\"locations\":["))
        assertTrue(json.contains("\"latitude\":20.2961"))
        assertTrue(json.contains("\"longitude\":85.8245"))
        assertTrue(json.contains("\"timestamp\":\"2026-10-09T06:00:00.000Z\""))
    }

    @Test
    fun syncResponse_deserializesSuccessAndSyncedCount() {
        val json = """
            {
                "success": true,
                "syncedCount": 12
            }
        """.trimIndent()
        val response = gson.fromJson(json, gov.disastraaa.companion.data.model.SyncResponse::class.java)

        assertTrue(response.success)
        assertEquals(12, response.syncedCount)
    }
}

