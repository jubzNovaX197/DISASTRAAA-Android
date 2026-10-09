package gov.disastraaa.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.android.gms.location.LocationServices
import gov.disastraaa.companion.data.api.ApiClient
import gov.disastraaa.companion.data.local.AppDatabase
import gov.disastraaa.companion.data.local.TokenManager
import gov.disastraaa.companion.data.repository.AuthRepository
import gov.disastraaa.companion.data.repository.LocationRepository
import gov.disastraaa.companion.location.DefaultLocationClient
import gov.disastraaa.companion.location.LocationClient
import gov.disastraaa.companion.utils.Constants
import androidx.work.Configuration

class DisastraaaApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    lateinit var tokenManager: TokenManager
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var locationRepository: LocationRepository
        private set

    lateinit var locationClient: LocationClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenManager = TokenManager(this)
        database = AppDatabase.getInstance(this)
        apiClient = ApiClient(tokenManager)

        authRepository = AuthRepository(
            authApiService = apiClient.authApiService,
            tokenStorage = tokenManager
        )

        locationRepository = LocationRepository(
            context = this,
            trackingApiService = apiClient.trackingApiService,
            locationDao = database.locationDao(),
            tokenStorage = tokenManager
        )

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationClient = DefaultLocationClient(this, fusedClient)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitoringChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Constants.NOTIFICATION_CHANNEL_DESC
                setShowBadge(false)
            }

            val riskChannel = NotificationChannel(
                Constants.RISK_ALERT_CHANNEL_ID,
                Constants.RISK_ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when in dangerous disaster zones"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(riskChannel)
        }
    }

    companion object {
        lateinit var instance: DisastraaaApplication
            private set
    }
}
