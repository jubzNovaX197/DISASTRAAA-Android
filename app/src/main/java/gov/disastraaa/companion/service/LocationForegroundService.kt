package gov.disastraaa.companion.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import gov.disastraaa.companion.DisastraaaApplication
import gov.disastraaa.companion.MainActivity
import gov.disastraaa.companion.R
import gov.disastraaa.companion.data.model.RiskAssessment
import gov.disastraaa.companion.data.repository.LocationUploadResult
import gov.disastraaa.companion.utils.Constants
import gov.disastraaa.companion.utils.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrackingStatus {
    NOT_TRACKING,
    STARTING,
    TRACKING,
    STOPPING,
    ERROR
}

data class LiveTrackingState(
    val status: TrackingStatus = TrackingStatus.NOT_TRACKING,
    val sessionId: String? = null,
    val lastLocation: Location? = null,
    val lastUploadTimeMillis: Long? = null,
    val lastRiskAssessment: RiskAssessment? = null,
    val lastUploadSuccess: Boolean? = null,
    val isQueuedLocally: Boolean = false,
    val errorMessage: String? = null
)

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationUpdatesJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_LOCATION_SERVICE -> startTracking()
            Constants.ACTION_STOP_LOCATION_SERVICE -> stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (_trackingState.value.status == TrackingStatus.TRACKING ||
            _trackingState.value.status == TrackingStatus.STARTING
        ) {
            return
        }

        val initialNotification = createNotification(
            title = "DISASTRAAA Safety Active",
            contentText = "Starting safety monitoring session...",
            sessionId = null
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, initialNotification)
        }

        val app = DisastraaaApplication.instance

        if (!PermissionUtils.hasLocationPermissions(this)) {
            _trackingState.update {
                it.copy(
                    status = TrackingStatus.ERROR,
                    errorMessage = "Location permission is required to start safety monitoring"
                )
            }
            syncLegacyStateFlows()
            stopForegroundGracefully()
            stopSelf()
            return
        }

        if (!app.tokenManager.isLoggedIn()) {
            _trackingState.update {
                it.copy(
                    status = TrackingStatus.ERROR,
                    errorMessage = "User is not logged in"
                )
            }
            syncLegacyStateFlows()
            stopForegroundGracefully()
            stopSelf()
            return
        }

        _trackingState.update {
            it.copy(
                status = TrackingStatus.STARTING,
                errorMessage = null
            )
        }
        syncLegacyStateFlows()

        serviceScope.launch {
            val sessionResult = app.locationRepository.startSession(consentGiven = true)

            sessionResult.onSuccess { sessionId ->
                _trackingState.update {
                    it.copy(
                        status = TrackingStatus.TRACKING,
                        sessionId = sessionId,
                        errorMessage = null
                    )
                }
                syncLegacyStateFlows()
                updateNotification("Safety monitoring active", sessionId)

                locationUpdatesJob?.cancel()
                locationUpdatesJob = app.locationClient.getLocationUpdates(Constants.LOCATION_UPDATE_INTERVAL_MS)
                    .catch { e ->
                        _trackingState.update { current ->
                            current.copy(
                                errorMessage = "Location provider error: ${e.localizedMessage}"
                            )
                        }
                        syncLegacyStateFlows()
                    }
                    .onEach { location ->
                        val battery = getBatteryLevel(this@LocationForegroundService)
                        val uploadResult = app.locationRepository.recordLocationDetailed(location, battery)

                        when (uploadResult) {
                            is LocationUploadResult.LiveDelivered -> {
                                val risk = uploadResult.response.riskAssessment
                                _trackingState.update { current ->
                                    current.copy(
                                        lastLocation = location,
                                        lastUploadTimeMillis = System.currentTimeMillis(),
                                        lastRiskAssessment = risk,
                                        lastUploadSuccess = true,
                                        isQueuedLocally = false,
                                        errorMessage = null
                                    )
                                }
                                syncLegacyStateFlows()

                                if (risk?.inHazardZone == true) {
                                    updateNotification(
                                        title = "ALERT: In Hazard Zone (${risk.riskLevel})",
                                        contentText = risk.title ?: "You have entered a detected hazard zone",
                                        sessionId = sessionId
                                    )
                                } else {
                                    updateNotification(
                                        title = "DISASTRAAA Safety Active",
                                        contentText = "Monitoring active • Status: ${risk?.riskLevel ?: "OK"}",
                                        sessionId = sessionId
                                    )
                                }
                            }
                            is LocationUploadResult.QueuedLocally -> {
                                _trackingState.update { current ->
                                    current.copy(
                                        lastLocation = location,
                                        lastUploadSuccess = false,
                                        isQueuedLocally = true,
                                        errorMessage = uploadResult.reason
                                    )
                                }
                                syncLegacyStateFlows()

                                updateNotification(
                                    title = "DISASTRAAA Safety (Offline Queue)",
                                    contentText = "Offline: Pings queued locally in Room for sync",
                                    sessionId = sessionId
                                )
                            }
                            is LocationUploadResult.Rejected -> {
                                val err = uploadResult.error
                                _trackingState.update { current ->
                                    current.copy(
                                        lastLocation = location,
                                        lastUploadSuccess = false,
                                        isQueuedLocally = false,
                                        errorMessage = err.localizedMessage ?: "Tracking rejected"
                                    )
                                }
                                syncLegacyStateFlows()

                                if (err.message?.contains("expired", ignoreCase = true) == true ||
                                    err.message?.contains("not active", ignoreCase = true) == true ||
                                    err.message?.contains("Session not found", ignoreCase = true) == true
                                ) {
                                    stopTracking()
                                }
                            }
                        }
                    }
                    .launchIn(serviceScope)

            }.onFailure { err ->
                _trackingState.update {
                    it.copy(
                        status = TrackingStatus.ERROR,
                        errorMessage = err.localizedMessage ?: "Failed to initialize tracking session on server"
                    )
                }
                syncLegacyStateFlows()
                stopForegroundGracefully()
                stopSelf()
            }
        }
    }

    private fun stopTracking() {
        if (_trackingState.value.status == TrackingStatus.NOT_TRACKING) {
            stopForegroundGracefully()
            stopSelf()
            return
        }

        _trackingState.update { it.copy(status = TrackingStatus.STOPPING) }
        syncLegacyStateFlows()

        locationUpdatesJob?.cancel()
        locationUpdatesJob = null

        val app = DisastraaaApplication.instance

        serviceScope.launch {
            try {
                app.locationRepository.stopSession()
            } finally {
                _trackingState.update {
                    LiveTrackingState(
                        status = TrackingStatus.NOT_TRACKING,
                        sessionId = null,
                        lastLocation = null,
                        lastUploadTimeMillis = null,
                        lastRiskAssessment = null,
                        lastUploadSuccess = null,
                        isQueuedLocally = false,
                        errorMessage = null
                    )
                }
                syncLegacyStateFlows()
                stopForegroundGracefully()
                stopSelf()
            }
        }
    }

    private fun stopForegroundGracefully() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotification(
        title: String = "DISASTRAAA Safety Active",
        contentText: String,
        sessionId: String? = null
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = Constants.ACTION_STOP_LOCATION_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Tracking",
                stopPendingIntent
            )

        sessionId?.let {
            builder.setSubText("Session: ${it.take(8)}")
        }

        return builder.build()
    }

    private fun updateNotification(
        contentText: String,
        sessionId: String? = null,
        title: String = "DISASTRAAA Safety Active"
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(Constants.NOTIFICATION_ID, createNotification(title, contentText, sessionId))
    }

    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                ((level * 100) / scale).coerceIn(0, 100)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationUpdatesJob?.cancel()
        serviceScope.cancel()
        _trackingState.update {
            LiveTrackingState(
                status = TrackingStatus.NOT_TRACKING,
                sessionId = null,
                lastLocation = null,
                errorMessage = null
            )
        }
        syncLegacyStateFlows()
    }

    companion object {
        private val _trackingState = MutableStateFlow(LiveTrackingState())
        val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastLocationText = MutableStateFlow<String?>(null)
        val lastLocationText: StateFlow<String?> = _lastLocationText.asStateFlow()

        private val _lastStatusText = MutableStateFlow("Ready")
        val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

        private fun syncLegacyStateFlows() {
            val current = _trackingState.value
            _isServiceRunning.value = current.status == TrackingStatus.TRACKING || current.status == TrackingStatus.STARTING
            _lastLocationText.value = current.lastLocation?.let {
                "Lat: %.4f, Lon: %.4f".format(it.latitude, it.longitude)
            }
            _lastStatusText.value = when (current.status) {
                TrackingStatus.NOT_TRACKING -> "Monitoring stopped"
                TrackingStatus.STARTING -> "Connecting to DISASTRAAA..."
                TrackingStatus.TRACKING -> {
                    if (current.isQueuedLocally) {
                        "Offline: Pings queued locally"
                    } else if (current.lastRiskAssessment?.inHazardZone == true) {
                        "ALERT: ${current.lastRiskAssessment.title ?: "Hazard zone detected"}"
                    } else {
                        "Safety monitoring active (Live)"
                    }
                }
                TrackingStatus.STOPPING -> "Stopping monitoring..."
                TrackingStatus.ERROR -> current.errorMessage ?: "Tracking error"
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = Constants.ACTION_START_LOCATION_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = Constants.ACTION_STOP_LOCATION_SERVICE
            }
            context.startService(intent)
        }
    }
}
