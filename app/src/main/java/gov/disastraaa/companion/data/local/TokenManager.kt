package gov.disastraaa.companion.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.utils.Constants

class TokenManager(context: Context) {

    private val sharedPreferences: SharedPreferences = createPreferences(context)

    private fun createPreferences(context: Context): SharedPreferences {
        val isEmulator = Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.FINGERPRINT.startsWith("generic", ignoreCase = true)

        if (!isEmulator) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                return EncryptedSharedPreferences.create(
                    context,
                    Constants.PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Throwable) {
                Log.w("TokenManager", "EncryptedSharedPreferences initialization fallback", e)
            }
        }
        return context.getSharedPreferences(Constants.PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun saveAuth(token: String, user: User?) {
        sharedPreferences.edit().apply {
            putString(Constants.KEY_ACCESS_TOKEN, token)
            user?.let {
                putString(Constants.KEY_USER_ID, it.id)
                putString(Constants.KEY_USER_NAME, it.name)
                putString(Constants.KEY_USER_EMAIL, it.email)
                putString(Constants.KEY_USER_ROLE, it.role)
            }
            apply()
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString(Constants.KEY_ACCESS_TOKEN, null)
    }

    fun getUser(): User? {
        val id = sharedPreferences.getString(Constants.KEY_USER_ID, null) ?: return null
        val name = sharedPreferences.getString(Constants.KEY_USER_NAME, "") ?: ""
        val email = sharedPreferences.getString(Constants.KEY_USER_EMAIL, "") ?: ""
        val role = sharedPreferences.getString(Constants.KEY_USER_ROLE, "citizen") ?: "citizen"
        return User(id = id, name = name, email = email, role = role)
    }

    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrBlank()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
