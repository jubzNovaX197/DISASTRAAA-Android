package gov.disastraaa.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import gov.disastraaa.companion.data.model.OfflineLocationRecord
import java.time.Instant

@Entity(
    tableName = "queued_locations",
    indices = [
        Index(value = ["userId", "sessionId", "isSynced"]),
        Index(value = ["timestamp"])
    ]
)
data class QueuedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val sessionId: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toOfflineRecord(): OfflineLocationRecord {
        val isoTimestamp = try {
            Instant.ofEpochMilli(if (timestamp > 0) timestamp else System.currentTimeMillis()).toString()
        } catch (_: Exception) {
            Instant.now().toString()
        }

        return OfflineLocationRecord(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy?.toDouble(),
            timestamp = isoTimestamp
        )
    }
}
