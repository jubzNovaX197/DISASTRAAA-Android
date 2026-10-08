package gov.disastraaa.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(entity: QueuedLocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<QueuedLocationEntity>)

    @Query("SELECT * FROM queued_locations WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedLocations(limit: Int = 100): List<QueuedLocationEntity>

    @Query("SELECT COUNT(*) FROM queued_locations WHERE isSynced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_locations WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("UPDATE queued_locations SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM queued_locations WHERE id IN (:ids)")
    suspend fun deleteLocations(ids: List<Long>)

    @Query("DELETE FROM queued_locations WHERE isSynced = 1")
    suspend fun clearSyncedLocations()

    @Query("DELETE FROM queued_locations")
    suspend fun clearAll()
}
