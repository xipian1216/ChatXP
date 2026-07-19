package com.xipian.chatxp_android.ui.screens.auth

import com.xipian.chatxp_android.MainDispatcherRule
import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.remote.AccountApi
import com.xipian.chatxp_android.data.remote.AuthApi
import com.xipian.chatxp_android.data.remote.dto.AnonymousAuthRequestDto
import com.xipian.chatxp_android.data.remote.dto.AuthUserDto
import com.xipian.chatxp_android.data.remote.dto.DataEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.LoginRequestDto
import com.xipian.chatxp_android.data.remote.dto.RefreshRequestDto
import com.xipian.chatxp_android.data.remote.dto.RegisterRequestDto
import com.xipian.chatxp_android.data.remote.dto.TokenDto
import com.xipian.chatxp_android.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun switchingTabsKeepsEmailAndClearsPasswordsAndErrors() {
        val viewModel = AuthViewModel(repository())
        viewModel.onAction(AuthAction.EmailChanged("name@example.com"))
        viewModel.onAction(AuthAction.PasswordChanged("secret"))
        viewModel.onAction(AuthAction.Submit)

        viewModel.onAction(AuthAction.SelectTab(AuthTab.REGISTER))

        val state = viewModel.uiState.value
        assertEquals("name@example.com", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.globalError)
    }

    @Test
    fun registrationValidatesEveryLocalField() {
        val viewModel = AuthViewModel(repository())
        viewModel.onAction(AuthAction.SelectTab(AuthTab.REGISTER))
        viewModel.onAction(AuthAction.EmailChanged("invalid"))
        viewModel.onAction(AuthAction.PasswordChanged("short"))
        viewModel.onAction(AuthAction.ConfirmPasswordChanged("different"))

        viewModel.onAction(AuthAction.Submit)

        val state = viewModel.uiState.value
        assertEquals(AuthFieldError.REQUIRED, state.displayNameError)
        assertEquals(AuthFieldError.INVALID_EMAIL, state.emailError)
        assertEquals(AuthFieldError.INVALID_PASSWORD_LENGTH, state.passwordError)
        assertEquals(AuthFieldError.PASSWORD_MISMATCH, state.confirmPasswordError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun validRegistrationCompletesAuthentication() = runTest {
        val repository = repository().also { it.bindAccountApi(FakeAccountApi()) }
        val viewModel = AuthViewModel(repository)
        viewModel.onAction(AuthAction.SelectTab(AuthTab.REGISTER))
        viewModel.onAction(AuthAction.DisplayNameChanged("小明"))
        viewModel.onAction(AuthAction.EmailChanged("name@example.com"))
        viewModel.onAction(AuthAction.PasswordChanged("password123"))
        viewModel.onAction(AuthAction.ConfirmPasswordChanged("password123"))

        viewModel.onAction(AuthAction.Submit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(repository.currentUser.value?.isRegistered == true)
    }

    private fun repository() = AuthRepository(FakeBootstrapApi(), FakeCredentialStore())
}

private class FakeBootstrapApi : AuthApi {
    override suspend fun anonymous(
        body: AnonymousAuthRequestDto
    ): DataEnvelopeDto<TokenDto> = error("Not used")

    override suspend fun refresh(body: RefreshRequestDto): DataEnvelopeDto<TokenDto> =
        error("Not used")
}

private class FakeAccountApi : AccountApi {
    override suspend fun me(): DataEnvelopeDto<AuthUserDto> = error("Not used")

    override suspend fun register(body: RegisterRequestDto): DataEnvelopeDto<TokenDto> =
        DataEnvelopeDto(registeredToken(body.displayName, body.email))

    override suspend fun login(body: LoginRequestDto): DataEnvelopeDto<TokenDto> =
        DataEnvelopeDto(registeredToken("小明", body.email))

    override suspend fun logout(): DataEnvelopeDto<TokenDto> = error("Not used")

    private fun registeredToken(displayName: String, email: String) = TokenDto(
        userId = "registered-user",
        accessToken = "access",
        tokenType = "Bearer",
        expiresIn = 3_600,
        refreshToken = "refresh",
        refreshExpiresIn = 7_776_000,
        user = AuthUserDto(
            id = "registered-user",
            accountType = "registered",
            displayName = displayName,
            email = email,
            avatarText = displayName.take(1)
        )
    )
}

private class FakeCredentialStore : CredentialStore {
    private var snapshot = AuthSnapshot(
        installationId = "installation",
        installationSecret = "secret",
        accessToken = "guest-access",
        refreshToken = "guest-refresh",
        accessExpiresAtMillis = Long.MAX_VALUE,
        refreshExpiresAtMillis = Long.MAX_VALUE,
        selectedSessionId = null,
        activeClientRequestId = null,
        userId = "guest-user",
        accountType = "guest"
    )

    override suspend fun read(): AuthSnapshot = snapshot

    override suspend fun saveInstallation(id: String, secret: String) {
        snapshot = snapshot.copy(installationId = id, installationSecret = secret)
    }

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    ) {
        snapshot = snapshot.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAtMillis = accessExpiresAtMillis,
            refreshExpiresAtMillis = refreshExpiresAtMillis
        )
    }

    override suspend fun clearTokens() = Unit

    override suspend fun saveSelectedSessionId(sessionId: String?) {
        snapshot = snapshot.copy(selectedSessionId = sessionId)
    }

    override suspend fun saveActiveClientRequestId(clientRequestId: String?) {
        snapshot = snapshot.copy(activeClientRequestId = clientRequestId)
    }
}
