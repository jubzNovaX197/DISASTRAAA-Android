package gov.disastraaa.companion

import gov.disastraaa.companion.data.local.TokenStorage
import gov.disastraaa.companion.data.model.User

/**
 * In-memory implementation of TokenStorage for fast, isolated unit testing without Android Context dependencies.
 */
class FakeTokenStorage(
    private var token: String? = null,
    private var user: User? = null
) : TokenStorage {

    var clearCallCount: Int = 0
        private set

    var saveAuthCallCount: Int = 0
        private set

    override fun saveAuth(token: String, user: User?) {
        this.token = token
        this.user = user
        saveAuthCallCount++
    }

    override fun getAccessToken(): String? = token

    override fun getUser(): User? = user

    override fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    override fun clear() {
        token = null
        user = null
        clearCallCount++
    }
}
