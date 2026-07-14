package com.xipian.chatxp_android.data.repository

import android.util.Base64
import com.xipian.chatxp_android.BuildConfig
import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.remote.AuthApi
import com.xipian.chatxp_android.data.remote.dto.AnonymousAuthRequestDto
import com.xipian.chatxp_android.data.remote.dto.RefreshRequestDto
import com.xipian.chatxp_android.data.remote.dto.TokenDto
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(
    private val authApi: AuthApi,
    private val authStore: CredentialStore,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    @Volatile private var cachedSnapshot: AuthSnapshot? = null

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
        val updated = snapshot.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            accessExpiresAtMillis = now + token.expiresIn * 1000,
            refreshExpiresAtMillis = now + token.refreshExpiresIn * 1000
        )
        authStore.saveTokens(
            accessToken = updated.accessToken.orEmpty(),
            refreshToken = updated.refreshToken.orEmpty(),
            accessExpiresAtMillis = updated.accessExpiresAtMillis,
            refreshExpiresAtMillis = updated.refreshExpiresAtMillis
        )
        cachedSnapshot = updated
        return updated
    }

    private suspend fun snapshot(): AuthSnapshot {
        return cachedSnapshot ?: authStore.read().also { cachedSnapshot = it }
    }

    private companion object {
        const val EXPIRY_SKEW_MS = 30_000L
    }
}
