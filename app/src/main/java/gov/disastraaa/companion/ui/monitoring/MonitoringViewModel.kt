package gov.disastraaa.companion.ui.monitoring

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
import gov.disastraaa.companion.data.repository.LocationRepository
import gov.disastraaa.companion.service.LocationForegroundService
import gov.disastraaa.companion.sync.SyncLocationsWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val isMonitoringActive: Boolean = false,
    val userName: String = "Citizen",
    val userEmail: String = "",
    val userRole: String = "citizen",
    val unsyncedCount: Int = 0,
    val lastLocation: String? = null,
    val statusMessage: String = "Monitoring is currently not active",
    val isSyncing: Boolean = false,
    val syncFeedback: String? = null
)

class MonitoringViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _manualSyncState = MutableStateFlow(false)
    private val _syncFeedback = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MonitoringUiState> = combine(
        LocationForegroundService.isServiceRunning,
        LocationForegroundService.lastLocationText,
        LocationForegroundService.lastStatusText,
        locationRepository.unsyncedCountFlow,
        _syncFeedback
    ) { isRunning, lastLoc, statusText, unsyncedCount, feedback ->
        val currentUser: User? = authRepository.getCurrentUser()
        MonitoringUiState(
            isMonitoringActive = isRunning,
            userName = currentUser?.name?.ifBlank { "Citizen" } ?: "Citizen",
            userEmail = currentUser?.email ?: "",
            userRole = currentUser?.role ?: "citizen",
            unsyncedCount = unsyncedCount,
            lastLocation = lastLoc,
            statusMessage = if (isRunning) statusText else "Monitoring is currently not active",
            isSyncing = _manualSyncState.value,
            syncFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitoringUiState(
            userName = authRepository.getCurrentUser()?.name ?: "Citizen"
        )
    )

    fun startMonitoring(context: Context) {
        LocationForegroundService.start(context)
        SyncLocationsWorker.enqueuePeriodicSync(context)
    }

    fun stopMonitoring(context: Context) {
        LocationForegroundService.stop(context)
    }

    fun syncNow(context: Context) {
        viewModelScope.launch {
            _manualSyncState.value = true
            _syncFeedback.value = "Synchronizing..."
            val result = locationRepository.syncQueuedLocations()
            _manualSyncState.value = false
            result.onSuccess { count ->
                _syncFeedback.value = if (count > 0) "Synchronized $count records" else "All records up to date"
            }.onFailure { err ->
                _syncFeedback.value = "Sync pending: ${err.localizedMessage}"
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }

    companion object {
        fun provideFactory(
            authRepository: AuthRepository,
            locationRepository: LocationRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MonitoringViewModel(authRepository, locationRepository) as T
                }
            }
        }
    }
}
