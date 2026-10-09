package gov.disastraaa.companion

import gov.disastraaa.companion.data.local.dao.LocationDao
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeLocationDao : LocationDao {
    private val locations = mutableListOf<QueuedLocationEntity>()
    private val unsyncedCountFlow = MutableStateFlow(0)

    override suspend fun insertLocation(entity: QueuedLocationEntity): Long {
        val id = (locations.size + 1).toLong()
        locations.add(entity.copy(id = id))
        updateCount()
        return id
    }

    override suspend fun insertAll(entities: List<QueuedLocationEntity>) {
        entities.forEach { insertLocation(it) }
    }

    override suspend fun getUnsyncedLocations(limit: Int): List<QueuedLocationEntity> {
        return locations.filter { !it.isSynced }.take(limit)
    }

    override fun observeUnsyncedCount(): Flow<Int> = unsyncedCountFlow.asStateFlow()

    override suspend fun getUnsyncedCount(): Int {
        return locations.count { !it.isSynced }
    }

    override fun observeUnsyncedCountForUser(userId: String): Flow<Int> {
        return unsyncedCountFlow.map {
            locations.count { it.userId == userId && !it.isSynced }
        }
    }

    override suspend fun getUnsyncedCountForUser(userId: String): Int {
        return locations.count { it.userId == userId && !it.isSynced }
    }

    override suspend fun getUnsyncedSessionsForUser(userId: String): List<String> {
        return locations.filter { it.userId == userId && !it.isSynced && it.sessionId.isNotEmpty() }
            .map { it.sessionId }
            .distinct()
    }

    override suspend fun getUnsyncedLocationsForSession(
        userId: String,
        sessionId: String,
        limit: Int
    ): List<QueuedLocationEntity> {
        return locations
            .filter { it.userId == userId && it.sessionId == sessionId && !it.isSynced }
            .sortedBy { it.timestamp }
            .take(limit)
    }

    override suspend fun getUnsyncedLocationsForUser(
        userId: String,
        limit: Int
    ): List<QueuedLocationEntity> {
        return locations
            .filter { it.userId == userId && !it.isSynced }
            .sortedBy { it.timestamp }
            .take(limit)
    }

    override suspend fun incrementRetryCount(ids: List<Long>) {
        val updated = locations.map {
            if (it.id in ids) it.copy(retryCount = it.retryCount + 1) else it
        }
        locations.clear()
        locations.addAll(updated)
    }

    override suspend fun markAsSynced(ids: List<Long>) {
        val updated = locations.map {
            if (it.id in ids) it.copy(isSynced = true) else it
        }
        locations.clear()
        locations.addAll(updated)
        updateCount()
    }

    override suspend fun deleteLocations(ids: List<Long>) {
        locations.removeAll { it.id in ids }
        updateCount()
    }

    override suspend fun deleteLocationsForUser(userId: String) {
        locations.removeAll { it.userId == userId }
        updateCount()
    }

    override suspend fun clearSyncedLocations() {
        locations.removeAll { it.isSynced }
        updateCount()
    }

    override suspend fun clearAll() {
        locations.clear()
        updateCount()
    }

    override suspend fun pruneOldestRecords(excessCount: Int) {
        if (excessCount <= 0) return
        val oldest = locations.sortedBy { it.timestamp }.take(excessCount).map { it.id }
        locations.removeAll { it.id in oldest }
        updateCount()
    }

    override suspend fun getTotalRecordCount(): Int {
        return locations.size
    }

    private fun updateCount() {
        unsyncedCountFlow.value = locations.count { !it.isSynced }
    }
}
