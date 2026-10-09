package gov.disastraaa.companion.ui.monitoring

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import gov.disastraaa.companion.data.model.RiskAssessment
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
import gov.disastraaa.companion.data.repository.LocationRepository
import gov.disastraaa.companion.data.repository.SyncStatus
import gov.disastraaa.companion.service.LocationForegroundService
import gov.disastraaa.companion.service.TrackingStatus
import gov.disastraaa.companion.sync.SyncLocationsWorker
import gov.disastraaa.companion.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val trackingStatus: TrackingStatus = TrackingStatus.NOT_TRACKING,
    val userName: String = "Citizen",
    val userEmail: String = "",
    val userRole: String = "citizen",
    val sessionId: String? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastAccuracyMeters: Float? = null,
    val lastUploadTimeMillis: Long? = null,
    val lastUploadSuccess: Boolean? = null,
    val isQueuedLocally: Boolean = false,
    val riskAssessment: RiskAssessment? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val unsyncedCount: Int = 0,
    val lastSyncTimeMillis: Long? = null,
    val lastSyncError: String? = null,
    val statusMessage: String = "Safety monitoring is currently not active",
    val errorMessage: String? = null,
    val hasLocationPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val syncFeedback: String? = null
) {
    val isMonitoringActive: Boolean
        get() = trackingStatus == TrackingStatus.TRACKING || trackingStatus == TrackingStatus.STARTING

    val isActionInProgress: Boolean
        get() = trackingStatus == TrackingStatus.STARTING || trackingStatus == TrackingStatus.STOPPING
}

class MonitoringViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _manualSyncState = MutableStateFlow(false)
    private val _syncFeedback = MutableStateFlow<String?>(null)
    private val _localErrorMessage = MutableStateFlow<String?>(null)
    private val _permissionState = MutableStateFlow(false)

    val uiState: StateFlow<MonitoringUiState> = combine(
        LocationForegroundService.trackingState,
        locationRepository.unsyncedCountFlow,
        locationRepository.syncStatus,
        locationRepository.lastSyncTimeMillis,
        locationRepository.lastSyncError,
        _syncFeedback,
        _localErrorMessage,
        _permissionState
    ) { args: Array<Any?> ->
        val liveState = args[0] as gov.disastraaa.companion.service.LiveTrackingState
        val unsyncedCount = args[1] as Int
        val repoSyncStatus = args[2] as SyncStatus
        val lastSyncTime = args[3] as Long?
        val lastSyncErr = args[4] as String?
        val feedback = args[5] as String?
        val localError = args[6] as String?
        val hasPermission = args[7] as Boolean

        val currentUser: User? = authRepository.getCurrentUser()
        val loc = liveState.lastLocation
        val displayedError = localError ?: liveState.errorMessage

        val statusText = when (liveState.status) {
            TrackingStatus.NOT_TRACKING -> "Safety monitoring is currently not active"
            TrackingStatus.STARTING -> "Connecting to DISASTRAAA backend..."
            TrackingStatus.TRACKING -> {
                if (liveState.isQueuedLocally) {
                    "Offline: Location stored in local queue ($unsyncedCount pending)"
                } else if (liveState.lastRiskAssessment?.inHazardZone == true) {
                    "ALERT: Inside active ${liveState.lastRiskAssessment.hazardType ?: "hazard"} zone!"
                } else {
                    "Live safety monitoring active (Delivered)"
                }
            }
            TrackingStatus.STOPPING -> "Stopping safety monitoring session..."
            TrackingStatus.ERROR -> displayedError ?: "Tracking encountered an error"
        }

        val effectiveIsSyncing = _manualSyncState.value || repoSyncStatus == SyncStatus.SYNCING

        MonitoringUiState(
            trackingStatus = liveState.status,
            userName = currentUser?.name?.ifBlank { "Citizen" } ?: "Citizen",
            userEmail = currentUser?.email ?: "",
            userRole = currentUser?.role ?: "citizen",
            sessionId = liveState.sessionId,
            lastLatitude = loc?.latitude,
            lastLongitude = loc?.longitude,
            lastAccuracyMeters = if (loc != null && loc.hasAccuracy()) loc.accuracy else null,
            lastUploadTimeMillis = liveState.lastUploadTimeMillis,
            lastUploadSuccess = liveState.lastUploadSuccess,
            isQueuedLocally = liveState.isQueuedLocally,
            riskAssessment = liveState.lastRiskAssessment,
            syncStatus = repoSyncStatus,
            unsyncedCount = unsyncedCount,
            lastSyncTimeMillis = lastSyncTime,
            lastSyncError = lastSyncErr,
            statusMessage = statusText,
            errorMessage = displayedError,
            hasLocationPermission = hasPermission,
            isSyncing = effectiveIsSyncing,
            syncFeedback = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitoringUiState(
            userName = authRepository.getCurrentUser()?.name ?: "Citizen"
        )
    )

    fun refreshPermissions(context: Context) {
        _permissionState.value = PermissionUtils.hasLocationPermissions(context)
    }

    fun startMonitoring(context: Context) {
        if (uiState.value.isActionInProgress) return

        if (!authRepository.isLoggedIn()) {
            _localErrorMessage.value = "You must be logged in to start safety monitoring"
            return
        }

        val hasPerms = PermissionUtils.hasLocationPermissions(context)
        _permissionState.value = hasPerms
        if (!hasPerms) {
            _localErrorMessage.value = "Location permission is required to start safety monitoring"
            return
        }

        _localErrorMessage.value = null
        LocationForegroundService.start(context)
        SyncLocationsWorker.enqueuePeriodicSync(context)
    }

    fun stopMonitoring(context: Context) {
        if (uiState.value.trackingStatus == TrackingStatus.STOPPING ||
            uiState.value.trackingStatus == TrackingStatus.NOT_TRACKING
        ) {
            return
        }
        _localErrorMessage.value = null
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

    fun logout(context: Context) {
        LocationForegroundService.stop(context)
        SyncLocationsWorker.cancelSyncWork(context)
        locationRepository.endSessionLocally()
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
