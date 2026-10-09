package gov.disastraaa.companion.data.local

import gov.disastraaa.companion.data.model.User

/**
 * Storage contract for persisting authentication tokens and user session data.
 */
interface TokenStorage {
    fun saveAuth(token: String, user: User?)
    fun getAccessToken(): String?
    fun getUser(): User?
    fun isLoggedIn(): Boolean
    fun clear()
}
