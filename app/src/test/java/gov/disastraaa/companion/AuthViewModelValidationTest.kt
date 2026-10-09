package gov.disastraaa.companion

import gov.disastraaa.companion.auth.AuthViewModel
import gov.disastraaa.companion.data.api.AuthApiService
import gov.disastraaa.companion.data.model.LoginRequest
import gov.disastraaa.companion.data.model.LoginResponse
import gov.disastraaa.companion.data.model.User
import gov.disastraaa.companion.data.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthViewModelValidationTest {

    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        fakeTokenStorage = FakeTokenStorage()
        val fakeApi = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<LoginResponse> {
                return Response.success(
                    LoginResponse(
                        success = true,
                        accessToken = "valid_token",
                        user = User("1", "User", request.email, "citizen")
                    )
                )
            }
        }
        authRepository = AuthRepository(fakeApi, fakeTokenStorage)
        viewModel = AuthViewModel(authRepository)
    }

    @Test
    fun login_withBlankEmail_setsErrorMessage() {
        viewModel.onEmailChanged("")
        viewModel.onPasswordChanged("validPassword123")
        viewModel.login()

        val state = viewModel.uiState.value
        assertEquals("Please enter your email address.", state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isAuthenticated)
    }

    @Test
    fun login_withMalformedEmail_setsErrorMessage() {
        val malformedEmails = listOf("not_an_email", "user@", "@domain.com", "user@domain", "user space@domain.com")

        for (email in malformedEmails) {
            viewModel.onEmailChanged(email)
            viewModel.onPasswordChanged("validPassword123")
            viewModel.login()

            val state = viewModel.uiState.value
            assertEquals("Please enter a valid email address.", state.errorMessage)
            assertFalse(state.isLoading)
            assertFalse(state.isAuthenticated)
        }
    }

    @Test
    fun login_withBlankPassword_setsErrorMessage() {
        viewModel.onEmailChanged("citizen@disastraaa.gov")
        viewModel.onPasswordChanged("")
        viewModel.login()

        val state = viewModel.uiState.value
        assertEquals("Password cannot be empty.", state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isAuthenticated)
    }

    @Test
    fun onEmailChanged_clearsPreviousErrorMessage() {
        viewModel.onEmailChanged("")
        viewModel.onPasswordChanged("password")
        viewModel.login()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onEmailChanged("c")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onPasswordChanged_clearsPreviousErrorMessage() {
        viewModel.onEmailChanged("citizen@disastraaa.gov")
        viewModel.onPasswordChanged("")
        viewModel.login()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onPasswordChanged("p")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loginWithMockState_authenticatesSuccessfully() {
        viewModel.onEmailChanged("mock.citizen@disastraaa.gov")
        viewModel.loginWithMockState()

        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertNotNull(state.user)
        assertEquals("mock.citizen@disastraaa.gov", state.user?.email)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
        assertTrue(fakeTokenStorage.isLoggedIn())
    }

    @Test
    fun logout_resetsUiStateAndClearsStorage() {
        viewModel.loginWithMockState()
        assertTrue(viewModel.uiState.value.isAuthenticated)

        viewModel.logout()

        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertNull(state.user)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(fakeTokenStorage.isLoggedIn())
    }
}
