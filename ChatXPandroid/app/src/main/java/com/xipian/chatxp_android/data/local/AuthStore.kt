package com.xipian.chatxp_android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "chatxp_auth")

data class AuthSnapshot(
    val installationId: String?,
    val installationSecret: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val accessExpiresAtMillis: Long,
    val refreshExpiresAtMillis: Long,
    val selectedSessionId: String?,
    val activeClientRequestId: String?,
    val userId: String? = null,
    val accountType: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarText: String? = null
)

interface CredentialStore {
    suspend fun read(): AuthSnapshot
    suspend fun saveInstallation(id: String, secret: String)
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    )
    suspend fun saveAuthSession(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long,
        userId: String,
        accountType: String,
        displayName: String?,
        email: String?,
        avatarText: String?
    ) {
        saveTokens(
            accessToken,
            refreshToken,
            accessExpiresAtMillis,
            refreshExpiresAtMillis
        )
    }
    suspend fun clearTokens()
    suspend fun saveSelectedSessionId(sessionId: String?)
    suspend fun saveActiveClientRequestId(clientRequestId: String?)
}

class AuthStore(private val context: Context) : CredentialStore {
    private object Keys {
        val installationId = stringPreferencesKey("installation_id")
        val installationSecret = stringPreferencesKey("installation_secret")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val accessExpiresAt = longPreferencesKey("access_expires_at")
        val refreshExpiresAt = longPreferencesKey("refresh_expires_at")
        val selectedSessionId = stringPreferencesKey("selected_session_id")
        val activeClientRequestId = stringPreferencesKey("active_client_request_id")
        val userId = stringPreferencesKey("auth_user_id")
        val accountType = stringPreferencesKey("auth_account_type")
        val displayName = stringPreferencesKey("auth_display_name")
        val email = stringPreferencesKey("auth_email")
        val avatarText = stringPreferencesKey("auth_avatar_text")
    }

    override suspend fun read(): AuthSnapshot {
        val preferences = context.authDataStore.data.first()
        return AuthSnapshot(
            installationId = preferences[Keys.installationId],
            installationSecret = preferences[Keys.installationSecret],
            accessToken = preferences[Keys.accessToken],
            refreshToken = preferences[Keys.refreshToken],
            accessExpiresAtMillis = preferences[Keys.accessExpiresAt] ?: 0L,
            refreshExpiresAtMillis = preferences[Keys.refreshExpiresAt] ?: 0L,
            selectedSessionId = preferences[Keys.selectedSessionId],
            activeClientRequestId = preferences[Keys.activeClientRequestId],
            userId = preferences[Keys.userId],
            accountType = preferences[Keys.accountType],
            displayName = preferences[Keys.displayName],
            email = preferences[Keys.email],
            avatarText = preferences[Keys.avatarText]
        )
    }

    override suspend fun saveInstallation(id: String, secret: String) {
        context.authDataStore.edit {
            it[Keys.installationId] = id
            it[Keys.installationSecret] = secret
        }
    }

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    ) {
        context.authDataStore.edit {
            it[Keys.accessToken] = accessToken
            it[Keys.refreshToken] = refreshToken
            it[Keys.accessExpiresAt] = accessExpiresAtMillis
            it[Keys.refreshExpiresAt] = refreshExpiresAtMillis
        }
    }

    override suspend fun saveAuthSession(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long,
        userId: String,
        accountType: String,
        displayName: String?,
        email: String?,
        avatarText: String?
    ) {
        context.authDataStore.edit {
            it[Keys.accessToken] = accessToken
            it[Keys.refreshToken] = refreshToken
            it[Keys.accessExpiresAt] = accessExpiresAtMillis
            it[Keys.refreshExpiresAt] = refreshExpiresAtMillis
            it[Keys.userId] = userId
            it[Keys.accountType] = accountType
            setOrRemove(it, Keys.displayName, displayName)
            setOrRemove(it, Keys.email, email)
            setOrRemove(it, Keys.avatarText, avatarText)
        }
    }

    override suspend fun clearTokens() {
        context.authDataStore.edit {
            it.remove(Keys.accessToken)
            it.remove(Keys.refreshToken)
            it.remove(Keys.accessExpiresAt)
            it.remove(Keys.refreshExpiresAt)
            it.remove(Keys.userId)
            it.remove(Keys.accountType)
            it.remove(Keys.displayName)
            it.remove(Keys.email)
            it.remove(Keys.avatarText)
        }
    }

    override suspend fun saveSelectedSessionId(sessionId: String?) {
        context.authDataStore.edit {
            if (sessionId == null) it.remove(Keys.selectedSessionId)
            else it[Keys.selectedSessionId] = sessionId
        }
    }

    override suspend fun saveActiveClientRequestId(clientRequestId: String?) {
        context.authDataStore.edit {
            if (clientRequestId == null) it.remove(Keys.activeClientRequestId)
            else it[Keys.activeClientRequestId] = clientRequestId
        }
    }

    private fun setOrRemove(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?
    ) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }
}
