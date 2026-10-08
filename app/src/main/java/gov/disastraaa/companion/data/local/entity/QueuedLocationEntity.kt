package gov.disastraaa.companion.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import gov.disastraaa.companion.data.model.OfflineLocationRecord

@Entity(tableName = "queued_locations")
data class QueuedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toOfflineRecord(): OfflineLocationRecord {
        return OfflineLocationRecord(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            speed = speed,
            timestamp = timestamp
        )
    }
}
