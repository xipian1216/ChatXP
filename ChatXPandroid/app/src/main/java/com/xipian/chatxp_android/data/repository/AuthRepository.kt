package com.xipian.chatxp_android.data.repository

import android.util.Base64
import com.xipian.chatxp_android.BuildConfig
import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.model.AccountType
import com.xipian.chatxp_android.data.model.AuthUser
import com.xipian.chatxp_android.data.remote.AccountApi
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.remote.AuthApi
import com.xipian.chatxp_android.data.remote.dto.AnonymousAuthRequestDto
import com.xipian.chatxp_android.data.remote.dto.AuthUserDto
import com.xipian.chatxp_android.data.remote.dto.ErrorEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.LoginRequestDto
import com.xipian.chatxp_android.data.remote.dto.RefreshRequestDto
import com.xipian.chatxp_android.data.remote.dto.RegisterRequestDto
import com.xipian.chatxp_android.data.remote.dto.TokenDto
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class AuthRepository(
    private val authApi: AuthApi,
    private val authStore: CredentialStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    @Volatile private var cachedSnapshot: AuthSnapshot? = null
    @Volatile private var accountApi: AccountApi? = null
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    fun bindAccountApi(api: AccountApi) {
        accountApi = api
    }

    suspend fun initializeUser(): AuthUser {
        validAccessToken()
        val stored = mutex.withLock { snapshot().toAuthUser() }
        val remote = accountApi?.let { api ->
            runCatching { apiCall { api.me().data.toModel() } }.getOrNull()
        }
        return (remote ?: stored ?: mutex.withLock {
            val state = snapshot()
            AuthUser(state.userId.orEmpty(), AccountType.GUEST)
        }).also { _currentUser.value = it }
    }

    suspend fun register(displayName: String, email: String, password: String): AuthUser {
        val api = requireAccountApi()
        val token = apiCall {
            api.register(RegisterRequestDto(displayName, email, password)).data
        }
        return mutex.withLock { persistTokens(snapshot(), token).toAuthUser()!! }
            .also { _currentUser.value = it }
    }

    suspend fun login(email: String, password: String): AuthUser {
        val api = requireAccountApi()
        val token = apiCall { api.login(LoginRequestDto(email, password)).data }
        return mutex.withLock { persistTokens(snapshot(), token).toAuthUser()!! }
            .also { _currentUser.value = it }
    }

    suspend fun logout(): AuthUser {
        val api = requireAccountApi()
        val token = apiCall { api.logout().data }
        return mutex.withLock {
            authStore.saveSelectedSessionId(null)
            authStore.saveActiveClientRequestId(null)
            persistTokens(
                snapshot().copy(selectedSessionId = null, activeClientRequestId = null),
                token
            ).toAuthUser()!!
        }.also { _currentUser.value = it }
    }

    suspend fun validAccessToken(): String = mutex.withLock {
        val snapshot = snapshot()
        val now = nowMillis()
        if (snapshot.accessToken != null && snapshot.accessExpiresAtMillis > now + EXPIRY_SKEW_MS) {
            return snapshot.accessToken
        }
        authenticate(snapshot).accessToken.orEmpty()
    }

    suspend fun refreshAfterUnauthorized(failedToken: String?): String? = mutex.withLock {
        val current = snapshot()
        if (current.accessToken != null && current.accessToken != failedToken) {
            return current.accessToken
        }
        runCatching { authenticate(current, forceRefresh = true).accessToken }.getOrNull()
    }

    suspend fun storedState(): AuthSnapshot = mutex.withLock { snapshot() }

    private suspend fun authenticate(
        snapshot: AuthSnapshot,
        forceRefresh: Boolean = false
    ): AuthSnapshot {
        val now = nowMillis()
        if (snapshot.refreshToken != null && snapshot.refreshExpiresAtMillis > now) {
            val refreshed = runCatching {
                authApi.refresh(RefreshRequestDto(snapshot.refreshToken)).data
            }.getOrNull()
            if (refreshed != null) return persistTokens(snapshot, refreshed)
            if (forceRefresh) authStore.clearTokens()
        }

        val installation = ensureInstallation(snapshot)
        val token = authApi.anonymous(
            AnonymousAuthRequestDto(
                installationId = installation.installationId.orEmpty(),
                installationSecret = installation.installationSecret.orEmpty(),
                appVersion = BuildConfig.VERSION_NAME
            )
        ).data
        return persistTokens(installation, token)
    }

    private suspend fun ensureInstallation(snapshot: AuthSnapshot): AuthSnapshot {
        if (snapshot.installationId != null && snapshot.installationSecret != null) return snapshot
        val id = UUID.randomUUID().toString()
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val secret = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        authStore.saveInstallation(id, secret)
        return snapshot.copy(installationId = id, installationSecret = secret).also {
            cachedSnapshot = it
        }
    }

    private suspend fun persistTokens(snapshot: AuthSnapshot, token: TokenDto): AuthSnapshot {
        val now = nowMillis()
        val user = token.user?.toModel()
            ?: snapshot.toAuthUser()?.takeIf { it.id == token.userId }
            ?: AuthUser(token.userId, AccountType.GUEST)
        val updated = snapshot.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            accessExpiresAtMillis = now + token.expiresIn * 1000,
            refreshExpiresAtMillis = now + token.refreshExpiresIn * 1000,
            userId = user.id,
            accountType = user.accountType.name.lowercase(),
            displayName = user.displayName,
            email = user.email,
            avatarText = user.avatarText
        )
        authStore.saveAuthSession(
            accessToken = updated.accessToken.orEmpty(),
            refreshToken = updated.refreshToken.orEmpty(),
            accessExpiresAtMillis = updated.accessExpiresAtMillis,
            refreshExpiresAtMillis = updated.refreshExpiresAtMillis,
            userId = user.id,
            accountType = updated.accountType.orEmpty(),
            displayName = user.displayName,
            email = user.email,
            avatarText = user.avatarText
        )
        cachedSnapshot = updated
        _currentUser.value = user
        return updated
    }

    private suspend fun snapshot(): AuthSnapshot {
        return cachedSnapshot ?: authStore.read().also { cachedSnapshot = it }
    }

    private fun requireAccountApi(): AccountApi = checkNotNull(accountApi) {
        "Account API has not been initialized"
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (error: HttpException) {
            val raw = error.response()?.errorBody()?.string().orEmpty()
            val envelope = runCatching {
                json.decodeFromString(ErrorEnvelopeDto.serializer(), raw)
            }.getOrNull()
            throw ApiException(
                code = envelope?.error?.code ?: "HTTP_ERROR",
                statusCode = error.code(),
                message = envelope?.error?.message ?: error.message()
            )
        }
    }

    private companion object {
        const val EXPIRY_SKEW_MS = 30_000L
    }
}

private fun AuthSnapshot.toAuthUser(): AuthUser? {
    val id = userId ?: return null
    return AuthUser(
        id = id,
        accountType = if (accountType == "registered") {
            AccountType.REGISTERED
        } else {
            AccountType.GUEST
        },
        displayName = displayName,
        email = email,
        avatarText = avatarText
    )
}

private fun AuthUserDto.toModel() = AuthUser(
    id = id,
    accountType = if (accountType == "registered") AccountType.REGISTERED else AccountType.GUEST,
    displayName = displayName,
    email = email,
    avatarText = avatarText
)
