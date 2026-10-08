package gov.disastraaa.companion.data.api

import gov.disastraaa.companion.data.model.PingRequest
import gov.disastraaa.companion.data.model.PingResponse
import gov.disastraaa.companion.data.model.SessionRequest
import gov.disastraaa.companion.data.model.SessionResponse
import gov.disastraaa.companion.data.model.SyncRequest
import gov.disastraaa.companion.data.model.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TrackingApiService {

    @POST("api/tracking/session")
    suspend fun createSession(
        @Body request: SessionRequest
    ): Response<SessionResponse>

    @POST("api/tracking/ping")
    suspend fun pingLocation(
        @Body request: PingRequest
    ): Response<PingResponse>

    @POST("api/tracking/sync")
    suspend fun syncLocations(
        @Body request: SyncRequest
    ): Response<SyncResponse>
}
