package com.xipian.chatxp_android.data.repository

import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.remote.AuthApi
import com.xipian.chatxp_android.data.remote.dto.AnonymousAuthRequestDto
import com.xipian.chatxp_android.data.remote.dto.DataEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.RefreshRequestDto
import com.xipian.chatxp_android.data.remote.dto.TokenDto
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun concurrentRequestsShareSingleRefresh() = runBlocking {
        val now = 100_000L
        val store = FakeCredentialStore(
            AuthSnapshot(
                installationId = "installation",
                installationSecret = "secret",
                accessToken = "expired-access",
                refreshToken = "valid-refresh",
                accessExpiresAtMillis = now - 1,
                refreshExpiresAtMillis = now + 60_000,
                selectedSessionId = null,
                activeClientRequestId = null
            )
        )
        val api = FakeAuthApi()
        val repository = AuthRepository(api, store) { now }

        val tokens = coroutineScope {
            List(8) { async(Dispatchers.Default) { repository.validAccessToken() } }
                .map { it.await() }
        }

        assertEquals(List(8) { "fresh-access" }, tokens)
        assertEquals(1, api.refreshCount.get())
        assertEquals("fresh-refresh", store.snapshot.refreshToken)
    }
}

private class FakeAuthApi : AuthApi {
    val refreshCount = AtomicInteger()

    override suspend fun anonymous(
        body: AnonymousAuthRequestDto
    ): DataEnvelopeDto<TokenDto> = error("Anonymous auth should not be used")

    override suspend fun refresh(body: RefreshRequestDto): DataEnvelopeDto<TokenDto> {
        refreshCount.incrementAndGet()
        delay(25)
        return DataEnvelopeDto(
            TokenDto(
                userId = "user",
                accessToken = "fresh-access",
                tokenType = "Bearer",
                expiresIn = 3_600,
                refreshToken = "fresh-refresh",
                refreshExpiresIn = 7_776_000
            )
        )
    }
}

private class FakeCredentialStore(initial: AuthSnapshot) : CredentialStore {
    var snapshot = initial

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
    override suspend fun clearTokens() {
        snapshot = snapshot.copy(
            accessToken = null,
            refreshToken = null,
            accessExpiresAtMillis = 0,
            refreshExpiresAtMillis = 0
        )
    }
    override suspend fun saveSelectedSessionId(sessionId: String?) {
        snapshot = snapshot.copy(selectedSessionId = sessionId)
    }
    override suspend fun saveActiveClientRequestId(clientRequestId: String?) {
        snapshot = snapshot.copy(activeClientRequestId = clientRequestId)
    }
}
