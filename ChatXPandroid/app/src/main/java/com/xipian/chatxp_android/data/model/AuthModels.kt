package com.xipian.chatxp_android.data.model

enum class AccountType { GUEST, REGISTERED }

data class AuthUser(
    val id: String,
    val accountType: AccountType,
    val displayName: String? = null,
    val email: String? = null,
    val avatarText: String? = null
) {
    val isRegistered: Boolean
        get() = accountType == AccountType.REGISTERED
}
