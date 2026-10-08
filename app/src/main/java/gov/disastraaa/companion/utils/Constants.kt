package gov.disastraaa.companion.utils

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "disastraaa_location_monitoring_channel"
    const val NOTIFICATION_CHANNEL_NAME = "DISASTRAAA Safety Monitoring"
    const val NOTIFICATION_CHANNEL_DESC = "Shows continuous GPS location monitoring status"
    const val NOTIFICATION_ID = 1001

    const val RISK_ALERT_CHANNEL_ID = "disastraaa_risk_alert_channel"
    const val RISK_ALERT_CHANNEL_NAME = "DISASTRAAA Disaster Alerts"

    const val ACTION_START_LOCATION_SERVICE = "ACTION_START_LOCATION_SERVICE"
    const val ACTION_STOP_LOCATION_SERVICE = "ACTION_STOP_LOCATION_SERVICE"

    const val PREFS_FILE_NAME = "disastraaa_secure_prefs"
    const val KEY_ACCESS_TOKEN = "key_access_token"
    const val KEY_USER_ID = "key_user_id"
    const val KEY_USER_NAME = "key_user_name"
    const val KEY_USER_EMAIL = "key_user_email"
    const val KEY_USER_ROLE = "key_user_role"

    const val LOCATION_UPDATE_INTERVAL_MS = 10000L
    const val LOCATION_FASTEST_INTERVAL_MS = 5000L

    const val DATABASE_NAME = "disastraaa_companion_db"
}
