package gov.disastraaa.companion.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val user: User? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkInitialAuthState()
    }

    private fun checkInitialAuthState() {
        if (authRepository.isLoggedIn()) {
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    user = authRepository.getCurrentUser()
                )
            }
        }
    }

    fun onEmailChanged(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, errorMessage = null) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, errorMessage = null) }
    }

    fun login() {
        val currentEmail = _uiState.value.email.trim()
        val currentPassword = _uiState.value.password

        if (currentEmail.isBlank() || !currentEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (currentPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Password cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.login(currentEmail, currentPassword)
            result.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = response.user ?: authRepository.getCurrentUser(),
                        errorMessage = null
                    )
                }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Authentication failed. Please retry."
                    )
                }
            }
        }
    }

    /**
     * Allows seamless local verification on Android emulator without requiring
     * a running external backend instance.
     */
    fun loginWithMockState() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val email = _uiState.value.email.ifBlank { "citizen@disastraaa.gov" }
        val response = authRepository.mockLogin(email = email)
        _uiState.update {
            it.copy(
                isLoading = false,
                isAuthenticated = true,
                user = response.user,
                errorMessage = null
            )
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.update {
            LoginUiState()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun provideFactory(authRepository: AuthRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
        }
    }
}
