package com.xipian.chatxp_android.app

import android.content.Context
import com.xipian.chatxp_android.BuildConfig
import com.xipian.chatxp_android.data.local.AuthStore
import com.xipian.chatxp_android.data.remote.AuthApi
import com.xipian.chatxp_android.data.remote.BearerInterceptor
import com.xipian.chatxp_android.data.remote.ChatStreamClient
import com.xipian.chatxp_android.data.remote.ChatXpApi
import com.xipian.chatxp_android.data.remote.TokenAuthenticator
import com.xipian.chatxp_android.data.repository.AuthRepository
import com.xipian.chatxp_android.data.repository.ChatRepository
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(context: Context) {
    val authStore = AuthStore(context.applicationContext)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val unauthenticatedClient = OkHttpClient.Builder().build()
    private val authApi = retrofit(unauthenticatedClient).create(AuthApi::class.java)
    val authRepository = AuthRepository(authApi, authStore)

    private val authenticatedClient = OkHttpClient.Builder()
        .addInterceptor(BearerInterceptor(authRepository))
        .authenticator(TokenAuthenticator(authRepository))
        .build()
    private val streamClient = authenticatedClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val api = retrofit(authenticatedClient).create(ChatXpApi::class.java)

    val chatRepository = ChatRepository(
        api = api,
        streamClient = ChatStreamClient(BuildConfig.CHATXP_API_BASE_URL, streamClient, json),
        json = json
    )

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.CHATXP_API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
