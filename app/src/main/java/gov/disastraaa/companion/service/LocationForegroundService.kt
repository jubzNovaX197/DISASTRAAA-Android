package gov.disastraaa.companion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import gov.disastraaa.companion.DisastraaaApplication
import gov.disastraaa.companion.MainActivity
import gov.disastraaa.companion.R
import gov.disastraaa.companion.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_LOCATION_SERVICE -> startTracking()
            Constants.ACTION_STOP_LOCATION_SERVICE -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (_isServiceRunning.value) return

        val app = DisastraaaApplication.instance
        val notification = createNotification("Safety monitoring is active")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        _isServiceRunning.value = true

        serviceScope.launch {
            app.locationRepository.startSession()
        }

        app.locationClient.getLocationUpdates(Constants.LOCATION_UPDATE_INTERVAL_MS)
            .catch { e ->
                _lastStatusText.value = "Location error: ${e.localizedMessage}"
            }
            .onEach { location ->
                _lastLocationText.value = "Lat: %.4f, Lon: %.4f".format(location.latitude, location.longitude)
                val result = app.locationRepository.recordLocation(location)
                result.onSuccess { pingResponse ->
                    if (pingResponse.risk?.isHighRisk == true) {
                        _lastStatusText.value = "ALERT: High risk zone detected!"
                    } else {
                        _lastStatusText.value = "Monitoring active (OK)"
                    }
                }.onFailure { err ->
                    _lastStatusText.value = "Stored offline: ${err.localizedMessage}"
                }
            }
            .launchIn(serviceScope)
    }

    private fun stopTracking() {
        _isServiceRunning.value = false
        _lastStatusText.value = "Monitoring stopped"
        val app = DisastraaaApplication.instance
        app.locationRepository.endSession()

        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("DISASTRAAA Safety Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        serviceScope.cancel()
    }

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastLocationText = MutableStateFlow<String?>(null)
        val lastLocationText: StateFlow<String?> = _lastLocationText.asStateFlow()

        private val _lastStatusText = MutableStateFlow("Ready")
        val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

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
