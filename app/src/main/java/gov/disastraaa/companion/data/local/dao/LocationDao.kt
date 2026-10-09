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

    // --- Legacy Queries for compatibility ---
    @Query("SELECT * FROM queued_locations WHERE isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedLocations(limit: Int = 100): List<QueuedLocationEntity>

    @Query("SELECT COUNT(*) FROM queued_locations WHERE isSynced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_locations WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int

    // --- User & Session Scoped Queries (Phase 4 Isolation) ---
    @Query("SELECT COUNT(*) FROM queued_locations WHERE userId = :userId AND isSynced = 0")
    fun observeUnsyncedCountForUser(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_locations WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedCountForUser(userId: String): Int

    @Query("SELECT DISTINCT sessionId FROM queued_locations WHERE userId = :userId AND isSynced = 0 AND sessionId != '' ORDER BY timestamp ASC")
    suspend fun getUnsyncedSessionsForUser(userId: String): List<String>

    @Query("SELECT * FROM queued_locations WHERE userId = :userId AND sessionId = :sessionId AND isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedLocationsForSession(userId: String, sessionId: String, limit: Int = 50): List<QueuedLocationEntity>

    @Query("SELECT * FROM queued_locations WHERE userId = :userId AND isSynced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedLocationsForUser(userId: String, limit: Int = 100): List<QueuedLocationEntity>

    @Query("UPDATE queued_locations SET retryCount = retryCount + 1 WHERE id IN (:ids)")
    suspend fun incrementRetryCount(ids: List<Long>)

    @Query("UPDATE queued_locations SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM queued_locations WHERE id IN (:ids)")
    suspend fun deleteLocations(ids: List<Long>)

    @Query("DELETE FROM queued_locations WHERE userId = :userId")
    suspend fun deleteLocationsForUser(userId: String)

    @Query("DELETE FROM queued_locations WHERE isSynced = 1")
    suspend fun clearSyncedLocations()

    @Query("DELETE FROM queued_locations")
    suspend fun clearAll()

    // Bounded queue retention: delete oldest records exceeding max capacity
    @Query("DELETE FROM queued_locations WHERE id IN (SELECT id FROM queued_locations ORDER BY timestamp ASC LIMIT :excessCount)")
    suspend fun pruneOldestRecords(excessCount: Int)

    @Query("SELECT COUNT(*) FROM queued_locations")
    suspend fun getTotalRecordCount(): Int
}
